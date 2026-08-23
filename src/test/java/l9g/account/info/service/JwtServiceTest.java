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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 * <p>
 * The service is a decoder for display purposes only — it deliberately performs
 * no verification. The previous signature-validation code was removed because it
 * was never wired up (neither the JWKS nor the client secret was ever set) and
 * selected its key without regard for the token's {@code kid}. Verification is
 * done by a {@code JwtDecoder}; see {@code LogoutTokenConfig}.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class JwtServiceTest
{
  private JwtService jwtService;

  @BeforeEach
  void setUp()
  {
    jwtService = new JwtService();
  }

  private static String b64url(String text)
  {
    return Base64.getUrlEncoder().withoutPadding()
      .encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }

  private static String tokenWithPayload(String payloadJson)
  {
    return "header." + b64url(payloadJson) + ".signature";
  }

  // --------------------------------------------------------------- splitJwt

  @Test
  @DisplayName("splitJwt accepts a three-part token and rejects other shapes")
  void splitJwtShapes()
  {
    assertThat(jwtService.splitJwt("aa.bb.cc"))
      .containsExactly("aa", "bb", "cc");

    assertThatThrownBy(() -> jwtService.splitJwt("aa.bb"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> jwtService.splitJwt("aa.bb.cc.dd"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * Documents the exact boundary of the shape check: {@code String.split}
   * discards TRAILING empty fields, so a token ending in a dot has only two
   * parts and is correctly rejected — but an empty field in the MIDDLE is
   * preserved, so a token with an empty payload passes the shape check and is
   * only caught later, by the base64/JSON decoding.
   */
  @Test
  @DisplayName("splitJwt rejects a trailing dot but accepts an empty middle part")
  void splitJwtEmptyPartBoundary()
  {
    assertThatThrownBy(() -> jwtService.splitJwt("aa.bb."))
      .isInstanceOf(IllegalArgumentException.class);

    assertThat(jwtService.splitJwt("aa..cc")).containsExactly("aa", "", "cc");
  }

  // ------------------------------------------------------- decodeJwtPayload

  @Test
  @DisplayName("decodeJwtPayload base64url-decodes the payload into a sorted map")
  void decodeJwtPayloadSortsKeys()
  {
    Map<String, Object> claims = jwtService.decodeJwtPayload(tokenWithPayload(
      "{\"sub\":\"user1\",\"aud\":\"app\",\"iss\":\"https://idp\"}"));

    assertThat(claims.keySet()).containsExactly("aud", "iss", "sub");
    assertThat(claims).containsEntry("sub", "user1");
  }

  @Test
  @DisplayName("decodeJwtPayload wraps every failure in a RuntimeException")
  void decodeJwtPayloadWrapsFailures()
  {
    assertThatThrownBy(() -> jwtService.decodeJwtPayload("only.two"))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("Fehler beim Decodieren");

    assertThatThrownBy(
      () -> jwtService.decodeJwtPayload("h.!!!not-base64!!!.s"))
      .isInstanceOf(RuntimeException.class);

    assertThatThrownBy(
      () -> jwtService.decodeJwtPayload(tokenWithPayload("not json")))
      .isInstanceOf(RuntimeException.class);
  }

  /**
   * The map is now declared {@code Map<String, Object>}, so numeric and boolean
   * claims keep their JSON type instead of being smuggled into a
   * {@code Map<String, String>} where every read threw a
   * {@link ClassCastException} at the call site.
   */
  @Test
  @DisplayName("claims keep their JSON type instead of pretending to be strings")
  void claimsKeepTheirJsonType()
  {
    Map<String, Object> claims = jwtService.decodeJwtPayload(tokenWithPayload(
      "{\"sub\":\"user1\",\"exp\":1700000000,\"email_verified\":true,"
      + "\"roles\":[\"admin\",\"user\"]}"));

    assertThat(claims.get("sub")).isInstanceOf(String.class);
    assertThat(claims.get("exp")).isInstanceOf(Integer.class);
    assertThat(claims.get("email_verified")).isEqualTo(Boolean.TRUE);
    assertThat(claims.get("roles")).isInstanceOf(java.util.List.class);
  }

  /**
   * The payload is now decoded as UTF-8 explicitly. Previously the platform
   * default charset was used, which made non-ASCII claim values depend on the
   * environment the application happened to run in.
   */
  @Test
  @DisplayName("non-ASCII claim values are decoded as UTF-8")
  void nonAsciiClaimsAreDecodedAsUtf8()
  {
    Map<String, Object> claims = jwtService.decodeJwtPayload(tokenWithPayload(
      "{\"name\":\"Jörg Müller\",\"city\":\"Wolfenbüttel\","
      + "\"emoji\":\"\\u2713\"}"));

    assertThat(claims).containsEntry("name", "Jörg Müller");
    assertThat(claims).containsEntry("city", "Wolfenbüttel");
    assertThat(claims).containsEntry("emoji", "\u2713");
  }

  @Test
  @DisplayName("an unpadded base64url payload is accepted")
  void unpaddedPayloadIsAccepted()
  {
    String payload = "{\"a\":\"b\"}";
    String unpadded = Base64.getUrlEncoder().withoutPadding()
      .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

    assertThat(jwtService.decodeJwtPayload("h." + unpadded + ".s"))
      .containsEntry("a", "b");
  }

  @Test
  @DisplayName("an empty JSON payload yields an empty map")
  void emptyPayloadYieldsEmptyMap()
  {
    assertThat(jwtService.decodeJwtPayload(tokenWithPayload("{}"))).isEmpty();
  }

}
