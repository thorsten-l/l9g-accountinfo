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
package l9g.account.info.ws;

import java.io.IOException;
import l9g.account.info.dto.DtoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Schedules and executes a heartbeat job at a fixed rate using Spring's scheduling capabilities.
 * This class periodically sends a heartbeat event through a WebSocket handler to all active sessions.
 * It is enabled based on the `scheduler.heartbeat.enabled` property.
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
  /**
   * The WebSocket handler used to fire heartbeat events to connected sessions.
   */
  private final SignaturePadWebSocketHandler webSockerHandler;

  /**
   * Sends a heartbeat event to all connected WebSocket sessions.
   * This method is scheduled to run at a fixed rate, defined by `scheduler.heartbeat.rate`.
   *
   * @throws IOException If an I/O error occurs while firing the event.
   */
  @Scheduled(fixedRateString = "${scheduler.heartbeat.rate:15000}")
  @Async
  public void heartbeatJob()
    throws IOException
  {
    log.trace("heartbeatJob 1");
    webSockerHandler.fireEventToAllSessions(new DtoEvent(DtoEvent.EVENT_HEARTBEAT));
  }

}
