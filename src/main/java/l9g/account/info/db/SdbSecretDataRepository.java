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

import java.util.List;
import l9g.account.info.db.model.SdbSecretData;
import java.util.Optional;
import l9g.account.info.db.model.SdbSecretType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for managing {@link SdbSecretData} entities.
 * Provides methods for CRUD operations and custom queries for secret data.
 *
 * @author Thorsten Ludewig <t.ludewig@gmail.com>
 */
@Repository
public interface SdbSecretDataRepository extends
  JpaRepository<SdbSecretData, String>
{
  /**
   * Finds a list of {@link SdbSecretData} entities by their key, ordered by modification timestamp in descending order.
   *
   * @param key The key of the secret data to find.
   *
   * @return An {@link Optional} containing a list of found {@link SdbSecretData} entities, or empty if none found.
   */
  Optional<List<SdbSecretData>> findByKeyOrderByModifyTimestampDesc(String key);

  /**
   * Finds a list of {@link SdbSecretData} entities by their key and type, ordered by modification timestamp in descending order.
   *
   * @param key The key of the secret data to find.
   * @param type The type of the secret data to find.
   *
   * @return An {@link Optional} containing a list of found {@link SdbSecretData} entities, or empty if none found.
   */
  Optional<List<SdbSecretData>> findByKeyAndTypeOrderByModifyTimestampDesc(String key, SdbSecretType type);

}
