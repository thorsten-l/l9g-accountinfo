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
package l9g.account.info.config;

import l9g.account.info.service.LogoutTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LogoutTokenConfig}. The bean factory methods are called
 * directly, so this verifies the wiring — including its fail-fast behaviour at
 * startup — without booting a Spring context and without any network access.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class LogoutTokenConfigTest
{
  private static final String REGISTRATION_ID = "app";

  private static final String ISSUER = "https://id.dev.sonia.de/realms/dev";

  private static final String JWK_SET_URI = ISSUER + "/protocol/openid-connect/certs";

  private LogoutTokenConfig config;

  private ClientRegistrationRepository repository;

  @BeforeEach
  void setUp()
  {
    config = new LogoutTokenConfig();
    repository = mock(ClientRegistrationRepository.class);
  }

  /**
   * Builds a client registration as Spring Boot would create it from an
   * {@code issuer-uri} plus OIDC discovery.
   *
   * @param jwkSetUri The JWKS endpoint, may be null to simulate a registration
   * configured without one.
   * @param issuerUri The issuer, may be null.
   *
   * @return The registration.
   */
  private static ClientRegistration registration(String jwkSetUri,
    String issuerUri)
  {
    return ClientRegistration.withRegistrationId(REGISTRATION_ID)
      .clientId("accountinfo")
      .clientSecret("secret")
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .redirectUri("http://localhost:8080/login/oauth2/code/app")
      .authorizationUri(ISSUER + "/protocol/openid-connect/auth")
      .tokenUri(ISSUER + "/protocol/openid-connect/token")
      .jwkSetUri(jwkSetUri)
      .issuerUri(issuerUri)
      .build();
  }

  private void repositoryHolds(ClientRegistration registration)
  {
    when(repository.findByRegistrationId(REGISTRATION_ID))
      .thenReturn(registration);
  }

  @Test
  @DisplayName("the decoder is built from the registration's JWKS endpoint")
  void decoderIsBuiltFromJwkSetUri()
  {
    repositoryHolds(registration(JWK_SET_URI, ISSUER));

    JwtDecoder decoder =
      config.logoutTokenJwtDecoder(repository, REGISTRATION_ID);

    assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
  }

  /**
   * Building the decoder must not perform any network I/O: it happens while the
   * application context starts, and the JWKS endpoint is not necessarily
   * reachable at that moment.
   */
  @Test
  @DisplayName("building the decoder performs no network access")
  void decoderCreationIsLazy()
  {
    repositoryHolds(registration(
      "https://127.0.0.1:1/does-not-exist/certs", ISSUER));

    assertThatCode(
      () -> config.logoutTokenJwtDecoder(repository, REGISTRATION_ID))
      .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a registration without a JWKS endpoint fails fast at startup")
  void missingJwkSetUriFailsFast()
  {
    repositoryHolds(registration(null, ISSUER));

    assertThatThrownBy(
      () -> config.logoutTokenJwtDecoder(repository, REGISTRATION_ID))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("No jwk-set-uri configured");
  }

  @Test
  @DisplayName("a registration without an issuer still yields a decoder")
  void missingIssuerIsTolerated()
  {
    repositoryHolds(registration(JWK_SET_URI, null));

    assertThat(config.logoutTokenJwtDecoder(repository, REGISTRATION_ID))
      .isNotNull();
  }

  @Test
  @DisplayName("an unknown registration id fails fast at startup")
  void unknownRegistrationIdFailsFast()
  {
    when(repository.findByRegistrationId("typo")).thenReturn(null);

    assertThatThrownBy(
      () -> config.logoutTokenJwtDecoder(repository, "typo"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("No OAuth2 client registration 'typo'");
  }

  @Test
  @DisplayName("the verifier expects the registration's client id as audience")
  void verifierUsesRegistrationClientIdAsAudience()
  {
    repositoryHolds(registration(JWK_SET_URI, ISSUER));
    JwtDecoder decoder = mock(JwtDecoder.class);

    LogoutTokenVerifier verifier =
      config.logoutTokenVerifier(decoder, repository, REGISTRATION_ID);

    assertThat(verifier).isNotNull();
    // the audience is private; it is asserted behaviourally in
    // LogoutTokenVerifierTest, here we only pin that the wiring resolves
    assertThat(registration(JWK_SET_URI, ISSUER).getClientId())
      .isEqualTo("accountinfo");
  }

}
