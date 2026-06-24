/*
 * Copyright 2025 Thorsten Ludewig (t.ludewig@gmail.com).
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Schedules and executes database deletion/cleanup jobs using Spring's scheduling capabilities.
 * This class periodically triggers a cleanup process in the DbService.
 * It is enabled based on the `scheduler.db-deletion.enabled` property.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@EnableAsync
@EnableScheduling
@ConditionalOnProperty(
  prefix = "scheduler.db-deletion",
  name = "enabled",
  havingValue = "true",
  matchIfMissing = false
)
@Configuration
@Slf4j
@RequiredArgsConstructor
public class DbDeletionScheduler
{
  /**
   * The database service used to perform cleanup operations.
   */
  private final DbService dbService;

  /**
   * The LDAP service used to read the current set of directory users.
   */
  private final LdapService ldapService;

  /**
   * LDAP configuration, providing the deletion grace period.
   */
  private final LdapData ldapDataConfig;

  /**
   * Service used to delete the encrypted files of expired users.
   */
  private final FileStorageService fileStorageService;

  /**
   * Executes the database deletion/cleanup job.
   * This method is scheduled using a cron expression defined by `scheduler.db-deletion.cron`.
   */
  @Scheduled(cron = "${scheduler.db-deletion.cron:0 15 2 * * *}")
  @Async
  public void deletionJob()
  {
    log.info("Starting database deletion job...");
    try
    {
      updateLastSeenFromLdap();
      deleteExpiredUsers();
      log.info("Database deletion job finished successfully.");
    }
    catch(Throwable e)
    {
      log.error("Error during database deletion job", e);
    }
  }

  /**
   * First step of the deletion concept: reads all (active) users from LDAP via
   * the configured {@code ldap.configuration.user.filter-last-seen} filter and
   * inserts/overwrites their entries in the "last seen" table, refreshing each
   * timestamp to "now".
   *
   * @throws Exception If reading from LDAP fails.
   */
  private void updateLastSeenFromLdap()
    throws Exception
  {
    log.info("Updating last-seen table from LDAP...");
    List<DtoLastSeenUser> users = ldapService.listLastSeenUsers();
    int count = dbService.updateLastSeen(users);
    log.info("Last-seen table updated: {} user(s).", count);
  }

  /**
   * Second step of the deletion concept: permanently erases every user whose
   * last-seen timestamp is older than the configured grace period
   * ({@code ldap.configuration.user.delete-grace-period}). For each expired user
   * all {@link SdbSecretData} rows, their encrypted files and the
   * {@link SdbLastSeen} entry are deleted (see {@link DbService#deleteUserData}).
   * A failure for one user is logged and does not stop the batch; incompletely
   * erased users are retried on the next run.
   */
  private void deleteExpiredUsers()
  {
    Duration gracePeriod = ldapDataConfig.getUser().getDeleteGracePeriod();

    if(gracePeriod == null)
    {
      log.warn("No delete-grace-period configured; skipping user deletion.");
      return;
    }

    List<SdbLastSeen> expired = dbService.findExpiredLastSeen(gracePeriod);
    log.info("User deletion (grace period {}): {} expired user(s).",
      gracePeriod, expired.size());

    int fullyDeleted = 0;
    for(SdbLastSeen user : expired)
    {
      try
      {
        DbService.UserDeletionResult result =
          dbService.deleteUserData(user.getUsername(), fileStorageService);
        if(result.complete())
        {
          fullyDeleted++;
          log.warn("USER_DELETED: tenant={}, username={}, records={}, "
            + "lastSeen={} (grace period {})",
            user.getTenantId(), user.getUsername(), result.deletedRecords(),
            user.getTimestamp(), gracePeriod);
        }
        else
        {
          log.error("USER_DELETE_INCOMPLETE: username={}, deleted={}, failed={}"
            + " — will retry next run",
            user.getUsername(), result.deletedRecords(), result.failedRecords());
        }
      }
      catch(Throwable e)
      {
        log.error("USER_DELETE_FAILED: username={}", user.getUsername(), e);
      }
    }

    log.info("User deletion finished: {}/{} user(s) fully erased.",
      fullyDeleted, expired.size());
  }

}
