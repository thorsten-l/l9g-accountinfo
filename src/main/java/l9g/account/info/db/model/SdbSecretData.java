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
import l9g.account.info.db.EncryptedAttributeConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
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
  private static final long serialVersionUID = 6137357483632195188l;

  public SdbSecretData(String createdBy, String key, SdbSecretType type)
  {
    super(createdBy, false);
    this.key = key;
    this.type = type;
  }

  public SdbSecretData(
    String createdBy, String key, SdbSecretType type, boolean immutable)
  {
    super(createdBy, immutable);
    this.key = key;
    this.type = type;
  }

  @Column(name = "s_key", nullable = false)
  private String key;

  private String name;

  private String description;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private SdbSecretType type;

  @Column(nullable = false)
  private long size;

  @Column(nullable = false, length = 64)
  private String checksum; // SHA-256 of the original file

  @Convert(converter = EncryptedAttributeConverter.class)
  @Column(length = 262144) // 256KB
  private String secret;

  @Transient
  @JsonIgnore
  @ToString.Exclude
  private byte[] value;

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
