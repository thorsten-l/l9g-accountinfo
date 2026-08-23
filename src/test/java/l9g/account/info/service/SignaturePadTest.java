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

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SignaturePad}, focused on the key-material invariant:
 * the JWK stored in {@code publicJwk} (and thus persisted to the database) must
 * never contain private RSA components.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class SignaturePadTest
{
  /**
   * The private RSA JWK members that must never appear in {@code publicJwk}.
   */
  private static final String[] PRIVATE_MEMBERS =
  {
    "d", "p", "q", "dp", "dq", "qi"
  };

  @Test
  @DisplayName("a fresh pad has a random UUID, version 0 and no key material")
  void freshPadHasNoKeyMaterial()
  {
    SignaturePad pad = new SignaturePad("Empfang");

    assertThat(pad.getName()).isEqualTo("Empfang");
    assertThat(pad.getUuid()).isNotBlank();
    assertThat(pad.getVersion()).isZero();
    assertThat(pad.getKeyId()).isEqualTo(pad.getUuid() + "-0");
    assertThat(pad.getPublicJwk()).isNull();
    assertThat(pad.isValidated()).isFalse();
  }

  @Test
  @DisplayName("two pads never share a UUID")
  void padsHaveDistinctUuids()
  {
    assertThat(new SignaturePad("a").getUuid())
      .isNotEqualTo(new SignaturePad("b").getUuid());
  }

  @Test
  @DisplayName("createPrivateJWK increments the version and keeps keyId in sync")
  void createPrivateJwkIncrementsVersion()
    throws Exception
  {
    SignaturePad pad = new SignaturePad("Empfang");

    pad.createPrivateJWK();
    assertThat(pad.getVersion()).isEqualTo(1);
    assertThat(pad.getKeyId()).isEqualTo(pad.getUuid() + "-1");
    assertThat(pad.getPublicJwk()).containsEntry("kid", pad.getKeyId());

    pad.createPrivateJWK();
    assertThat(pad.getVersion()).isEqualTo(2);
    assertThat(pad.getKeyId()).isEqualTo(pad.getUuid() + "-2");
    assertThat(pad.getPublicJwk()).containsEntry("kid", pad.getKeyId());
  }

  @Test
  @DisplayName("the generated key is an RS256 signing key with a 2048 bit modulus")
  void generatedKeyHasExpectedParameters()
    throws Exception
  {
    SignaturePad pad = new SignaturePad("Empfang");

    pad.createPrivateJWK();
    Map<String, Object> publicJwk = pad.getPublicJwk();

    assertThat(publicJwk)
      .containsEntry("kty", "RSA")
      .containsEntry("use", "sig")
      .containsEntry("alg", "RS256");

    RSAKey parsed = (RSAKey)JWK.parse(publicJwk);
    assertThat(parsed.toRSAPublicKey().getModulus().bitLength())
      .isEqualTo(2048);
  }

  /**
   * The central security invariant of this class: {@code createPrivateJWK}
   * returns the FULL key (including the private components) to the caller — who
   * hands it to the signature pad device — while {@code publicJwk}, the field
   * that {@code DbService.saveSignaturePad} persists, must be stripped of every
   * private component. A regression here would write RSA private keys into the
   * database.
   */
  @Test
  @DisplayName("publicJwk carries no private key material while the returned JWK does")
  void publicJwkNeverContainsPrivateMaterial()
    throws Exception
  {
    SignaturePad pad = new SignaturePad("Empfang");

    String fullJwk = pad.createPrivateJWK();

    assertThat(JWK.parse(fullJwk).isPrivate())
      .as("the JWK handed to the device must contain the private key")
      .isTrue();

    Map<String, Object> publicJwk = pad.getPublicJwk();
    assertThat(publicJwk.keySet())
      .as("stored JWK must not contain private RSA members")
      .doesNotContain(PRIVATE_MEMBERS);
    assertThat(JWK.parse(publicJwk).isPrivate())
      .as("the stored JWK must be a public-only key")
      .isFalse();
  }

  @Test
  @DisplayName("each rotation produces a different key")
  void rotationProducesNewKeyMaterial()
    throws Exception
  {
    SignaturePad pad = new SignaturePad("Empfang");

    pad.createPrivateJWK();
    Object firstModulus = pad.getPublicJwk().get("n");

    pad.createPrivateJWK();

    assertThat(pad.getPublicJwk().get("n")).isNotEqualTo(firstModulus);
  }

}
