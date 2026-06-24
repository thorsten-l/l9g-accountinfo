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
package l9g.account.info.db.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link SdbLastSeen}, made up of the tenant id and
 * the username. Field names must match the {@code @Id} fields of the entity.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
public class SdbLastSeenId implements Serializable
{
  /**
   * Serial Version UID.
   */
  private static final long serialVersionUID = 7461357483632195188l;

  /**
   * The tenant identifier (currently constant "sonia").
   */
  private String tenantId;

  /**
   * The user's unique login name.
   */
  private String username;

  /**
   * Default constructor required by JPA.
   */
  public SdbLastSeenId()
  {
  }

  /**
   * Constructs a composite key.
   *
   * @param tenantId The tenant identifier.
   * @param username The username.
   */
  public SdbLastSeenId(String tenantId, String username)
  {
    this.tenantId = tenantId;
    this.username = username;
  }

  @Override
  public boolean equals(Object obj)
  {
    if(this == obj)
    {
      return true;
    }
    if( ! (obj instanceof SdbLastSeenId other))
    {
      return false;
    }
    return Objects.equals(tenantId, other.tenantId)
      && Objects.equals(username, other.username);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(tenantId, username);
  }

}
