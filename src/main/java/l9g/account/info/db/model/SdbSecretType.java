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
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
public enum SdbSecretType
{
  SIGNATURE_PAD_JSON,
  ID_SIGNATURE_JWT,
  ID_FRONT_IMAGE,
  ID_BACK_IMAGE;

  public static SdbSecretType fromString(String type) {
    if (type == null) {
      throw new IllegalArgumentException("Type string cannot be null");
    }
    return switch (type.toLowerCase()) {
      case "front" -> ID_FRONT_IMAGE;
      case "back" -> ID_BACK_IMAGE;
      case "signature" -> ID_SIGNATURE_JWT;
      case "pad" -> SIGNATURE_PAD_JSON;
      default -> throw new IllegalArgumentException("Unknown SdbSecretType: " + type);
    };
  }
}
