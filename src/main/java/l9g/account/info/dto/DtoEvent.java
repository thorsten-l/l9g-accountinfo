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
package l9g.account.info.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object (DTO) representing an event, typically used for WebSocket communication.
 */
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@Schema(name = "Event", description = "Represents a communication event")
public class DtoEvent
{
  /**
   * Constant for an unknown event type.
   */
  public static final String EVENT_UNKNOWN = "unkown";

  /**
   * Constant for a heartbeat event, indicating a live connection.
   */
  public static final String EVENT_HEARTBEAT = "heartbeat";

  /**
   * Constant for an error event.
   */
  public static final String EVENT_ERROR = "error";

  /**
   * Constant for an event to show something on the client.
   */
  public static final String EVENT_SHOW = "show";

  /**
   * Constant for an event to hide something on the client.
   */
  public static final String EVENT_HIDE = "hide";

  /**
   * Constant for an event to clear content on the client.
   */
  public static final String EVENT_CLEAR = "clear";

  /**
   * Constructs a new DtoEvent with a specified event type and current timestamp.
   *
   * @param event The type of the event (e.g., {@link #EVENT_SHOW}, {@link #EVENT_HIDE}).
   */
  public DtoEvent(String event)
  {
    this.timestamp = System.currentTimeMillis();
    this.event = event;
  }

  /**
   * Constructs a new DtoEvent with a specified event type and message, and current timestamp.
   *
   * @param event The type of the event.
   * @param message An optional message associated with the event.
   */
  public DtoEvent(String event, String message)
  {
    this(event);
    this.message = message;
  }

  /**
   * The type of the event.
   */
  private String event;

  /**
   * The timestamp when the event occurred, in milliseconds since epoch.
   */
  private long timestamp;

  /**
   * An optional message associated with the event.
   */
  private String message;

}
