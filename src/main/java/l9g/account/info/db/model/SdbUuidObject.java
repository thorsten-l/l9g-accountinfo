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
import jakarta.persistence.Id;
import lombok.Getter;

//~--- JDK imports ------------------------------------------------------------
import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Objects;
import java.util.UUID;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for JPA entities that require a UUID as a primary key
 * and automatically manage creation and modification timestamps, along with
 * audit fields.
 *
 * @author Dr. Thorsten Ludewig (t.ludewig@ostfalia.de)
 */
@MappedSuperclass
@NoArgsConstructor
@Slf4j
@Getter
@ToString
public class SdbUuidObject implements Serializable
{
  /**
   * Serial Version UID.
   */
  private static final long serialVersionUID = 1575497541642600225l;

  //~--- constructors ---------------------------------------------------------
  /**
   * Constructs a new {@code SdbUuidObject} with specified creation details, immutability, and hidden status.
   * A unique UUID is generated for the object's ID.
   *
   * @param createdBy The identifier of the user or system that created this object.
   * @param immutable If true, this object cannot be updated after creation.
   * @param hidden If true, this object is hidden from standard views or queries.
   */
  public SdbUuidObject(String createdBy, boolean immutable, boolean hidden)
  {
    this.createdBy = this.modifiedBy = createdBy;
    this.id = UUID.randomUUID().toString();
    this.immutable = immutable;
    this.hidden = hidden;
  }

  /**
   * Constructs a new {@code SdbUuidObject} with specified creation details and immutability.
   * The object is not hidden by default. A unique UUID is generated for the object's ID.
   *
   * @param createdBy The identifier of the user or system that created this object.
   * @param immutable If true, this object cannot be updated after creation.
   */
  public SdbUuidObject(String createdBy, boolean immutable)
  {
    this(createdBy, immutable, false);
  }

  /**
   * Constructs a new {@code SdbUuidObject} with specified creation details.
   * The object is not immutable and not hidden by default. A unique UUID is generated for the object's ID.
   *
   * @param createdBy The identifier of the user or system that created this object.
   */
  public SdbUuidObject(String createdBy)
  {
    this(createdBy, false);
  }

  @PrePersist
  /**
   * Sets the creation and modification timestamps before the entity is first persisted.
   */
  public void prePersist()
  {
    log.debug("prePersist " + this.getClass().getCanonicalName());
    this.createTimestamp = this.modifyTimestamp = new Date();
  }

  @PreRemove
  /**
   * Logs a debug message before an entity is removed.
   * Currently, it is commented out to prevent removal of immutable objects.
   */
  public void preRemove()
  {
    log.debug("preRemove {} {}",
      this.getClass().getCanonicalName(), id);

    if(immutable)
    {
      log.error("Attempted to remove an immutable object: {} {}",
        this.getClass().getCanonicalName(), id);
      throw new IllegalStateException("Cannot remove an immutable object.");
    }

    log.debug("done");
  }

  @PreUpdate
  /**
   * Updates the modification timestamp before an entity is updated.
   * Throws an {@link IllegalStateException} if an attempt is made to update an immutable object.
   */
  public void preUpdate()
  {
    log.debug("preUpdate {} {}",
      this.getClass().getCanonicalName(), id);
    if(immutable)
    {
      log.error("Attempted to update an immutable object: {} {}",
        this.getClass().getCanonicalName(), id);
      throw new IllegalStateException("Cannot update an immutable object.");
    }
    this.modifyTimestamp = new Date();
  }

  //~--- methods --------------------------------------------------------------
  /**
   * Compares this {@code SdbUuidObject} to the specified object.
   * The result is {@code true} if and only if the argument is not null and is an {@code SdbUuidObject} object
   * that has the same ID as this object.
   *
   * @param obj The object to compare this {@code SdbUuidObject} against.
   *
   * @return {@code true} if the given object represents an {@code SdbUuidObject} equivalent to this object, {@code false} otherwise.
   */
  @Override
  public boolean equals(Object obj)
  {
    boolean same = false;

    if(this == obj)
    {
      return true;
    }

    if((obj != null) && (obj instanceof SdbUuidObject))
    {
      same = this.getId().equals(((SdbUuidObject)obj).getId());
    }

    return same;
  }

  /**
   * Returns a hash code value for the object.
   * This method is supported for the benefit of hash tables such as those provided by {@link java.util.HashMap}.
   * The hash code is based on the object's unique ID.
   *
   * @return A hash code value for this object.
   */
  @Override
  public int hashCode()
  {
    return Objects.hashCode(this.id);
  }

  /**
   * Sets the identifier of the entity that last modified this object and updates the modification timestamp.
   *
   * @param modifiedBy The identifier of the modifier.
   */
  public void setModifiedBy(String modifiedBy)
  {
    this.modifiedBy = modifiedBy;
    this.modifyTimestamp = new Date();
  }

  @Column(updatable = false)
  /**
   * The identifier of the user or system that created this object.
   */
  private String createdBy;

  /**
   * The identifier of the user or system that last modified this object.
   */
  private String modifiedBy;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(updatable = false)
  /**
   * The timestamp when this object was created.
   */
  protected Date createTimestamp;

  @Temporal(TemporalType.TIMESTAMP)
  /**
   * The timestamp when this object was last modified.
   */
  protected Date modifyTimestamp;

  /**
   * Flag indicating if this object is immutable (cannot be updated after creation).
   */
  private boolean immutable;

  @Setter
  /**
   * Flag indicating if this object is hidden from standard views or queries.
   */
  private boolean hidden;

  @Id
  @Column(length = 40, updatable = false)
  /**
   * The unique identifier (UUID) of this object.
   */
  private String id;

}
