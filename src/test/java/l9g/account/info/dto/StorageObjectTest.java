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
package l9g.account.info.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import l9g.account.info.dto.StorageObject.EndUserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StorageObject} and its nested {@link EndUserData}.
 * <p>
 * {@code EndUserData.description()} implements the data-minimisation step whose
 * output {@code FileStorageService.buildSecretData} persists as the record
 * description, so its exact shape matters.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class StorageObjectTest
{
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp()
  {
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("description() merges the name and drops the separate name parts")
  void descriptionMinimisesTheNameParts()
  {
    EndUserData user = new EndUserData("pid-1", "m@example.org", "ignored",
      "mmuster", "Marie", "Muster");

    EndUserData description = user.description();

    assertThat(description.pid()).isEqualTo("pid-1");
    assertThat(description.mail()).isEqualTo("m@example.org");
    assertThat(description.username()).isEqualTo("mmuster");
    assertThat(description.name()).isEqualTo("Marie Muster");
    assertThat(description.givenName()).isNull();
    assertThat(description.surname()).isNull();
  }

  @Test
  @DisplayName("the persisted description JSON omits the nulled name parts")
  void descriptionJsonOmitsNullFields()
    throws Exception
  {
    EndUserData user = new EndUserData("pid-1", "m@example.org", "ignored",
      "mmuster", "Marie", "Muster");

    String json = objectMapper.writeValueAsString(user.description());

    assertThat(json)
      .doesNotContain("givenName")
      .doesNotContain("surname")
      .contains("\"name\":\"Marie Muster\"")
      .contains("\"username\":\"mmuster\"")
      .contains("\"pid\":\"pid-1\"")
      .contains("\"mail\":\"m@example.org\"");
  }

  /**
   * Missing name parts no longer leak the literal string {@code "null null"}
   * into the persisted description. Note that records written before this fix
   * keep their old value — the change only affects newly stored data.
   */
  @Test
  @DisplayName("without any name part the display name stays empty")
  void missingNamePartsYieldNoName()
  {
    EndUserData user =
      new EndUserData("pid-1", "m@example.org", null, "mmuster", null, null);

    assertThat(user.description().name()).isNull();
    assertThat(user.description().username()).isEqualTo("mmuster");
  }

  @Test
  @DisplayName("a single available name part is used on its own")
  void singleNamePartIsUsedAlone()
  {
    assertThat(new EndUserData(null, null, null, null, "Marie", null)
      .description().name()).isEqualTo("Marie");
    assertThat(new EndUserData(null, null, null, null, null, "Muster")
      .description().name()).isEqualTo("Muster");
  }

  @Test
  @DisplayName("a description without a name omits the key entirely in JSON")
  void descriptionWithoutNameOmitsTheKey()
    throws Exception
  {
    String json = objectMapper.writeValueAsString(
      new EndUserData("pid-1", null, null, "mmuster", null, null)
        .description());

    assertThat(json).doesNotContain("\"name\"");
    assertThat(json).contains("\"username\":\"mmuster\"");
  }

  @Test
  @DisplayName("a storage object round-trips through JSON with Base64 encoded data")
  void storageObjectRoundTripsThroughJson()
    throws Exception
  {
    byte[] payload = "ZIP-CONTENT".getBytes();
    StorageObject original = new StorageObject(
      l9g.account.info.db.model.SdbSecretType.EXT_IDENTIFICATION_ARCHIVE,
      new EndUserData("pid-1", "m@example.org", "Marie Muster", "mmuster",
        "Marie", "Muster"),
      null, payload);

    String json = objectMapper.writeValueAsString(original);
    StorageObject parsed = objectMapper.readValue(json, StorageObject.class);

    assertThat(json).contains(java.util.Base64.getEncoder()
      .encodeToString(payload));
    assertThat(parsed.type()).isEqualTo(original.type());
    assertThat(parsed.user()).isEqualTo(original.user());
    assertThat(parsed.data()).isEqualTo(payload);
  }

  @Test
  @DisplayName("equals compares the payload by content, not by reference")
  void equalsComparesDataByContent()
  {
    EndUserData user = new EndUserData("pid-1", null, null, "mmuster", null,
      null);
    StorageObject a = new StorageObject(null, user, null, "same".getBytes());
    StorageObject b = new StorageObject(null, user, null, "same".getBytes());
    StorageObject c = new StorageObject(null, user, null, "other".getBytes());

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a).isEqualTo(a);
    assertThat(a).isNotEqualTo(null);
    assertThat(a).isNotEqualTo("a string");
  }

  @Test
  @DisplayName("a null payload is handled by equals and hashCode")
  void equalsHandlesNullPayload()
  {
    StorageObject a = new StorageObject(null, null, null, null);
    StorageObject b = new StorageObject(null, null, null, null);

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(
      new StorageObject(null, null, null, new byte[0]));
  }

  /**
   * {@code data} holds identity document content, so it must never end up in a
   * log line. The generated {@code toString} printed the array's identity hash,
   * which was merely useless; the replacement reports the size instead.
   */
  @Test
  @DisplayName("toString reports the payload size instead of its content")
  void toStringDoesNotLeakThePayload()
  {
    StorageObject object = new StorageObject(
      l9g.account.info.db.model.SdbSecretType.EXT_IDENTIFICATION_ARCHIVE,
      new EndUserData("pid-1", null, null, "mmuster", null, null),
      null, "SECRET-ID-SCAN".getBytes());

    String rendered = object.toString();

    assertThat(rendered)
      .doesNotContain("SECRET-ID-SCAN")
      .contains("14 bytes")
      .contains("EXT_IDENTIFICATION_ARCHIVE")
      .contains("mmuster");
  }

  @Test
  @DisplayName("unknown JSON fields are ignored on both record levels")
  void unknownFieldsAreIgnored()
    throws Exception
  {
    StorageObject parsed = objectMapper.readValue(
      "{\"type\":\"EXT_IDENTIFICATION_STATUS\",\"brandNew\":1,"
      + "\"user\":{\"username\":\"mmuster\",\"alsoNew\":true}}",
      StorageObject.class);

    assertThat(parsed.user().username()).isEqualTo("mmuster");
  }

}
