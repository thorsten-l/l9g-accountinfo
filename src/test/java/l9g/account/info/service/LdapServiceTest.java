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
package l9g.account.info.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LdapService#sanitizeFilterValue(String)}, the single
 * barrier against LDAP filter injection.
 * <p>
 * Every filter in {@code LdapService} is assembled with {@code String.format},
 * so a user-supplied value is interpolated unescaped into an assertion such as
 * {@code (givenName=%s*)} or
 * {@code (|(soniaChipcardBarcode=%s)(soniaCustomerNumber=%s)…)}. These tests
 * cover both halves of the contract: nothing that could break out of an
 * assertion survives, and everything the search interfaces actually receive
 * passes through untouched.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class LdapServiceTest
{
  // ------------------------------------------------------- compatibility

  /**
   * The operational contract: the person search boxes only ever receive letters
   * and spaces, and card numbers are digits. Those inputs must reach the
   * directory byte for byte, otherwise the fix would change search results.
   */
  @ParameterizedTest
  @ValueSource(strings =
  {
    "muster",
    "Marie Muster",
    "marie muster",
    "MUSTER",
    "Jörg Müller",
    "Müller",
    "Öztürk",
    "Français",
    "Große",
    "091600045759",
    "00123456",
    "1",
    "Müller-Lüdenscheidt",
    "Georg-Martin",
    "Georg-Martin Müller",
    "O'Brien",
    "Jr. Muster",
    "a b c d e"
  })
  @DisplayName("letters, digits and spaces pass through byte for byte")
  void legitimateInputIsUnchanged(String input)
  {
    assertThat(LdapService.sanitizeFilterValue(input)).isEqualTo(input);
  }

  @Test
  @DisplayName("a card number of digits is never altered")
  void cardNumbersAreNeverAltered()
  {
    for(int i = 0; i < 20; i ++ )
    {
      String cardNumber = String.valueOf(900000000000L + i);
      assertThat(LdapService.sanitizeFilterValue(cardNumber))
        .isEqualTo(cardNumber);
    }
  }

  // ------------------------------------------------------------ injection

  /**
   * The characters that can terminate an assertion or introduce a new filter
   * component. With these gone, appending filter logic is structurally
   * impossible.
   */
  @ParameterizedTest
  @ValueSource(strings =
  {
    "(", ")", "*", "\\", "&", "|", "=", "!", "<", ">", "~", ",", ":", "+",
    "\"", ";", "\n", "\r", "\t"
  })
  @DisplayName("every character with meaning in a filter is removed")
  void filterMetacharactersAreRemoved(String metacharacter)
  {
    assertThat(LdapService.sanitizeFilterValue("a" + metacharacter + "b"))
      .isEqualTo("ab");
  }

  /**
   * A NUL byte cannot be written into an annotation value, so it gets its own
   * test. RFC 4515 requires it to be escaped in a filter; dropping it is
   * equally effective and simpler.
   */
  @Test
  @DisplayName("a NUL byte is removed")
  void nulByteIsRemoved()
  {
    String withNul = "a" + ((char)0) + "b";

    assertThat(LdapService.sanitizeFilterValue(withNul)).isEqualTo("ab");
  }

  /**
   * The counterpart to the metacharacter test: the separators that names
   * legitimately contain must survive, otherwise "Marie Muster" would collapse
   * into one token and double-barrelled names would be mangled.
   */
  @ParameterizedTest
  @CsvSource(value =
  {
    "Marie Muster        ; Marie Muster",
    "Georg-Martin        ; Georg-Martin",
    "Georg-Martin Müller ; Georg-Martin Müller",
    "Müller-Lüdenscheidt ; Müller-Lüdenscheidt",
    "Anna-Lena Schmidt   ; Anna-Lena Schmidt",
    "O'Brien             ; O'Brien",
    "Jr. Muster          ; Jr. Muster"
  }, delimiter = ';')
  @DisplayName("space, hyphen, apostrophe and period are preserved in names")
  void nameSeparatorsArePreserved(String raw, String expected)
  {
    assertThat(LdapService.sanitizeFilterValue(raw.trim()))
      .isEqualTo(expected.trim());
  }

  @ParameterizedTest
  @CsvSource(value =
  {
    "muster                                 ; muster",
    ")(objectClass=*                        ; objectClass",
    "*                                      ; ",
    "*)(uid=*))(|(uid=*                     ; uiduid",
    "x)(|(soniaIsUnregistered=*             ; xsoniaIsUnregistered",
    "muster)(!(soniaIsUnregistered=*)       ; mustersoniaIsUnregistered",
    "a<=b                                   ; ab",
    "a~=b                                   ; ab",
    "cn=admin                               ; cnadmin"
  }, delimiter = ';')
  @DisplayName("known injection attempts are reduced to harmless text")
  void injectionAttemptsAreNeutralised(String raw, String expected)
  {
    String sanitized = LdapService.sanitizeFilterValue(raw.trim());

    assertThat(sanitized).isEqualTo(expected == null ? "" : expected.trim());
    assertThat(sanitized)
      .as("no character that could close or open an assertion may survive")
      .doesNotContain("(").doesNotContain(")").doesNotContain("*")
      .doesNotContain("\\").doesNotContain("=");
  }

  /**
   * The value is interpolated into the real filter template to show that the
   * result is a single, well-formed assertion rather than injected filter logic.
   */
  @Test
  @DisplayName("the resulting filter stays a single assertion")
  void resultingFilterStaysOneAssertion()
  {
    String template =
      "(&(givenName=%s*)(sn=%s*)(objectClass=soniaPerson))";
    String attack = ")(objectClass=*))(|(uid=";

    String filter = String.format(template,
      LdapService.sanitizeFilterValue(attack),
      LdapService.sanitizeFilterValue(attack));

    assertThat(filter).isEqualTo(
      "(&(givenName=objectClassuid*)(sn=objectClassuid*)"
      + "(objectClass=soniaPerson))");
  }

  // ---------------------------------------------------------------- edges

  @Test
  @DisplayName("null and empty input yield an empty value")
  void nullAndEmptyInput()
  {
    assertThat(LdapService.sanitizeFilterValue(null)).isEmpty();
    assertThat(LdapService.sanitizeFilterValue("")).isEmpty();
    assertThat(LdapService.sanitizeFilterValue("()*\\")).isEmpty();
  }

  @Test
  @DisplayName("sanitizing is idempotent")
  void sanitizingIsIdempotent()
  {
    String once = LdapService.sanitizeFilterValue("Marie *)(Muster");

    assertThat(LdapService.sanitizeFilterValue(once)).isEqualTo(once);
  }

  /**
   * The person search splits the sanitized value on whitespace and uses the
   * first two tokens, so removing characters must not merge or lose tokens for
   * ordinary input.
   */
  @Test
  @DisplayName("whitespace structure is preserved for tokenizing")
  void whitespaceStructureIsPreserved()
  {
    assertThat(LdapService.sanitizeFilterValue("Marie Muster").split("\\s+"))
      .containsExactly("Marie", "Muster");
    assertThat(LdapService.sanitizeFilterValue("  Marie   Muster  ").trim()
      .split("\\s+")).containsExactly("Marie", "Muster");
  }

}
