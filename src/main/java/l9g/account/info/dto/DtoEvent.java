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

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class DtoEvent
{
  public static final String EVENT_UNKNOWN = "unkown";

  public static final String EVENT_HEARTBEAT = "heartbeat";

  public static final String EVENT_ERROR = "error";

  public static final String EVENT_SHOW = "show";

  public static final String EVENT_HIDE = "hide";

  public static final String EVENT_CLEAR = "clear";

  public DtoEvent(String event)
  {
    this.timestamp = System.currentTimeMillis();
    this.event = event;
  }

  public DtoEvent(String event, String message)
  {
    this(event);
    this.message = message;
  }

  private String event;

  private long timestamp;

  private String message;

}
