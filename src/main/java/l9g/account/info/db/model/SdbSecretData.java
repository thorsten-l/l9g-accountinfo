/*
 * Copyright 2025 Thorsten Ludewig <t.ludewig@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.l9g.crypto.jpa.EncryptedAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents sensitive data stored in the database as a JPA entity.
 * This entity supports storing various types of secrets, including encrypted strings and byte arrays,
 * with associated metadata like key, name, description, type, size, and checksum.
 *
 * @author Thorsten Ludewig <t.ludewig@gmail.com>
 */
@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name = "secretdata")
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class SdbSecretData extends SdbUuidObject
{
  /**
   * Serial Version UID.
   */
  private static final long serialVersionUID = 6137357483632195188l;

  /**
   * Constructs a new {@code SdbSecretData} instance.
   *
   * @param createdBy The identifier of the entity that created this secret data.
   * @param key The unique key for this secret data.
   * @param type The {@link SdbSecretType} indicating the nature of the secret.
   */
  public SdbSecretData(String createdBy, String key, SdbSecretType type)
  {
    super(createdBy, false);
    this.key = key;
    this.type = type;
  }

  /**
   * Constructs a new {@code SdbSecretData} instance, allowing specification of immutability.
   *
   * @param createdBy The identifier of the entity that created this secret data.
   * @param key The unique key for this secret data.
   * @param type The {@link SdbSecretType} indicating the nature of the secret.
   * @param immutable A boolean indicating if this secret data is immutable.
   */
  public SdbSecretData(
    String createdBy, String key, SdbSecretType type, boolean immutable)
  {
    super(createdBy, immutable);
    this.key = key;
    this.type = type;
  }

  @Column(name = "s_key", nullable = false)
  /**
   * The key associated with this secret data.
   */
  private String key;

  /**
   * A human-readable name for the secret data.
   */
  private String name;

  /**
   * A detailed description of the secret data.
   */
  private String description;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  /**
   * The type of the secret data, indicating its content or purpose.
   */
  private SdbSecretType type;

  @Column(nullable = false)
  /**
   * The size of the secret data in bytes.
   */
  private long size;

  @Column(nullable = false, length = 64)
  /**
   * The SHA-256 checksum of the original secret data.
   */
  private String checksum; // SHA-256 of the original file

  @Convert(converter = EncryptedAttributeConverter.class)
  @Column(length = 262144) // 256KB
  /**
   * The encrypted secret data itself, stored as a String.
   */
  private String secret;

  @Transient
  @JsonIgnore
  @ToString.Exclude
  /**
   * The transient (unpersisted) byte array representation of the secret data.
   */
  private byte[] value;

  /**
   * Sets the byte array value of the secret data.
   * This method also updates the size and calculates the SHA-256 checksum of the new value.
   * If the value is null or empty, size is set to 0 and checksum to null.
   *
   * @param value The byte array to set as the secret data's value.
   */
  public final void setValue(byte[] value)
  {
    this.value = value;
    if(value != null && value.length > 0)
    {
      this.size = value.length;
      this.checksum = calculateChecksum(value);
    }
    else
    {
      this.size = 0;
      this.checksum = null;
    }
  }

  /**
   * Sets the encrypted string secret.
   * This method also updates the size and calculates the SHA-256 checksum of the new secret.
   * If the secret is null or empty, size is set to 0 and checksum to null.
   *
   * @param secret The encrypted string to set as the secret data.
   */
  public void setSecret(String secret)
  {
    this.secret = secret;
    if(secret != null && secret.length() > 0)
    {
      this.size = secret.length();
      this.checksum = calculateChecksum(secret.getBytes());
    }
    else
    {
      this.size = 0;
      this.checksum = null;
    }
  }

  /**
   * Calculates the SHA-256 checksum of the provided byte array.
   *
   * @param data The byte array for which to calculate the checksum.
   *
   * @return The hexadecimal representation of the SHA-256 checksum.
   *
   * @throws RuntimeException If the SHA-256 algorithm is not available.
   */
  private String calculateChecksum(byte[] data)
  {
    try
    {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(data)); // Seit JDK 17
    }
    catch(NoSuchAlgorithmException e)
    {
      throw new RuntimeException("Could not calculate checksum", e);
    }
  }

}
