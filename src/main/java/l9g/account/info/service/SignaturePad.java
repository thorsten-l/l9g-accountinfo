/*
 * Copyright 2025 Thorsten Ludewig (t.ludewig@gmail.com).
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
package l9g.account.info.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Entity class representing a signature pad device and its configuration.
 */
@Slf4j
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
@Schema(name = "SignaturePad", description = "Represents a signature pad device and its configuration")
public class SignaturePad
{
  /**
   * Unique identifier for this signature pad, generated at creation time
   */
  @Schema(description = "Unique identifier for this signature pad, generated at creation time")
  private final String uuid;

  /**
   * Human-readable display name for the signature pad
   */
  @Setter
  @Schema(description = "Human-readable display name for the signature pad")
  private String name;

  /**
   * Flag indicating whether the signature pad has been validated and is ready for use
   */
  @Setter
  @Schema(description = "Flag indicating whether the signature pad has been validated and is ready for use")
  private boolean validated;

  /**
   * Client environment information received during validation
   */
  @Setter
  @Schema(description = "Client environment information received during validation")
  private Map<String, Object> clientEnvironment;

  /**
   * Version number for key rotation, incremented when new keys are generated
   */
  @Schema(description = "Version number for key rotation, incremented when new keys are generated")
  private int version;

  /**
   * Time-to-live value for the signature pad (currently unused)
   */
  @Schema(description = "Time-to-live value for the signature pad (currently unused)")
  private long ttl;

  /**
   * Public key in JWK format for signature verification
   */
  @Setter
  @Schema(description = "Public key in JWK format for signature verification")
  private Map<String, Object> publicJwk;

  /**
   * Default constructor for JSON deserialization.
   * Creates a new signature pad with a randomly generated UUID.
   */
  /**
   * Default constructor for JSON deserialization.
   * Creates a new signature pad with a randomly generated UUID.
   */
  public SignaturePad()
  {
    uuid = UUID.randomUUID().toString();
  }

  /**
   * Constructor for creating a new signature pad with a specified name.
   *
   * @param name the display name for the signature pad
   */
  /**
   * Constructs a new signature pad with a specified name.
   *
   * @param name The display name for the signature pad.
   */
  public SignaturePad(String name)
  {
    this();
    this.name = name;
  }

  /**
   * Gets the key identifier for the current version.
   * Combines UUID and version number to create a unique key identifier.
   *
   * @return the current key identifier in format "uuid-version"
   */
  /**
   * Gets the key identifier for the current version.
   * Combines UUID and version number to create a unique key identifier.
   *
   * @return The current key identifier in format "uuid-version".
   */
  @JsonIgnore
  public String getKeyId()
  {
    return uuid + "-" + version;
  }

  /**
   * Creates a new RSA key pair and returns the private key in JWK format.
   * Generates a 2048-bit RSA key pair, increments the version number,
   * and stores the public key for later verification.
   *
   * @return the private key in JSON Web Key (JWK) format as a string
   *
   * @throws NoSuchAlgorithmException if RSA algorithm is not available
   */
  /**
   * Creates a new RSA key pair and returns the private key in JWK format.
   * Generates a 2048-bit RSA key pair, increments the version number,
   * and stores the public key for later verification.
   *
   * @return The private key in JSON Web Key (JWK) format as a string.
   *
   * @throws NoSuchAlgorithmException If RSA algorithm is not available.
   */
  public String createPrivateJWK()
    throws NoSuchAlgorithmException
  {
    // Generate new RSA key pair
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    KeyPair keyPair = keyGen.generateKeyPair();
    RSAPrivateKey privateKey = (RSAPrivateKey)keyPair.getPrivate();
    RSAPublicKey rsaPublicKey = (RSAPublicKey)keyPair.getPublic();

    // Increment version for key rotation
    version ++;

    String kid = uuid + "-" + version;
    log.debug("create new JWK with key id={}", kid);

    // Build JWK with both public and private key components
    RSAKey fullJwk = new RSAKey.Builder(rsaPublicKey)
      .privateKey(privateKey)
      .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
      .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
      .keyID(kid)
      .build();

    // Store public key for verification
    publicJwk = fullJwk.toPublicJWK().toJSONObject();

    // Return full JWK (including private key) for the signature pad
    return fullJwk.toJSONString();
  }

}
