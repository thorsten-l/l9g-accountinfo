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
package l9g.account.info.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import l9g.account.info.db.model.SdbSecretType;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record StorageObject(
  SdbSecretType type,
  EndUserData user,
  IdentificationStatus status,
  byte[] data)
  {
  
  /**
   * End-user identity attributes captured at process start. Returned by
   * {@link #endUserData} to tag identification artifacts pushed to the
   * storage backend.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static record EndUserData(
    String pid,
    String mail,
    String name,
    String username,
    String givenName,
    String surname)
    {
    
    public EndUserData description()
    {
      return new EndUserData(this.pid, this.mail, 
        this.givenName + " " + this.surname, this.username, null, null);
    }
  }

}
