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
      dbService.cleanupJob();
      log.info("Database deletion job finished successfully.");
    }
    catch(Throwable e)
    {
      log.error("Error during database deletion job", e);
    }
  }

}
