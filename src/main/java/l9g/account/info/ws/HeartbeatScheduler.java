/*
 * Copyright 2024 Thorsten Ludewig (t.ludewig@gmail.com).
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
/**
 * The HeartbeatScheduler class is responsible for scheduling and executing
 * a heartbeat job at a fixed rate. This class uses Spring's scheduling and
 * asynchronous capabilities to periodically send a heartbeat event through
 * a WebSocket handler.
 *
 * <p>
 * The heartbeat job is scheduled with a fixed rate specified by the
 * `scheduler.heartbeat.rate` property and is executed asynchronously.
 *
 * <p>
 * Dependencies:
 * <ul>
 * <li>{@link MonitorWebSocketHandler} - The WebSocket handler used to fire the heartbeat event.</li>
 * </ul>
 *
 * <p>
 * Annotations:
 * <ul>
 * <li>{@link EnableAsync} - Enables Spring's asynchronous method execution capability.</li>
 * <li>{@link EnableScheduling} - Enables Spring's scheduled task execution capability.</li>
 * <li>{@link Configuration} - Indicates that this class is a Spring configuration class.</li>
 * <li>{@link Slf4j} - Lombok annotation to generate a logger field.</li>
 * <li>{@link RequiredArgsConstructor} - Lombok annotation to generate a constructor with required arguments.</li>
 * </ul>
 *
 * <p>
 * Methods:
 * <ul>
 * <li>{@code heartbeatJob()} - The method that gets executed at a fixed rate to send a heartbeat event.</li>
 * </ul>
 *
 * <p>
 * Exceptions:
 * <ul>
 * <li>{@link IOException} - Thrown if an I/O error occurs during the execution of the heartbeat job.</li>
 * </ul>
 *
 * <p>
 * Author: Thorsten Ludewig (t.ludewig@gmail.com)
 */
package l9g.account.info.ws;

import l9g.account.info.dto.DtoEvent;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@EnableAsync
@EnableScheduling
@ConditionalOnProperty(
  prefix = "scheduler.heartbeat",
  name = "enabled",
  havingValue = "true",
  matchIfMissing = false
)
@Configuration
@Slf4j
@RequiredArgsConstructor
public class HeartbeatScheduler
{
  private final SignaturePadWebSocketHandler webSockerHandler;

  @Scheduled(fixedRateString = "${scheduler.heartbeat.rate:15000}")
  @Async
  public void heartbeatJob()
    throws IOException
  {
    log.trace("heartbeatJob 1");
    webSockerHandler.fireEventToAllSessions(new DtoEvent(DtoEvent.EVENT_HEARTBEAT));
  }

}
