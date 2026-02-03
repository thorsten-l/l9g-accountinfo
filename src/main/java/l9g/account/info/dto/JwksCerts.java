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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a collection of JSON Web Key Set (JWKS) certificates.
 */
@Schema(name = "JwksCerts", description = "A collection of JSON Web Key Set (JWKS) certificates")
public record JwksCerts(
  List<JwksKey> keys)
  {
  /**
   * Represents a JSON Web Key (JWK) used in the JWKS response.
   */
  @Schema(name = "JwksKey", description = "A JSON Web Key (JWK)")
  public record JwksKey(
    @JsonProperty("kid")
    @Schema(description = "The unique identifier for the key")
    String keyId,
    @JsonProperty("kty")
    @Schema(description = "The cryptographic algorithm family used with the key")
    String keyType,
    @JsonProperty("alg")
    @Schema(description = "The specific algorithm used with the key")
    String algorithm,
    @JsonProperty("use")
    @Schema(description = "The intended use of the key (e.g., signature, encryption)")
    String keyUsage,
    @JsonProperty("n")
    @Schema(description = "The modulus value for RSA keys")
    String modulus,
    @JsonProperty("e")
    @Schema(description = "The exponent value for RSA keys")
    String exponent,
    @JsonProperty("x5c")
    @Schema(description = "The X.509 certificate chain corresponding to the key")
    List<String> x509CertificateChain,
    @JsonProperty("x5t")
    @Schema(description = "The thumbprint of the X.509 certificate")
    String x509CertificateThumbprint,
    @JsonProperty("x5t#S256")
    @Schema(description = "The SHA-256 thumbprint of the X.509 certificate")
    String x509CertificateSha256Thumbprint)
    {
  }

}
