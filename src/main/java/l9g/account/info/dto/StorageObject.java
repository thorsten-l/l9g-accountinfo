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
import java.util.Arrays;
import java.util.Objects;
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
   * Compares by value, including the content of {@link #data}.
   * <p>
   * The generated record implementation compared the {@code byte[]} by
   * reference, so two objects with byte-identical payloads were unequal.
   *
   * @param obj The object to compare with.
   *
   * @return {@code true} if all components are equal.
   */
  @Override
  public boolean equals(Object obj)
  {
    if(this == obj)
    {
      return true;
    }

    if( ! (obj instanceof StorageObject other))
    {
      return false;
    }

    return type == other.type
      && Objects.equals(user, other.user)
      && Objects.equals(status, other.status)
      && Arrays.equals(data, other.data);
  }

  /**
   * @return A hash code consistent with {@link #equals(Object)}.
   */
  @Override
  public int hashCode()
  {
    return Objects.hash(type, user, status, Arrays.hashCode(data));
  }

  /**
   * Renders the object without its payload: {@code data} holds identity
   * document content, so only its size is reported. The generated
   * implementation printed the array's identity hash, which was useless for
   * diagnostics.
   *
   * @return A log-safe description of this object.
   */
  @Override
  public String toString()
  {
    return "StorageObject[type=" + type + ", user=" + user
      + ", status=" + (status != null ? "present" : "null")
      + ", data=" + (data != null ? data.length + " bytes" : "null") + "]";
  }


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
    
    /**
     * Returns a data-minimised copy for persisting as the record description:
     * the display name is merged into {@code name}, and the separate name parts
     * are dropped.
     *
     * @return The reduced copy.
     */
    public EndUserData description()
    {
      return new EndUserData(this.pid, this.mail, displayName(),
        this.username, null, null);
    }

    /**
     * Joins the available name parts. Only the parts that are actually present
     * are used — plain concatenation previously produced the literal display
     * name {@code "null null"} for a record without a name.
     *
     * @return The display name, or {@code null} if neither part is set.
     */
    private String displayName()
    {
      if(givenName == null)
      {
        return surname;
      }

      if(surname == null)
      {
        return givenName;
      }

      return givenName + " " + surname;
    }

  }

}
