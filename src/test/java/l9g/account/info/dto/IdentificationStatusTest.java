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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link IdentificationStatus}, the WebID callback payload.
 * The decisive property is the {@code WRITE_ONLY} treatment of
 * {@code fileResponseDownload}: the one-time download token must be readable
 * from the incoming callback but must never be serialised back out into logs or
 * the persisted audit record.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class IdentificationStatusTest
{
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp()
  {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Test
  @DisplayName("isVerified is true only for an explicit success flag")
  void isVerifiedIsThreeWay()
    throws Exception
  {
    assertThat(objectMapper.readValue("{\"success\":true}",
      IdentificationStatus.class).isVerified()).isTrue();
    assertThat(objectMapper.readValue("{\"success\":false}",
      IdentificationStatus.class).isVerified()).isFalse();
    assertThat(objectMapper.readValue("{}",
      IdentificationStatus.class).isVerified())
      .as("an absent success flag must not throw and must not count as verified")
      .isFalse();
  }

  /**
   * The security invariant of this DTO: the download token arrives from the
   * identification provider and is needed to fetch the result archive, but it
   * must never leave the application again. A regression that drops the
   * {@code WRITE_ONLY} access would leak a live one-time token into every log
   * line and into the persisted audit JSON.
   */
  @Test
  @DisplayName("the download token is read from the callback but never written back out")
  void downloadTokenIsWriteOnly()
    throws Exception
  {
    String callback = "{\"transactionId\":\"tx-1\",\"success\":true,"
      + "\"fileResponseDownload\":{\"downloadToken\":\"one-time-token\","
      + "\"validUntil\":\"2026-08-23T10:00:00Z\"}}";

    IdentificationStatus status =
      objectMapper.readValue(callback, IdentificationStatus.class);

    assertThat(status.fileResponseDownload()).isNotNull();
    assertThat(status.fileResponseDownload().downloadToken())
      .isEqualTo("one-time-token");

    String serialized = objectMapper.writeValueAsString(status);

    assertThat(serialized).doesNotContain("fileResponseDownload");
    assertThat(serialized).doesNotContain("one-time-token");
    assertThat(serialized).contains("tx-1");
  }

  @Test
  @DisplayName("unknown callback fields are ignored instead of failing")
  void unknownFieldsAreIgnored()
  {
    String callback = "{\"transactionId\":\"tx-1\","
      + "\"someBrandNewProviderField\":{\"nested\":123},"
      + "\"anotherOne\":\"value\"}";

    assertThatCode(
      () -> objectMapper.readValue(callback, IdentificationStatus.class))
      .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("the product field is bound from the JSON name 'product'")
  void productIsBoundByJsonName()
    throws Exception
  {
    IdentificationStatus status = objectMapper.readValue(
      "{\"product\":{\"name\":\"WebID-Ident\"}}", IdentificationStatus.class);

    assertThat(status.product()).isNotNull();
    assertThat(status.product().name()).isEqualTo("WebID-Ident");
  }

  @Test
  @DisplayName("nested user and document data are bound including date types")
  void nestedStructuresAreBound()
    throws Exception
  {
    String callback = "{\"success\":true,"
      + "\"identifiedOn\":\"2026-08-23T09:30:00Z\","
      + "\"user\":{\"firstname\":\"Marie\",\"lastname\":\"Muster\","
      + "\"dateOfBirth\":\"1990-05-17\","
      + "\"address\":{\"city\":\"Wolfenbuettel\",\"zip\":\"38302\"},"
      + "\"contact\":{\"email\":\"m@example.org\"}},"
      + "\"idDocument\":{\"documentType\":\"ID_CARD\","
      + "\"dateOfExpiry\":\"2030-01-01\"}}";

    IdentificationStatus status =
      objectMapper.readValue(callback, IdentificationStatus.class);

    assertThat(status.identifiedOn()).isNotNull();
    assertThat(status.user().firstname()).isEqualTo("Marie");
    assertThat(status.user().dateOfBirth().toString()).isEqualTo("1990-05-17");
    assertThat(status.user().address().city()).isEqualTo("Wolfenbuettel");
    assertThat(status.user().contact().email()).isEqualTo("m@example.org");
    assertThat(status.idDocument().documentType()).isEqualTo("ID_CARD");
  }

}
