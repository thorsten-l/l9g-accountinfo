/*
 * Copyright 2026 Thorsten Ludewig (t.ludewig@gmail.com).
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
package l9g.account.info.db;

import java.util.Date;
import java.util.List;
import l9g.account.info.db.model.SdbLastSeen;
import l9g.account.info.db.model.SdbLastSeenId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for managing {@link SdbLastSeen} entities,
 * keyed by the composite {@link SdbLastSeenId} (tenant id + username).
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Repository
public interface SdbLastSeenRepository extends
  JpaRepository<SdbLastSeen, SdbLastSeenId>
{
  /**
   * Finds all entries last seen strictly before the given cutoff timestamp,
   * i.e. deletion candidates whose grace period has elapsed.
   *
   * @param cutoff The cutoff timestamp ({@code now - grace period}).
   *
   * @return The matching entries (never {@code null}).
   */
  List<SdbLastSeen> findByTimestampBefore(Date cutoff);

}
