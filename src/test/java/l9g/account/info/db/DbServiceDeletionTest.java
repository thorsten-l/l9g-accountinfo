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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import l9g.account.info.db.model.SdbLastSeen;
import l9g.account.info.db.model.SdbLastSeenId;
import l9g.account.info.db.model.SdbSecretData;
import l9g.account.info.db.model.SdbSecretType;
import l9g.account.info.dto.DtoLastSeenUser;
import l9g.account.info.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the GDPR-relevant deletion paths of {@link DbService}:
 * {@code updateLastSeen}, {@code findExpiredLastSeen} and
 * {@code deleteUserData}. These implement the Art. 17 erasure concept, so their
 * invariants — above all "an incompletely erased user keeps its last-seen entry
 * so the next run retries" — must not regress silently.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class DbServiceDeletionTest
{
  private static final String USERNAME = "mmuster";

  private SdbPropertiesRepository propertiesRepository;

  private SdbSecretDataRepository secretDataRepository;

  private SdbVaultAdminKeyRepository vaultAdminKeyRepository;

  private SdbLastSeenRepository lastSeenRepository;

  private FileStorageService fileStorageService;

  private DbService dbService;

  @BeforeEach
  void setUp()
  {
    propertiesRepository = mock(SdbPropertiesRepository.class);
    secretDataRepository = mock(SdbSecretDataRepository.class);
    vaultAdminKeyRepository = mock(SdbVaultAdminKeyRepository.class);
    lastSeenRepository = mock(SdbLastSeenRepository.class);
    fileStorageService = mock(FileStorageService.class);
    dbService = new DbService(propertiesRepository, secretDataRepository,
      vaultAdminKeyRepository, lastSeenRepository, new ObjectMapper());
  }

  private static SdbSecretData secretData(SdbSecretType type)
  {
    return new SdbSecretData("publisher", "pad-uuid", type);
  }

  // --------------------------------------------------------- updateLastSeen

  @Test
  @DisplayName("updateLastSeen ignores null and empty input without touching the repository")
  void updateLastSeenIgnoresEmptyInput()
  {
    assertThat(dbService.updateLastSeen(null)).isZero();
    assertThat(dbService.updateLastSeen(List.of())).isZero();

    verify(lastSeenRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("updateLastSeen skips users without a usable username")
  void updateLastSeenSkipsBlankUsernames()
  {
    List<DtoLastSeenUser> users = Arrays.asList(
      new DtoLastSeenUser("mmuster", "Marie", "Muster", "m@example.org"),
      new DtoLastSeenUser(null, "No", "Name", "n@example.org"),
      new DtoLastSeenUser("   ", "Blank", "Name", "b@example.org"),
      new DtoLastSeenUser("jdoe", "John", "Doe", "j@example.org"));

    assertThat(dbService.updateLastSeen(users)).isEqualTo(2);

    ArgumentCaptor<List<SdbLastSeen>> captor =
      ArgumentCaptor.forClass(List.class);
    verify(lastSeenRepository).saveAll(captor.capture());
    assertThat(captor.getValue())
      .extracting(SdbLastSeen :: getUsername)
      .containsExactly("mmuster", "jdoe");
  }

  @Test
  @DisplayName("updateLastSeen writes the default tenant and copies the user attributes")
  void updateLastSeenMapsAttributes()
  {
    Date before = new Date();

    dbService.updateLastSeen(List.of(
      new DtoLastSeenUser("mmuster", "Marie", "Muster", "m@example.org")));

    ArgumentCaptor<List<SdbLastSeen>> captor =
      ArgumentCaptor.forClass(List.class);
    verify(lastSeenRepository).saveAll(captor.capture());
    SdbLastSeen entity = captor.getValue().get(0);

    assertThat(entity.getTenantId()).isEqualTo(DbService.DEFAULT_TENANT_ID);
    assertThat(entity.getUsername()).isEqualTo("mmuster");
    assertThat(entity.getFirstname()).isEqualTo("Marie");
    assertThat(entity.getLastname()).isEqualTo("Muster");
    assertThat(entity.getMail()).isEqualTo("m@example.org");
    assertThat(entity.getTimestamp()).isAfterOrEqualTo(before);
  }

  // ----------------------------------------------------- findExpiredLastSeen

  @Test
  @DisplayName("findExpiredLastSeen without a grace period returns empty and skips the query")
  void findExpiredLastSeenWithoutGracePeriod()
  {
    assertThat(dbService.findExpiredLastSeen(null)).isEmpty();

    verify(lastSeenRepository, never()).findByTimestampBefore(any());
  }

  @Test
  @DisplayName("findExpiredLastSeen queries with a cutoff of now minus the grace period")
  void findExpiredLastSeenComputesCutoff()
  {
    Duration gracePeriod = Duration.ofDays(90);
    long expected = System.currentTimeMillis() - gracePeriod.toMillis();
    when(lastSeenRepository.findByTimestampBefore(any())).thenReturn(List.of());

    dbService.findExpiredLastSeen(gracePeriod);

    ArgumentCaptor<Date> cutoff = ArgumentCaptor.forClass(Date.class);
    verify(lastSeenRepository).findByTimestampBefore(cutoff.capture());
    assertThat(cutoff.getValue().getTime())
      .isBetween(expected - 5_000L, expected + 5_000L);
  }

  // ---------------------------------------------------------- deleteUserData

  @Test
  @DisplayName("deleteUserData erases every record and finally the last-seen entry")
  void deleteUserDataErasesEverything()
    throws Exception
  {
    List<SdbSecretData> records = List.of(
      secretData(SdbSecretType.ID_FRONT_IMAGE),
      secretData(SdbSecretType.ID_BACK_IMAGE),
      secretData(SdbSecretType.ID_SIGNATURE_JWT));
    SdbLastSeen lastSeen = new SdbLastSeen(DbService.DEFAULT_TENANT_ID,
      USERNAME, "Marie", "Muster", "m@example.org");
    when(secretDataRepository.findByNameOrderByModifyTimestampDesc(USERNAME))
      .thenReturn(Optional.of(records));
    when(lastSeenRepository.findById(any())).thenReturn(Optional.of(lastSeen));

    DbService.UserDeletionResult result =
      dbService.deleteUserData(USERNAME, fileStorageService);

    assertThat(result.deletedRecords()).isEqualTo(3);
    assertThat(result.failedRecords()).isZero();
    assertThat(result.complete()).isTrue();
    verify(lastSeenRepository).delete(lastSeen);
  }

  @Test
  @DisplayName("the encrypted file is deleted before the database row")
  void fileIsDeletedBeforeDatabaseRow()
    throws Exception
  {
    SdbSecretData record = secretData(SdbSecretType.ID_FRONT_IMAGE);
    when(secretDataRepository.findByNameOrderByModifyTimestampDesc(USERNAME))
      .thenReturn(Optional.of(List.of(record)));
    when(lastSeenRepository.findById(any())).thenReturn(Optional.empty());

    dbService.deleteUserData(USERNAME, fileStorageService);

    InOrder order = inOrder(fileStorageService, secretDataRepository);
    order.verify(fileStorageService).delete(record);
    order.verify(secretDataRepository).delete(record);
  }

  /**
   * The central invariant of the erasure concept: if even one record could not
   * be erased, the {@code SdbLastSeen} entry must survive so the next scheduled
   * run picks the user up again. Deleting it early would strand the remaining
   * personal data in the database forever, since nothing would ever flag the
   * user as a deletion candidate again.
   */
  @Test
  @DisplayName("a failed record keeps the last-seen entry for the next retry")
  void partialFailureKeepsLastSeenEntry()
    throws Exception
  {
    SdbSecretData ok1 = secretData(SdbSecretType.ID_FRONT_IMAGE);
    SdbSecretData broken = secretData(SdbSecretType.ID_BACK_IMAGE);
    SdbSecretData ok2 = secretData(SdbSecretType.ID_SIGNATURE_JWT);
    when(secretDataRepository.findByNameOrderByModifyTimestampDesc(USERNAME))
      .thenReturn(Optional.of(List.of(ok1, broken, ok2)));
    doThrow(new IOException("file locked"))
      .when(fileStorageService).delete(broken);

    DbService.UserDeletionResult result =
      dbService.deleteUserData(USERNAME, fileStorageService);

    assertThat(result.deletedRecords()).isEqualTo(2);
    assertThat(result.failedRecords()).isEqualTo(1);
    assertThat(result.complete()).isFalse();

    // the broken record's row must survive together with the last-seen entry
    verify(secretDataRepository, never()).delete(broken);
    verify(lastSeenRepository, never()).delete(any());
    verify(lastSeenRepository, never()).findById(any());
  }

  @Test
  @DisplayName("deleteUserData is idempotent for a user without any records")
  void deleteUserDataIsIdempotent()
    throws Exception
  {
    when(secretDataRepository.findByNameOrderByModifyTimestampDesc(USERNAME))
      .thenReturn(Optional.empty());
    when(lastSeenRepository.findById(any())).thenReturn(Optional.empty());

    DbService.UserDeletionResult result =
      dbService.deleteUserData(USERNAME, fileStorageService);

    assertThat(result.deletedRecords()).isZero();
    assertThat(result.failedRecords()).isZero();
    assertThat(result.complete()).isTrue();
    verify(fileStorageService, never()).delete(any());
    verify(lastSeenRepository, never()).delete(any());
  }

  @Test
  @DisplayName("the last-seen entry is looked up under the default tenant")
  void lastSeenIsLookedUpWithDefaultTenant()
    throws Exception
  {
    when(secretDataRepository.findByNameOrderByModifyTimestampDesc(USERNAME))
      .thenReturn(Optional.empty());
    when(lastSeenRepository.findById(any())).thenReturn(Optional.empty());

    dbService.deleteUserData(USERNAME, fileStorageService);

    verify(lastSeenRepository).findById(
      new SdbLastSeenId(DbService.DEFAULT_TENANT_ID, USERNAME));
  }

}
