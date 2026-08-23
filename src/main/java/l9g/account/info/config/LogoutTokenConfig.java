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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Wires up the verification of OpenID Connect backchannel logout tokens.
 * <p>
 * Both beans are derived from the existing OAuth2 client registration, so the
 * identity provider's JWKS endpoint and this application's client id are not
 * configured a second time. The decoder is built lazily from the JWKS URI: no
 * network access happens while the context starts, only on the first logout
 * token that arrives. Key rotation is handled by
 * {@link NimbusJwtDecoder}, which refreshes the key set and selects the key by
 * the token's {@code kid}.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Configuration
@Slf4j
public class LogoutTokenConfig
{
  /**
   * Bean name of the decoder used for logout tokens.
   */
  public static final String LOGOUT_TOKEN_DECODER = "logoutTokenJwtDecoder";

  /**
   * Creates the decoder that verifies a logout token's signature against the
   * identity provider's JWKS and validates its {@code iss}, {@code exp} and
   * {@code nbf} claims.
   *
   * @param clientRegistrationRepository The repository holding the configured
   * OAuth2 client registrations.
   * @param registrationId The registration to read the provider details from.
   *
   * @return A lazily initialising {@link JwtDecoder}.
   */
  @Bean(LOGOUT_TOKEN_DECODER)
  public JwtDecoder logoutTokenJwtDecoder(
    ClientRegistrationRepository clientRegistrationRepository,
    @Value("${app.oauth2.registration-id:app}") String registrationId)
  {
    ClientRegistration registration =
      findRegistration(clientRegistrationRepository, registrationId);

    ClientRegistration.ProviderDetails provider =
      registration.getProviderDetails();
    String jwkSetUri = provider.getJwkSetUri();

    if(jwkSetUri == null || jwkSetUri.isBlank())
    {
      throw new IllegalStateException("No jwk-set-uri configured for OAuth2 "
        + "registration '" + registrationId + "'; backchannel logout tokens "
        + "cannot be verified without it.");
    }

    log.debug("logout token decoder uses jwkSetUri={}", jwkSetUri);
    NimbusJwtDecoder decoder =
      NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

    String issuerUri = provider.getIssuerUri();
    if(issuerUri != null &&  ! issuerUri.isBlank())
    {
      decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
        new JwtTimestampValidator(), new JwtIssuerValidator(issuerUri)));
    }
    else
    {
      log.warn("No issuer-uri configured for OAuth2 registration '{}'; "
        + "logout tokens will not be checked against an expected issuer.",
        registrationId);
      decoder.setJwtValidator(new JwtTimestampValidator());
    }

    return decoder;
  }

  /**
   * Creates the verifier enforcing the backchannel-logout specific claims.
   *
   * @param jwtDecoder The decoder created by
   * {@link #logoutTokenJwtDecoder(ClientRegistrationRepository, String)}.
   * @param clientRegistrationRepository The repository holding the configured
   * OAuth2 client registrations.
   * @param registrationId The registration to read the client id from.
   *
   * @return The configured {@link LogoutTokenVerifier}.
   */
  @Bean
  public LogoutTokenVerifier logoutTokenVerifier(
    @Qualifier(LOGOUT_TOKEN_DECODER) JwtDecoder jwtDecoder,
    ClientRegistrationRepository clientRegistrationRepository,
    @Value("${app.oauth2.registration-id:app}") String registrationId)
  {
    String clientId =
      findRegistration(clientRegistrationRepository, registrationId)
        .getClientId();
    log.debug("logout token verifier expects audience={}", clientId);
    return new LogoutTokenVerifier(jwtDecoder, clientId);
  }

  /**
   * Looks up a client registration and fails fast if it is missing, since
   * without it no logout token could ever be verified.
   *
   * @param clientRegistrationRepository The repository to query.
   * @param registrationId The registration id to look for.
   *
   * @return The registration, never {@code null}.
   */
  private ClientRegistration findRegistration(
    ClientRegistrationRepository clientRegistrationRepository,
    String registrationId)
  {
    ClientRegistration registration =
      clientRegistrationRepository.findByRegistrationId(registrationId);

    if(registration == null)
    {
      throw new IllegalStateException("No OAuth2 client registration '"
        + registrationId + "' found; check app.oauth2.registration-id.");
    }

    return registration;
  }

}
