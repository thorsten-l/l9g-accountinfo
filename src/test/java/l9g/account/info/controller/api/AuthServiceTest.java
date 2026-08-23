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
package l9g.account.info.controller.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import l9g.account.info.service.SignaturePad;
import l9g.account.info.service.SignaturePadService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}.
 * <p>
 * This test class deliberately lives in {@code l9g.account.info.controller.api}
 * because both methods under test are package-private.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class AuthServiceTest
{
  private static final String PAD_UUID = "b7d2b1f0-1111-4222-8333-444455556666";

  private static RSAKey padKey;

  private static RSAKey otherKey;

  private SignaturePadService signaturePadService;

  private AuthService authService;

  @BeforeAll
  static void generateKeys()
    throws Exception
  {
    padKey = generateRsaKey("pad-key");
    otherKey = generateRsaKey("other-key");
  }

  private static RSAKey generateRsaKey(String kid)
    throws Exception
  {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    return new RSAKey.Builder((RSAPublicKey)pair.getPublic())
      .privateKey((RSAPrivateKey)pair.getPrivate())
      .keyID(kid)
      .build();
  }

  private static String sign(RSAKey key, JWTClaimsSet claims)
    throws Exception
  {
    SignedJWT jwt = new SignedJWT(
      new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
      claims);
    jwt.sign(new RSASSASigner(key.toRSAPrivateKey()));
    return jwt.serialize();
  }

  private static SignaturePad padWithKey(RSAKey key)
  {
    SignaturePad pad = new SignaturePad("Test-Pad");
    pad.setValidated(true);
    pad.setPublicJwk(key.toPublicJWK().toJSONObject());
    return pad;
  }

  @BeforeEach
  void setUp()
  {
    signaturePadService = mock(SignaturePadService.class);
    authService = new AuthService(signaturePadService);
  }

  // -------------------------------------------------------------- authCheck

  @Test
  @DisplayName("a validated pad is returned unchanged")
  void validatedPadIsReturned()
    throws Exception
  {
    SignaturePad pad = new SignaturePad("Test-Pad");
    pad.setValidated(true);
    when(signaturePadService.findSignaturePadByUUID(PAD_UUID)).thenReturn(pad);

    assertThat(authService.authCheck(PAD_UUID, true)).isSameAs(pad);
  }

  @Test
  @DisplayName("an unknown pad UUID yields 404")
  void unknownPadIsNotFound()
    throws Exception
  {
    when(signaturePadService.findSignaturePadByUUID(PAD_UUID)).thenReturn(null);

    assertThatThrownBy(() -> authService.authCheck(PAD_UUID, true))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("Signature pad UUID not found!")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("an unvalidated pad yields 404 only when validity is checked")
  void unvalidatedPadDependsOnCheckValidity()
    throws Exception
  {
    SignaturePad pad = new SignaturePad("Test-Pad");
    when(signaturePadService.findSignaturePadByUUID(PAD_UUID)).thenReturn(pad);

    assertThatThrownBy(() -> authService.authCheck(PAD_UUID, true))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("Signature pad UUID not valid!");

    assertThat(authService.authCheck(PAD_UUID, false)).isSameAs(pad);
  }

  /**
   * An infrastructure failure is now reported as {@code 500}, not {@code 404}.
   * Mapping it to 404 made a database outage indistinguishable from a wrong pad
   * UUID — for clients and for log-based monitoring alike.
   */
  @Test
  @DisplayName("a backend failure is reported as 500, not masked as 404")
  void backendFailureIsInternalServerError()
    throws Exception
  {
    when(signaturePadService.findSignaturePadByUUID(PAD_UUID))
      .thenThrow(new IllegalStateException("database unreachable"));

    assertThatThrownBy(() -> authService.authCheck(PAD_UUID, true))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("Unable to read signature pad storage.")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("a declared IOException from the storage layer is also a 500")
  void ioExceptionIsInternalServerError()
    throws Exception
  {
    when(signaturePadService.findSignaturePadByUUID(PAD_UUID))
      .thenThrow(new java.io.IOException("corrupt record"));

    assertThatThrownBy(() -> authService.authCheck(PAD_UUID, true))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Compatibility guard: the pad JavaScript reacts to {@code 404} specifically
   * ({@code websocket.js:108}). The two genuine "not found" cases must
   * therefore keep returning 404 — only infrastructure failures moved to 500.
   */
  @Test
  @DisplayName("the genuine not-found cases still return 404 for the pads")
  void genuineNotFoundCasesStayAt404()
    throws Exception
  {
    when(signaturePadService.findSignaturePadByUUID(PAD_UUID)).thenReturn(null);
    assertThatThrownBy(() -> authService.authCheck(PAD_UUID, true))
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.NOT_FOUND);

    SignaturePad unvalidated = new SignaturePad("Test-Pad");
    when(signaturePadService.findSignaturePadByUUID(PAD_UUID))
      .thenReturn(unvalidated);
    assertThatThrownBy(() -> authService.authCheck(PAD_UUID, true))
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.NOT_FOUND);
  }

  /**
   * An {@link Error} must propagate instead of being turned into an HTTP
   * status; the previous {@code catch(Throwable)} hid even an
   * {@code OutOfMemoryError}.
   */
  @Test
  @DisplayName("an Error propagates instead of being mapped to a status code")
  void errorIsNotSwallowed()
    throws Exception
  {
    when(signaturePadService.findSignaturePadByUUID(PAD_UUID))
      .thenThrow(new StackOverflowError());

    assertThatThrownBy(() -> authService.authCheck(PAD_UUID, true))
      .isInstanceOf(StackOverflowError.class);
  }

  // -------------------------------------------------------------- verifyJwt

  @Test
  @DisplayName("a JWT signed with the pad's key verifies")
  void correctlySignedJwtVerifies()
    throws Exception
  {
    SignaturePad pad = padWithKey(padKey);
    String jwt = sign(padKey,
      new JWTClaimsSet.Builder().subject("user1").build());

    SignedJWT verified = authService.verifyJwt(pad, jwt);

    assertThat(verified.getJWTClaimsSet().getSubject()).isEqualTo("user1");
  }

  @Test
  @DisplayName("a JWT signed with a foreign key is rejected with 400")
  void foreignKeyIsRejected()
    throws Exception
  {
    SignaturePad pad = padWithKey(padKey);
    String jwt = sign(otherKey,
      new JWTClaimsSet.Builder().subject("attacker").build());

    assertThatThrownBy(() -> authService.verifyJwt(pad, jwt))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("JWT signature verification failed!")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("a tampered payload is rejected with 400")
  void tamperedPayloadIsRejected()
    throws Exception
  {
    SignaturePad pad = padWithKey(padKey);
    String[] parts = sign(padKey,
      new JWTClaimsSet.Builder().subject("user1").build()).split("\\.");
    String tampered = parts[0] + "."
      + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
        "{\"sub\":\"attacker\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
      + "." + parts[2];

    assertThatThrownBy(() -> authService.verifyJwt(pad, tampered))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("an unparseable token is rejected with 400")
  void unparseableTokenIsRejected()
  {
    SignaturePad pad = padWithKey(padKey);

    assertThatThrownBy(() -> authService.verifyJwt(pad, "not-a-jwt"))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("could not be parsed")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /**
   * Intended behaviour, pinned deliberately: {@code verifyJwt} verifies the RSA
   * signature and nothing else — no {@code exp}, {@code nbf}, {@code iss} or
   * {@code aud} is evaluated.
   * <p>
   * That is by design. Signature pads deliberately issue tokens <em>without</em>
   * an {@code exp} claim because a captured signature must never expire: the
   * same method re-verifies archived signatures read back from the database
   * ({@code ApiAdminController} lines 168, 214 and 287), sometimes years later.
   * Any expiry or maximum-age check here would make the audit trail
   * unverifiable, which is the opposite of what it exists for.
   * <p>
   * The token used below carries an {@code exp} in the past only to show that
   * even then no claim is looked at. Real pad tokens carry {@code iat} alone —
   * see {@link #existingRsaPadSignatureStillVerifies()}.
   */
  @Test
  @DisplayName("claims are deliberately not evaluated, so an archived signature never expires")
  void expiredJwtIsAccepted()
    throws Exception
  {
    SignaturePad pad = padWithKey(padKey);
    String expired = sign(padKey, new JWTClaimsSet.Builder()
      .subject("user1")
      .issueTime(new Date(0L))
      .expirationTime(new Date(1000L))
      .build());

    SignedJWT verified = authService.verifyJwt(pad, expired);

    assertThat(verified.getJWTClaimsSet().getExpirationTime())
      .isBefore(new Date());
  }

  @Test
  @DisplayName("a pad without a stored public JWK is rejected with 400, not an NPE")
  void padWithoutPublicJwkIsRejected()
  {
    SignaturePad pad = new SignaturePad("Test-Pad");
    pad.setValidated(true);

    assertThatThrownBy(() -> authService.verifyJwt(pad, "a.b.c"))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("has no public key")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("an empty public JWK is rejected with 400")
  void padWithEmptyPublicJwkIsRejected()
  {
    SignaturePad pad = new SignaturePad("Test-Pad");
    pad.setValidated(true);
    pad.setPublicJwk(java.util.Map.of());

    assertThatThrownBy(() -> authService.verifyJwt(pad, "a.b.c"))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("has no public key")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /**
   * A non-RSA key previously produced an uncaught {@link ClassCastException},
   * i.e. a 500 from the global handler instead of a mapped client error.
   */
  @Test
  @DisplayName("a non-RSA public JWK is rejected with 400, not a ClassCastException")
  void padWithNonRsaPublicJwkIsRejected()
    throws Exception
  {
    java.security.KeyPairGenerator generator =
      java.security.KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    java.security.KeyPair ecPair = generator.generateKeyPair();
    com.nimbusds.jose.jwk.ECKey ecKey = new com.nimbusds.jose.jwk.ECKey.Builder(
      com.nimbusds.jose.jwk.Curve.P_256,
      (java.security.interfaces.ECPublicKey)ecPair.getPublic()).build();

    SignaturePad pad = new SignaturePad("Test-Pad");
    pad.setValidated(true);
    pad.setPublicJwk(ecKey.toPublicJWK().toJSONObject());

    assertThatThrownBy(() -> authService.verifyJwt(pad, "a.b.c"))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("not an RSA key")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /**
   * Guards the compatibility requirement that matters most in production: the
   * signatures of the signature pads already in the field must keep verifying
   * exactly as before. The hardening above must only turn crashes into mapped
   * errors, never reject a token that used to be accepted.
   */
  @Test
  @DisplayName("an existing RSA pad signature still verifies unchanged")
  void existingRsaPadSignatureStillVerifies()
    throws Exception
  {
    SignaturePad pad = padWithKey(padKey);
    // a pad JWT as signaturePad.js builds it: iat only, no exp, no iss, no aud
    String jwt = sign(padKey, new JWTClaimsSet.Builder()
      .subject("mmuster")
      .claim("name", "Marie Muster")
      .claim("iat", 1700000000L)
      .build());

    SignedJWT verified = authService.verifyJwt(pad, jwt);

    assertThat(verified.getJWTClaimsSet().getSubject()).isEqualTo("mmuster");
    assertThat(verified.getJWTClaimsSet().getClaim("name"))
      .isEqualTo("Marie Muster");
    assertThat(verified.getJWTClaimsSet().getExpirationTime())
      .as("pad tokens carry no exp and must not be required to")
      .isNull();
  }

}
