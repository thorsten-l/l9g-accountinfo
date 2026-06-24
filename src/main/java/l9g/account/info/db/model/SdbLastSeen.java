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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Tracks the most recent time a user was observed ("last seen") in the source
 * directory (LDAP). One row per (tenant, user). The {@link #timestamp} is
 * always refreshed to "now" whenever the row is inserted or updated, so a
 * re-import overwrites the previous entry with the current time.
 * <p>
 * This table is the basis for the deletion concept driven by
 * {@code l9g.account.info.db.DbDeletionScheduler}: users no longer present in
 * the directory keep their last (now stale) timestamp.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Entity
@Table(name = "lastseen")
@IdClass(SdbLastSeenId.class)
@NoArgsConstructor
@Getter
@Setter
@ToString
public class SdbLastSeen implements Serializable
{
  /**
   * Serial Version UID.
   */
  private static final long serialVersionUID = 7561357483632195188l;

  /**
   * The tenant identifier. Currently a constant ("sonia"), part of the
   * composite primary key together with {@link #username}.
   */
  @Id
  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  /**
   * The user's unique login name, part of the composite primary key.
   */
  @Id
  @Column(name = "username", nullable = false, length = 128)
  private String username;

  /**
   * The user's first name.
   */
  @Column(name = "firstname")
  private String firstname;

  /**
   * The user's last name.
   */
  @Column(name = "lastname")
  private String lastname;

  /**
   * The user's email address.
   */
  @Column(name = "mail")
  private String mail;

  /**
   * The timestamp the user was last seen in the directory. Automatically set
   * to "now" on every insert and update (see {@link #touch()}).
   */
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "last_seen_timestamp", nullable = false)
  private Date timestamp;

  /**
   * Constructs a "last seen" entry with the timestamp set to "now".
   *
   * @param tenantId The tenant identifier.
   * @param username The user's unique login name.
   * @param firstname The user's first name.
   * @param lastname The user's last name.
   * @param mail The user's email address.
   */
  public SdbLastSeen(String tenantId, String username, String firstname,
    String lastname, String mail)
  {
    this.tenantId = tenantId;
    this.username = username;
    this.firstname = firstname;
    this.lastname = lastname;
    this.mail = mail;
    this.timestamp = new Date();
  }

  /**
   * Refreshes {@link #timestamp} to the current time before the row is
   * persisted or updated, guaranteeing the stored value is always "now".
   */
  @PrePersist
  @PreUpdate
  public void touch()
  {
    this.timestamp = new Date();
  }

}
