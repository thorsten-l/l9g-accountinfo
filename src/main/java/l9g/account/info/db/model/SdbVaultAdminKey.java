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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents a stored application property as a JPA entity.
 * This entity is used to store key-value pairs for application configuration and state.
 *
 * @author Thorsten Ludewig <t.ludewig@gmail.com>
 */
@Entity
@NoArgsConstructor
@Getter
@ToString(callSuper = true)
@Table(name = "vaultadminkeys", uniqueConstraints =
{
  @UniqueConstraint(columnNames =
  {
    "credentialId"
  })
})
public class SdbVaultAdminKey extends SdbUuidObject
{
  private static final long serialVersionUID = -2466646557606272089L;

  public SdbVaultAdminKey(String createdBy, String adminId, String fullName,
    String description, String credentialId, String prfSalt,
    String encryptedMasterKey)
  {
    super(createdBy);
    this.adminId = adminId;
    this.fullName = fullName;
    this.description = description;
    this.credentialId = credentialId;
    this.prfSalt = prfSalt;
    this.encryptedMasterKey = encryptedMasterKey;
  }

  private String adminId;

  private String fullName;

  @Setter
  private String description;

  @Column(unique = true, nullable = false)
  private String credentialId;

  @Setter
  private String prfSalt;

  @Setter
  private String encryptedMasterKey;

}
