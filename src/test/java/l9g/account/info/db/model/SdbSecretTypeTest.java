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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SdbSecretType#fromString(String)}. The aliases are part
 * of the public API surface: {@code ApiScanController} passes the {@code side}
 * request parameter straight into this method.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class SdbSecretTypeTest
{
  @ParameterizedTest
  @CsvSource(
    {
      "front, ID_FRONT_IMAGE",
      "back, ID_BACK_IMAGE",
      "signature, ID_SIGNATURE_JWT",
      "pad, SIGNATURE_PAD_JSON",
      "ext-id-status, EXT_IDENTIFICATION_STATUS",
      "ext-id-archive, EXT_IDENTIFICATION_ARCHIVE"
    })
  @DisplayName("every documented alias resolves to its type")
  void aliasesResolve(String alias, SdbSecretType expected)
  {
    assertThat(SdbSecretType.fromString(alias)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
    {
      "FRONT, ID_FRONT_IMAGE",
      "Back, ID_BACK_IMAGE",
      "SiGnAtUrE, ID_SIGNATURE_JWT",
      "EXT-ID-ARCHIVE, EXT_IDENTIFICATION_ARCHIVE"
    })
  @DisplayName("alias matching is case-insensitive")
  void aliasMatchingIsCaseInsensitive(String alias, SdbSecretType expected)
  {
    assertThat(SdbSecretType.fromString(alias)).isEqualTo(expected);
  }

  @Test
  @DisplayName("null is rejected with a dedicated message")
  void nullIsRejected()
  {
    assertThatThrownBy(() -> SdbSecretType.fromString(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Type string cannot be null");
  }

  @Test
  @DisplayName("an unknown alias is rejected and echoed in the message")
  void unknownAliasIsRejected()
  {
    assertThatThrownBy(() -> SdbSecretType.fromString("sideways"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Unknown SdbSecretType: sideways");
  }

  @Test
  @DisplayName("no whitespace is trimmed and the enum name itself is not an alias")
  void noTrimAndNoEnumNameAlias()
  {
    assertThatThrownBy(() -> SdbSecretType.fromString("front "))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SdbSecretType.fromString("ID_FRONT_IMAGE"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * The alias for {@code EXT_IDENTIFICATION_STATUS} used to be misspelled
   * {@code "est-id-status"} while its counterpart was correctly spelled
   * {@code "ext-id-archive"}. The typo is fixed; the old spelling is gone.
   * <p>
   * That is safe here: the only caller of {@code fromString} is
   * {@code FileStorageService.saveSecretFileData}, which passes the {@code side}
   * request parameter, and the client only ever sends {@code front} or
   * {@code back}. The {@code EXT_IDENTIFICATION_*} types reach the application
   * through {@code StorageController}, where Jackson resolves them by enum
   * name rather than through this method.
   */
  @Test
  @DisplayName("the status alias is spelled consistently with the archive alias")
  void statusAliasIsSpelledConsistently()
  {
    assertThat(SdbSecretType.fromString("ext-id-status"))
      .isEqualTo(SdbSecretType.EXT_IDENTIFICATION_STATUS);
    assertThat(SdbSecretType.fromString("ext-id-archive"))
      .isEqualTo(SdbSecretType.EXT_IDENTIFICATION_ARCHIVE);

    assertThatThrownBy(() -> SdbSecretType.fromString("est-id-status"))
      .as("the old misspelling is no longer accepted")
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Unknown SdbSecretType: est-id-status");
  }

}
