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
package l9g.account.info.db.model;

/**
 * Enumerates the different types of secret data that can be stored in the database.
 * Each type represents a distinct category of sensitive information, such as
 * signature pad configurations, JWTs, or image data.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
public enum SdbSecretType
{
  /**
   * Represents JSON configuration data for a signature pad.
   */
  SIGNATURE_PAD_JSON,
  /**
   * Represents a signed JSON Web Token (JWT) containing a signature.
   */
  ID_SIGNATURE_JWT,
  /**
   * Represents an image of the front side of an identification document.
   */
  ID_FRONT_IMAGE,
  /**
   * Represents an image of the back side of an identification document.
   */
  ID_BACK_IMAGE;

  /**
   * Converts a string representation to an {@link SdbSecretType} enum constant.
   * This method supports various string inputs (e.g., "front", "back", "signature", "pad").
   *
   * @param type The string representation of the secret type.
   *
   * @return The corresponding {@link SdbSecretType} enum constant.
   *
   * @throws IllegalArgumentException If the type string is null or unknown.
   */
  public static SdbSecretType fromString(String type)
  {
    if(type == null)
    {
      throw new IllegalArgumentException("Type string cannot be null");
    }
    return switch(type.toLowerCase())
    {
      case "front" ->
        ID_FRONT_IMAGE;
      case "back" ->
        ID_BACK_IMAGE;
      case "signature" ->
        ID_SIGNATURE_JWT;
      case "pad" ->
        SIGNATURE_PAD_JSON;
      default ->
        throw new IllegalArgumentException("Unknown SdbSecretType: " + type);
    };
  }

}
