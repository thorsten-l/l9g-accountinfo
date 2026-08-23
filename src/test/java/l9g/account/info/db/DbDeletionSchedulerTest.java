/*
 * Copyright 2026 Thorsten Ludewig (t.ludewig@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package l9g.account.info.db;

import java.time.Duration;
import java.util.List;
import l9g.account.info.config.LdapData;
import l9g.account.info.db.model.SdbLastSeen;
import l9g.account.info.dto.DtoLastSeenUser;
import l9g.account.info.service.FileStorageService;
import l9g.account.info.service.LdapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DbDeletionScheduler}, the scheduled Art. 17 erasure
 * job. The job must be robust: a single failing user may never abort the batch,
 * and no exception may escape into the scheduler thread.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class DbDeletionSchedulerTest
{
  private DbService dbService;

  private LdapService ldapService;

  private LdapData ldapDataConfig;

  private FileStorageService fileStorageService;

  private DbDeletionScheduler scheduler;

  @BeforeEach
  void setUp()
  {
    dbService = mock(DbService.class);
    ldapService = mock(LdapService.class);
    fileStorageService = mock(FileStorageService.class);
    ldapDataConfig = ldapConfig(Duration.ofDays(90));
    scheduler = new DbDeletionScheduler(dbService, ldapService, ldapDataConfig,
      fileStorageService);
  }

  private static LdapData ldapConfig(Duration gracePeriod)
  {
    LdapData.LdapConfig user = new LdapData.LdapConfig();
    user.setDeleteGracePeriod(gracePeriod);
    LdapData data = new LdapData();
    data.setUser(user);
    return data;
  }

  private static SdbLastSeen lastSeen(String username)
  {
    return new SdbLastSeen(DbService.DEFAULT_TENANT_ID, username,
      "First", "Last", username + "@example.org");
  }

  @Test
  @DisplayName("the job refreshes last-seen from LDAP and then erases expired users")
  void jobRefreshesThenDeletes()
    throws Exception
  {
    List<DtoLastSeenUser> directoryUsers = List.of(
      new DtoLastSeenUser("mmuster", "Marie", "Muster", "m@example.org"));
    when(ldapService.listLastSeenUsers()).thenReturn(directoryUsers);
    when(dbService.updateLastSeen(directoryUsers)).thenReturn(1);
    when(dbService.findExpiredLastSeen(Duration.ofDays(90)))
      .thenReturn(List.of(lastSeen("jdoe")));
    when(dbService.deleteUserData(eq("jdoe"), any()))
      .thenReturn(new DbService.UserDeletionResult(2, 0));

    scheduler.deletionJob();

    InOrder order = inOrder(ldapService, dbService);
    order.verify(ldapService).listLastSeenUsers();
    order.verify(dbService).updateLastSeen(directoryUsers);
    order.verify(dbService).findExpiredLastSeen(Duration.ofDays(90));
    order.verify(dbService).deleteUserData("jdoe", fileStorageService);
  }

  @Test
  @DisplayName("without a configured grace period no user is ever deleted")
  void missingGracePeriodSkipsDeletion()
    throws Exception
  {
    scheduler = new DbDeletionScheduler(dbService, ldapService,
      ldapConfig(null), fileStorageService);
    when(ldapService.listLastSeenUsers()).thenReturn(List.of());

    scheduler.deletionJob();

    verify(dbService).updateLastSeen(any());
    verify(dbService, never()).findExpiredLastSeen(any());
    verify(dbService, never()).deleteUserData(any(), any());
  }

  @Test
  @DisplayName("a failing user does not abort the batch")
  void failingUserDoesNotAbortBatch()
    throws Exception
  {
    when(ldapService.listLastSeenUsers()).thenReturn(List.of());
    when(dbService.findExpiredLastSeen(any())).thenReturn(List.of(
      lastSeen("user1"), lastSeen("user2"), lastSeen("user3")));
    when(dbService.deleteUserData(eq("user1"), any()))
      .thenReturn(new DbService.UserDeletionResult(1, 0));
    when(dbService.deleteUserData(eq("user2"), any()))
      .thenThrow(new IllegalStateException("vault sealed"));
    when(dbService.deleteUserData(eq("user3"), any()))
      .thenReturn(new DbService.UserDeletionResult(1, 0));

    scheduler.deletionJob();

    verify(dbService).deleteUserData("user1", fileStorageService);
    verify(dbService).deleteUserData("user2", fileStorageService);
    verify(dbService).deleteUserData("user3", fileStorageService);
  }

  @Test
  @DisplayName("an incompletely erased user is tolerated and retried on the next run")
  void incompleteDeletionIsTolerated()
    throws Exception
  {
    when(ldapService.listLastSeenUsers()).thenReturn(List.of());
    when(dbService.findExpiredLastSeen(any()))
      .thenReturn(List.of(lastSeen("user1")));
    when(dbService.deleteUserData(eq("user1"), any()))
      .thenReturn(new DbService.UserDeletionResult(1, 2));

    assertThatCode(() -> scheduler.deletionJob()).doesNotThrowAnyException();

    verify(dbService).deleteUserData("user1", fileStorageService);
  }

  @Test
  @DisplayName("an LDAP outage neither propagates nor triggers a deletion run")
  void ldapFailureIsContained()
    throws Exception
  {
    when(ldapService.listLastSeenUsers())
      .thenThrow(new java.net.ConnectException("ldap unreachable"));

    assertThatCode(() -> scheduler.deletionJob()).doesNotThrowAnyException();

    verify(dbService, never()).updateLastSeen(any());
    verify(dbService, never()).findExpiredLastSeen(any());
    verify(dbService, never()).deleteUserData(any(), any());
  }

  @Test
  @DisplayName("an Error from the database is contained as well")
  void errorFromDatabaseIsContained()
    throws Exception
  {
    when(ldapService.listLastSeenUsers()).thenReturn(List.of());
    when(dbService.updateLastSeen(any())).thenThrow(new StackOverflowError());

    assertThatCode(() -> scheduler.deletionJob()).doesNotThrowAnyException();
  }

}
