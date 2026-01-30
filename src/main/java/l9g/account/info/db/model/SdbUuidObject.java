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
  private static final long serialVersionUID = 1575497541642600225l;

  //~--- constructors ---------------------------------------------------------
  public SdbUuidObject(String createdBy, boolean immutable, boolean hidden )
  {
    this.createdBy = this.modifiedBy = createdBy;
    this.id = UUID.randomUUID().toString();
    this.immutable = immutable;
    this.hidden = hidden;
  }

  public SdbUuidObject(String createdBy, boolean immutable)
  {
    this( createdBy, immutable, false );
  }

  public SdbUuidObject(String createdBy)
  {
    this(createdBy, false);
  }

  @PrePersist
  public void prePersist()
  {
    log.debug("prePersist " + this.getClass().getCanonicalName());
    this.createTimestamp = this.modifyTimestamp = new Date();
  }

  /**
   * Method description
   *
   */
  @PreRemove
  public void preRemove()
  {
    log.debug("preRemove {} {}",
      this.getClass().getCanonicalName(), id);
    /*
    if(immutable)
    {
      log.error("Attempted to remove an immutable object: {} {}",
        this.getClass().getCanonicalName(), id);
      throw new IllegalStateException("Cannot remove an immutable object.");
    }
     */
    log.debug("done");
  }

  /**
   * Method description
   *
   */
  @PreUpdate
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

  @Override
  public int hashCode()
  {
    return Objects.hashCode(this.id);
  }

  public void setModifiedBy(String modifiedBy)
  {
    this.modifiedBy = modifiedBy;
    this.modifyTimestamp = new Date();
  }

  @Column(updatable = false)
  private String createdBy;

  private String modifiedBy;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(updatable = false)
  protected Date createTimestamp;

  @Temporal(TemporalType.TIMESTAMP)
  protected Date modifyTimestamp;

  private boolean immutable;

  @Setter
  private boolean hidden;

  @Id
  @Column(length = 40, updatable = false)
  private String id;

}
