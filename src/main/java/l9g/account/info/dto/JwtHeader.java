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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents the header section of a JWT (JSON Web Token).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "JwtHeader", description = "The header section of a JSON Web Token (JWT)")
public record JwtHeader(
  @JsonProperty("alg")
  @Schema(description = "The algorithm used to sign the JWT")
  String algorithm,
  @JsonProperty("typ")
  @Schema(description = "The type of the token, typically 'JWT'")
  String type,
  @JsonProperty("kid")
  @Schema(description = "The key ID used to identify the key used to sign the JWT")
  String kid)
  {
}
