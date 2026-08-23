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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LogoutTokenVerifier}.
 * <p>
 * These cover the fix for the unauthenticated session-termination defect: the
 * backchannel logout endpoint is {@code permitAll} and CSRF-exempt, so the
 * logout token is the only credential and every check below is load-bearing.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class LogoutTokenVerifierTest
{
  private static final String CLIENT_ID = "accountinfo";

  private static final String TOKEN = "the.logout.token";

  private JwtDecoder jwtDecoder;

  private LogoutTokenVerifier verifier;

  @BeforeEach
  void setUp()
  {
    jwtDecoder = mock(JwtDecoder.class);
    verifier = new LogoutTokenVerifier(jwtDecoder, CLIENT_ID);
  }

  /**
   * Builds a decoded token as the identity provider would issue it.
   *
   * @return A builder pre-filled with a valid logout token's claims.
   */
  private static Jwt.Builder validLogoutToken()
  {
    return Jwt.withTokenValue(TOKEN)
      .header("alg", "RS256")
      .header("kid", "idp-key-1")
      .issuer("https://id.dev.sonia.de/realms/dev")
      .audience(List.of(CLIENT_ID))
      .issuedAt(Instant.now().minusSeconds(5))
      .expiresAt(Instant.now().plusSeconds(60))
      .claim("jti", "jti-1")
      .claim("sid", "session-1")
      .claim("events",
        Map.of(LogoutTokenVerifier.BACKCHANNEL_LOGOUT_EVENT, Map.of()));
  }

  private void decoderReturns(Jwt jwt)
  {
    when(jwtDecoder.decode(any())).thenReturn(jwt);
  }

  private static void assertBadRequest(Throwable thrown, String reason)
  {
    assertThat(thrown).isInstanceOf(ResponseStatusException.class);
    ResponseStatusException e = (ResponseStatusException)thrown;
    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getReason()).contains(reason);
  }

  // ----------------------------------------------------------- happy path

  @Test
  @DisplayName("a valid logout token yields its sid")
  void validTokenYieldsSid()
  {
    decoderReturns(validLogoutToken().build());

    assertThat(verifier.verifyAndExtractSid(TOKEN)).isEqualTo("session-1");
  }

  @Test
  @DisplayName("surrounding whitespace is tolerated")
  void surroundingWhitespaceIsTolerated()
  {
    decoderReturns(validLogoutToken().build());

    assertThat(verifier.verifyAndExtractSid("  " + TOKEN + "\n"))
      .isEqualTo("session-1");
  }

  @Test
  @DisplayName("an audience list containing further clients is accepted")
  void additionalAudiencesAreAccepted()
  {
    decoderReturns(validLogoutToken()
      .audience(List.of("other-client", CLIENT_ID)).build());

    assertThat(verifier.verifyAndExtractSid(TOKEN)).isEqualTo("session-1");
  }

  // ------------------------------------------------------------ signature

  /**
   * The core of the fix: a token whose signature cannot be verified must never
   * reach the session store. Before the fix an attacker-built token with a
   * garbage signature terminated the referenced session and was answered with
   * {@code 200 OK}.
   */
  @Test
  @DisplayName("a forged token is rejected with 400")
  void forgedTokenIsRejected()
  {
    when(jwtDecoder.decode(any()))
      .thenThrow(new BadJwtException("Signed JWT rejected: Invalid signature"));

    assertThatThrownBy(() -> verifier.verifyAndExtractSid(TOKEN))
      .satisfies(t -> assertBadRequest(t, "Invalid logout token"));
  }

  @Test
  @DisplayName("an expired or wrongly issued token is rejected with 400")
  void invalidClaimsFromTheDecoderAreRejected()
  {
    when(jwtDecoder.decode(any())).thenThrow(new JwtValidationException(
      "An error occurred while attempting to decode the Jwt",
      List.of(new org.springframework.security.oauth2.core.OAuth2Error(
        "invalid_token", "Jwt expired", null))));

    assertThatThrownBy(() -> verifier.verifyAndExtractSid(TOKEN))
      .satisfies(t -> assertBadRequest(t, "Invalid logout token"));
  }

  // ------------------------------------------------------------- claims

  @Test
  @DisplayName("a token issued for another client is rejected")
  void foreignAudienceIsRejected()
  {
    decoderReturns(validLogoutToken()
      .audience(List.of("some-other-client")).build());

    assertThatThrownBy(() -> verifier.verifyAndExtractSid(TOKEN))
      .satisfies(t -> assertBadRequest(t, "not issued for this client"));
  }

  /**
   * Without the {@code events} check any token the identity provider issued for
   * this client — an ID token in particular — would work as a logout
   * instruction.
   */
  @Test
  @DisplayName("a token without the backchannel-logout event is rejected")
  void missingLogoutEventIsRejected()
  {
    decoderReturns(validLogoutToken().claim("events", Map.of()).build());

    assertThatThrownBy(() -> verifier.verifyAndExtractSid(TOKEN))
      .satisfies(t -> assertBadRequest(t, "backchannel-logout event"));
  }

  @Test
  @DisplayName("a token with a foreign event identifier is rejected")
  void foreignEventIdentifierIsRejected()
  {
    decoderReturns(validLogoutToken()
      .claim("events", Map.of("http://example.com/event/something", Map.of()))
      .build());

    assertThatThrownBy(() -> verifier.verifyAndExtractSid(TOKEN))
      .satisfies(t -> assertBadRequest(t, "backchannel-logout event"));
  }

  @Test
  @DisplayName("a non-object events claim is rejected")
  void nonObjectEventsClaimIsRejected()
  {
    decoderReturns(validLogoutToken()
      .claim("events", LogoutTokenVerifier.BACKCHANNEL_LOGOUT_EVENT).build());

    assertThatThrownBy(() -> verifier.verifyAndExtractSid(TOKEN))
      .satisfies(t -> assertBadRequest(t, "backchannel-logout event"));
  }

  /**
   * A logout token must not carry a {@code nonce}; its presence indicates an ID
   * token being replayed as a logout instruction.
   */
  @Test
  @DisplayName("an ID token replayed as a logout token is rejected via its nonce")
  void nonceClaimIsRejected()
  {
    decoderReturns(validLogoutToken().claim("nonce", "n-0S6_WzA2Mj").build());

    assertThatThrownBy(() -> verifier.verifyAndExtractSid(TOKEN))
      .satisfies(t -> assertBadRequest(t, "nonce"));
  }

  @Test
  @DisplayName("a token without a usable sid is rejected")
  void missingSidIsRejected()
  {
    decoderReturns(validLogoutToken().claim("sid", null).build());
    assertThatThrownBy(() -> verifier.verifyAndExtractSid(TOKEN))
      .satisfies(t -> assertBadRequest(t, "sid"));

    decoderReturns(validLogoutToken().claim("sid", "   ").build());
    assertThatThrownBy(() -> verifier.verifyAndExtractSid(TOKEN))
      .satisfies(t -> assertBadRequest(t, "sid"));
  }

  // -------------------------------------------------------------- input

  @Test
  @DisplayName("a missing or blank token is rejected without consulting the decoder")
  void missingTokenIsRejected()
  {
    assertThatThrownBy(() -> verifier.verifyAndExtractSid(null))
      .satisfies(t -> assertBadRequest(t, "Missing logout token"));
    assertThatThrownBy(() -> verifier.verifyAndExtractSid("   "))
      .satisfies(t -> assertBadRequest(t, "Missing logout token"));

    org.mockito.Mockito.verify(jwtDecoder, org.mockito.Mockito.never())
      .decode(any());
  }

}
