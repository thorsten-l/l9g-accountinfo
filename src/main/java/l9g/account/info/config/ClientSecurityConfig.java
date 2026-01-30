/*
 * Copyright 2022 Thorsten Ludewig (t.ludewig@gmail.com).
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class ClientSecurityConfig
{
  private final AppAuthoritiesConverter appAuthoritiesConverter;

  private final LoginSuccessHandler loginSuccessHandler;

  /**
   * Content Security Policy directives for enhanced security
   */
  private final String CSP_POLICY;

  public ClientSecurityConfig(
    AppAuthoritiesConverter appAuthoritiesConverter,
    LoginSuccessHandler loginSuccessHandler,
    @Value("${app.base-url}") String appBaseUrl
  )
  {
    this.appAuthoritiesConverter = appAuthoritiesConverter;
    this.loginSuccessHandler = loginSuccessHandler;
    this.CSP_POLICY = "default-src 'self'; "
      + "manifest-src 'self'; "
      + "connect-src 'self' https://idp.sonia.de; "
      + "img-src 'self' data:;"
      + "style-src 'self' 'unsafe-inline';"
      + "script-src 'self' 'unsafe-inline';";
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http,
    ClientRegistrationRepository clientRegistrationRepository)
    throws Exception
  {
    log.debug("filterChain clientRegistrationRepository={}",
      clientRegistrationRepository);

    DefaultOAuth2AuthorizationRequestResolver resolver =
      new DefaultOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository,
        OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);

    resolver.setAuthorizationRequestCustomizer(
      OAuth2AuthorizationRequestCustomizers.withPkce());

    http.authorizeHttpRequests(
      authorize -> authorize
        // test deny all
        .requestMatchers("/system/test/error403").denyAll()
        // allow all
        .requestMatchers("/", "/manifest.json",
          "/manifest.webmanifest",
          "/error/**", "/api/v1/buildinfo",
          "/webjars/**", "/icons/**", "/css/**", "/js/**", "/images/**",
          "/actuator/**", "/flags/**", "/logout", "/oidc-backchannel-logout", 
          "/admin/validate-new-pad", "/api/v1/signature-pad/validate", 
          "/ws/signature-pad",
          "/android**",
          "/apple**",
          "/favicon**"
        )
        .permitAll()
        .requestMatchers("/admin**","/api/v1/admin**","/v3/api-docs")
        .hasRole("ADMIN")
        .anyRequest()
        .authenticated()
    )
      .headers(
        headers -> headers
          .frameOptions(frameOptions -> frameOptions.disable())
          .contentSecurityPolicy(csp -> csp.policyDirectives(CSP_POLICY))
      )
      .oauth2Login(
        login -> login
          .authorizationEndpoint(
            authorizationEndpointCustomizer -> authorizationEndpointCustomizer
              .authorizationRequestResolver(resolver))
          .userInfoEndpoint(userInfo -> userInfo
          .oidcUserService(this.oidcUserService())
          )
          .successHandler(loginSuccessHandler))
      .oauth2Client(withDefaults())
      .logout(
        logout -> logout
          .deleteCookies("JSESSIONID") // Session info from embedded Tomcat
          .addLogoutHandler(invalidateCacheLogoutHandler())
          .logoutSuccessHandler(
            oidcLogoutSuccessHandler(clientRegistrationRepository))
      )
      // permit even POST, PUT and DELETE requests
      .csrf(csrf -> csrf.ignoringRequestMatchers(
      "/oidc-backchannel-logout", "/api/v1/signature-pad/**"));

    return http.build();
  }

  private LogoutHandler invalidateCacheLogoutHandler()
  {
    return (HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) ->
    {
      new SecurityContextLogoutHandler().logout(request, response, authentication);
    };
  }

  private LogoutSuccessHandler oidcLogoutSuccessHandler(
    ClientRegistrationRepository clientRegistrationRepository)
  {
    log.debug("oidcLogoutSuccessHandler");
    OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
      new OidcClientInitiatedLogoutSuccessHandler(
        clientRegistrationRepository);
    oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
    return oidcLogoutSuccessHandler;
  }

  private OidcUserService oidcUserService()
  {
    log.debug("oidcUserService");
    OidcUserService delegate = new OidcUserService();

    return new OidcUserService()
    {
      @Override
      public OidcUser loadUser(OidcUserRequest userRequest)
      {
        OidcUser oidcUser = delegate.loadUser(userRequest);

        Jwt accessToken = decodeAccessToken(
          userRequest.getAccessToken().getTokenValue(),
          userRequest.getClientRegistration().getProviderDetails().getIssuerUri()
        );

        Collection<GrantedAuthority> authorities =
          appAuthoritiesConverter.convert(oidcUser, accessToken);

        if(log.isDebugEnabled())
        {
          authorities.stream()
            .map(GrantedAuthority :: getAuthority)
            .forEach(System.out :: println);
        }

        return new DefaultOidcUser(
          authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
      }

    };
  }

  private Jwt decodeAccessToken(String token, String issuerUri)
  {
    JwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);
    return jwtDecoder.decode(token);
  }

}
