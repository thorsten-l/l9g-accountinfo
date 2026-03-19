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
package l9g.account.info.db;

import l9g.account.info.db.model.SdbVaultAdminKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for managing {@link SdbVaultAdminKey} entities.
 */
@Repository
public interface SdbVaultAdminKeyRepository extends
  JpaRepository<SdbVaultAdminKey, String>
{
  /**
   * Finds all vault admin keys for a specific admin ID.
   *
   * @param adminId The identifier of the admin.
   * @return A list of vault admin keys.
   */
  List<SdbVaultAdminKey> findByAdminIdIgnoreCase(String adminId);

  /**
   * Finds a vault admin key by its credential ID.
   *
   * @param credentialId The unique credential identifier.
   * @return An optional containing the vault admin key if found.
   */
  Optional<SdbVaultAdminKey> findByCredentialId(String credentialId);

  /**
   * Deletes a vault admin key by its credential ID.
   *
   * @param credentialId The unique credential identifier.
   */
  void deleteByCredentialId(String credentialId);
}
