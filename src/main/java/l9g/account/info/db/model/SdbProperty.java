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

import com.fasterxml.jackson.annotation.JsonInclude;
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
@Setter
@Table(name = "properties", uniqueConstraints =
     {
       @UniqueConstraint(columnNames =
       {
         "p_key"
       })
})
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class SdbProperty extends SdbUuidObject
{
  /**
   * Serial Version UID.
   */
  private static final long serialVersionUID = 2377357483632195188l;

  /**
   * Constructs a new SdbProperty.
   *
   * @param createdBy The user who created this property.
   * @param key The unique key of the property.
   * @param value The value associated with the property.
   */
  public SdbProperty(String createdBy, String key, String value)
  {
    super(createdBy);
    this.key = key;
    this.value = value;
  }

  @Column(name = "p_key", nullable = false)
  /**
   * The unique key of the property.
   */
  private String key;

  @Column(name = "p_value", length = 2048)
  /**
   * The value associated with the property.
   */
  private String value;

}
