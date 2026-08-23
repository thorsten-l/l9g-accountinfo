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

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the integrity metadata that {@link SdbSecretData} derives from
 * its payload. The checksum is the only means of detecting corruption of an
 * encrypted file after the fact, so it is verified against published SHA-256
 * test vectors rather than against the implementation itself.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class SdbSecretDataTest
{
  /**
   * SHA-256 of the ASCII string "abc" (FIPS 180-4 example B.1).
   */
  private static final String SHA256_ABC =
    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

  /**
   * SHA-256 of the empty byte sequence.
   */
  private static final String SHA256_EMPTY =
    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  private static SdbSecretData newData()
  {
    return new SdbSecretData("publisher", "pad-uuid",
      SdbSecretType.ID_FRONT_IMAGE);
  }

  @Test
  @DisplayName("setValue derives the size and the SHA-256 checksum of the payload")
  void setValueDerivesSizeAndChecksum()
  {
    SdbSecretData data = newData();

    data.setValue("abc".getBytes(StandardCharsets.UTF_8));

    assertThat(data.getSize()).isEqualTo(3);
    assertThat(data.getChecksum()).isEqualTo(SHA256_ABC);
  }

  @Test
  @DisplayName("setValue resets the metadata for null and empty payloads")
  void setValueResetsForEmptyPayload()
  {
    SdbSecretData data = newData();
    data.setValue("abc".getBytes(StandardCharsets.UTF_8));

    data.setValue(null);
    assertThat(data.getSize()).isZero();
    assertThat(data.getChecksum()).isNull();

    data.setValue("abc".getBytes(StandardCharsets.UTF_8));
    data.setValue(new byte[0]);
    assertThat(data.getSize()).isZero();
    assertThat(data.getChecksum())
      .as("an empty payload must NOT be recorded with the SHA-256 of the "
        + "empty string (%s), it must clear the checksum", SHA256_EMPTY)
      .isNull();
  }

  @Test
  @DisplayName("setValue is order-independent - the last payload wins")
  void setValueOverwritesPreviousMetadata()
  {
    SdbSecretData data = newData();

    data.setValue(new byte[1024]);
    assertThat(data.getSize()).isEqualTo(1024);

    data.setValue("abc".getBytes(StandardCharsets.UTF_8));
    assertThat(data.getSize()).isEqualTo(3);
    assertThat(data.getChecksum()).isEqualTo(SHA256_ABC);
  }

  @Test
  @DisplayName("setSecret derives the size and checksum for ASCII input")
  void setSecretDerivesSizeAndChecksumForAscii()
  {
    SdbSecretData data = newData();

    data.setSecret("abc");

    assertThat(data.getSize()).isEqualTo(3);
    assertThat(data.getChecksum()).isEqualTo(SHA256_ABC);
  }

  @Test
  @DisplayName("setSecret resets the metadata for null and empty input")
  void setSecretResetsForEmptyInput()
  {
    SdbSecretData data = newData();
    data.setSecret("abc");

    data.setSecret(null);
    assertThat(data.getSize()).isZero();
    assertThat(data.getChecksum()).isNull();

    data.setSecret("abc");
    data.setSecret("");
    assertThat(data.getSize()).isZero();
    assertThat(data.getChecksum()).isNull();
  }

  /**
   * Intended behaviour, pinned deliberately: {@code setSecret} records
   * {@code secret.length()} — a count of UTF-16 characters — as the size, while
   * the checksum is computed over {@code secret.getBytes()}. For non-ASCII
   * content the two therefore describe different lengths. Contrast with
   * {@code setValue}, where size and checksum agree by construction.
   * <p>
   * Accepted as-is: the checksum remains a valid integrity check over the stored
   * string, and {@code size} is a display and quota figure rather than a
   * statement about the checksummed byte count. Changing it would invalidate the
   * checksums of every record already stored.
   * <p>
   * The unqualified {@code getBytes()} is safe here on this project's Java 21
   * baseline: since JEP 400 (Java 18) the default charset is UTF-8, so the
   * checksum is reproducible across environments unless someone explicitly sets
   * {@code -Dfile.encoding}.
   */
  @Test
  @DisplayName("setSecret size counts characters while the checksum covers bytes, as intended")
  void setSecretSizeAndChecksumDisagreeForNonAscii()
  {
    SdbSecretData data = newData();
    String umlauts = "äöü";

    data.setSecret(umlauts);

    assertThat(umlauts.length()).isEqualTo(3);
    assertThat(umlauts.getBytes(StandardCharsets.UTF_8)).hasSize(6);

    assertThat(data.getSize())
      .as("size is the character count, not the byte count")
      .isEqualTo(3);

    SdbSecretData reference = newData();
    reference.setValue(umlauts.getBytes());
    assertThat(data.getChecksum())
      .as("the checksum however covers the encoded bytes")
      .isEqualTo(reference.getChecksum());
    assertThat(reference.getSize())
      .as("so the byte-based path records a different size for the same input")
      .isNotEqualTo(data.getSize());
  }

  @Test
  @DisplayName("a new record is mutable and carries the constructor arguments")
  void constructorArgumentsAreApplied()
  {
    SdbSecretData data = new SdbSecretData("publisher", "pad-uuid",
      SdbSecretType.EXT_IDENTIFICATION_ARCHIVE, true);

    assertThat(data.getCreatedBy()).isEqualTo("publisher");
    assertThat(data.getKey()).isEqualTo("pad-uuid");
    assertThat(data.getType())
      .isEqualTo(SdbSecretType.EXT_IDENTIFICATION_ARCHIVE);
    assertThat(data.isImmutable()).isTrue();
    assertThat(newData().isImmutable()).isFalse();
  }

}
