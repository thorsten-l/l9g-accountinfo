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
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents the OpenID Connect (OIDC) Discovery metadata.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "OidcDiscovery", description = "OpenID Connect Discovery metadata")
public record OidcDiscovery(
  @Schema(description = "The issuer identifier for the OpenID Provider")
  String issuer,
  @JsonProperty("authorization_endpoint")
  @Schema(description = "The URL of the authorization endpoint")
  String authorizationEndpoint,
  @JsonProperty("token_endpoint")
  @Schema(description = "The URL of the token endpoint")
  String tokenEndpoint,
  @JsonProperty("introspection_endpoint")
  @Schema(description = "The URL of the introspection endpoint")
  String introspectionEndpoint,
  @JsonProperty("userinfo_endpoint")
  @Schema(description = "The URL of the userinfo endpoint")
  String userinfoEndpoint,
  @JsonProperty("end_session_endpoint")
  @Schema(description = "The URL of the end session endpoint")
  String endSessionEndpoint,
  @JsonProperty("frontchannel_logout_session_supported")
  @Schema(description = "Indicates if frontchannel logout session is supported")
  boolean frontchannelLogoutSessionSupported,
  @JsonProperty("frontchannel_logout_supported")
  @Schema(description = "Indicates if frontchannel logout is supported")
  boolean frontchannelLogoutSupported,
  @JsonProperty("jwks_uri")
  @Schema(description = "The URL of the JSON Web Key Set (JWKS) endpoint")
  String jwksUri,
  @JsonProperty("check_session_iframe")
  @Schema(description = "The URL of the check session iframe")
  String checkSessionIframe,
  @JsonProperty("grant_types_supported")
  @Schema(description = "The list of grant types supported by the OpenID Provider")
  List<String> grantTypesTupported)
  {
}
