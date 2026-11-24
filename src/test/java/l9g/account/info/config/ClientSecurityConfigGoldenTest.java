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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.spec.SecretKeySpec;
import l9g.account.info.vault.VaultService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Golden test for the HTTP security behaviour of {@link ClientSecurityConfig}.
 * <p>
 * Runs the real {@code SecurityFilterChain} against a matrix of request path,
 * HTTP method, CSRF token, user role and vault state, and compares every outcome
 * (status code, redirect target, security headers) with the recorded matrix in
 * {@code src/test/resources/golden/security-matrix.txt}.
 * <p>
 * The matrix was recorded on Spring Boot 3.5 / Spring Security 6.5 before the
 * upgrade to Spring Boot 4 / Spring Security 7. It pins the behaviour so that
 * any framework upgrade that changes who may access what fails here.
 * <p>
 * No Spring Boot context is started: the test builds a plain web application
 * context with only the security configuration and mocked collaborators, so
 * neither {@code data/config.yaml} nor {@code data/secret.bin} is read.
 * A {@code 404} in the matrix means "passed the security filter chain" because
 * no controllers are registered.
 * <p>
 * The actual matrix is always written to {@code target/golden/security-matrix.txt};
 * after an intended behaviour change, review the diff and copy that file over
 * the golden file.
 */
class ClientSecurityConfigGoldenTest
{
  private static final String GOLDEN = "/golden/security-matrix.txt";

  private static final Path ACTUAL = Path.of("target/golden/security-matrix.txt");

  private static final String BASE_URL = "https://accountinfo.example.org";

  private static final String END_SESSION = "https://idp.example.org/logout";

  private static AnnotationConfigWebApplicationContext context;

  private static MockMvc mockMvc;

  private static VaultService vaultService;

  private static ClientRegistration registration;

  enum Vault
  {
    SEALED_KEYS(false, false),
    UNSEALED_KEYS(true, false),
    SEALED_NOKEYS(false, true),
    UNSEALED_NOKEYS(true, true);

    final boolean unsealed;

    final boolean noKeys;

    Vault(boolean unsealed, boolean noKeys)
    {
      this.unsealed = unsealed;
      this.noKeys = noKeys;
    }

  }

  enum User
  {
    ANON(),
    ANON_XHR(),
    ANON_JSON(),
    NOROLE(),
    PUBLISHER("ROLE_PUBLISHER"),
    ADMIN("ROLE_ADMIN"),
    TABADMIN("ROLE_TABADMIN"),
    VAULTADMIN("ROLE_VAULTADMIN"),
    AUDITADMIN("ROLE_AUDITADMIN");

    final String[] roles;

    User(String... roles)
    {
      this.roles = roles;
    }

    boolean authenticated()
    {
      return  ! name().startsWith("ANON");
    }

  }

  private static final String[] GET_PATHS =
  {
    "/", "/manifest.json", "/manifest.webmanifest",
    "/system/test/403", "/system/test/error403", "/system/test/500",
    "/error", "/error/x", "/api/v1/buildinfo",
    "/webjars/jquery/jquery.min.js", "/icons/x.png", "/css/app.css",
    "/js/app.js", "/images/x.png", "/flags/de.svg", "/actuator/health",
    "/android-chrome-192x192.png", "/apple-touch-icon.png", "/favicon.ico",
    "/favicon-16x16.png", "/androidX/sub", "/logout", "/logoutnow",
    "/ws/signature-pad", "/admin/validate-new-pad", "/signature-pad",
    "/admin", "/admin/", "/ADMIN", "/admin/vault/managekeys",
    "/admin/vault/managekeys/", "/admin/vault/enrollment",
    "/admin/vault/unseal", "/admin/useraudit", "/admin/register-new-pad",
    "/admin/manage-pads", "/admin/show-pad-info", "/admin/show-pad",
    "/admin/hide-pad", "/admin/delete-pad", "/admin/wait-for-response",
    "/admin/other", "/api/v1/admin/vault/challenge-data",
    "/api/v1/admin/vault/unlocktimeleft", "/api/v1/admin/vault/adminkey",
    "/api/v1/admin/secret", "/api/v1/admin/secret/",
    "/api/v1/admin/secret/audit", "/api/v1/admin/secret/audit/x",
    "/api/v1/admin/secret/search/person",
    "/api/v1/admin/secret/userinfo.json",
    "/api/v1/admin/secret/signature.png",
    "/api/v1/admin/secret/signature.svg", "/api/v1/admin/secret/id.jpeg",
    "/api/v1/admin/secret/archive.json", "/api/v1/admin/secret/archive/x",
    "/api/v1/admin/secret/export/x", "/api/v1/admin/secret/other",
    "/v3/api-docs", "/v3/api-docs/swagger-config", "/api/v1/admin",
    "/api/v1/admin/x", "/app", "/app/x", "/api/v1/search/x",
    "/api/v1/summary", "/api/v1/userinfo",
    "/api/v1/signature-pad/connect-qrcode", "/oauth2/authorization/app",
    "/login/oauth2/code/app", "/some/other", "/api/v1/storage/objects",
    "/oidc-backchannel-logout"
  };

  private static final String[] POST_PATHS =
  {
    "/logout", "/logout?logoutReason=idle:/x", "/oidc-backchannel-logout",
    "/api/v1/storage/objects", "/api/v1/signature-pad/validate",
    "/api/v1/signature-pad/scan", "/api/v1/signature-pad/signature",
    "/api/v1/signature-pad/photo", "/api/v1/auth-pad",
    "/admin/connect-new-pad", "/api/v1/admin/vault/adminkey",
    "/api/v1/admin/vault/unseal", "/api/v1/admin/secret/x", "/app/x",
    "/some/other"
  };

  private static final String[] DELETE_PATHS =
  {
    "/api/v1/admin/vault/adminkey", "/api/v1/admin/secret/x"
  };

  @Configuration
  @EnableWebMvc
  static class TestConfig
  {
    @Bean
    static PropertySourcesPlaceholderConfigurer placeholderConfigurer()
    {
      PropertySourcesPlaceholderConfigurer configurer =
        new PropertySourcesPlaceholderConfigurer();
      MutablePropertySources sources = new MutablePropertySources();
      sources.addFirst(new MapPropertySource("test",
        Map.of("app.base-url", BASE_URL)));
      configurer.setPropertySources(sources);
      return configurer;
    }

    @Bean
    VaultService vaultService()
    {
      return vaultService;
    }

    @Bean
    AppAuthoritiesConverter appAuthoritiesConverter()
    {
      return mock(AppAuthoritiesConverter.class);
    }

    @Bean
    LoginSuccessHandler loginSuccessHandler()
    {
      return mock(LoginSuccessHandler.class);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository()
    {
      return new InMemoryClientRegistrationRepository(registration);
    }

  }

  @BeforeAll
  static void setUp()
  {
    vaultService = mock(VaultService.class);
    registration = ClientRegistration.withRegistrationId("app")
      .clientId("accountinfo")
      .clientSecret("secret")
      .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
      .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
      .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
      .scope("openid", "profile", "email", "roles")
      .authorizationUri("https://idp.example.org/auth")
      .tokenUri("https://idp.example.org/token")
      .jwkSetUri("https://idp.example.org/certs")
      .userInfoUri("https://idp.example.org/userinfo")
      .issuerUri("https://idp.example.org")
      .userNameAttributeName("preferred_username")
      .providerConfigurationMetadata(
        Map.of("end_session_endpoint", END_SESSION))
      .build();

    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestConfig.class, ClientSecurityConfig.class);
    context.refresh();

    mockMvc = MockMvcBuilders.webAppContextSetup(context)
      .apply(springSecurity())
      .build();
  }

  @AfterAll
  static void tearDown()
  {
    if(context != null)
    {
      context.close();
    }
  }

  @Test
  @DisplayName("security matrix matches the matrix recorded before the Spring Security 7 upgrade")
  void securityMatrixIsUnchanged()
    throws Exception
  {
    List<String> actual = new ArrayList<>();

    actual.add("# access matrix: METHOD PATH USER -> outcome per vault state");
    actual.add("# 404 = passed the security filter chain (no controllers registered)");
    for(String path : GET_PATHS)
    {
      matrixLines(actual, "GET", path, false);
    }
    for(String path : POST_PATHS)
    {
      matrixLines(actual, "POST", path, false);
      matrixLines(actual, "POST", path, true);
    }
    for(String path : DELETE_PATHS)
    {
      matrixLines(actual, "DELETE", path, false);
      matrixLines(actual, "DELETE", path, true);
    }

    actual.add("");
    actual.add("# response headers");
    headerLines(actual, "GET", "/", User.ANON);
    headerLines(actual, "GET", "/favicon.ico", User.ANON);
    headerLines(actual, "GET", "/admin", User.ADMIN);
    headerLines(actual, "GET", "/api/v1/admin/x", User.NOROLE);

    Files.createDirectories(ACTUAL.getParent());
    Files.write(ACTUAL, actual, StandardCharsets.UTF_8);

    assertThat(asSentByContainer(actual))
      .containsExactlyElementsOf(asSentByContainer(readGolden()));
  }

  /**
   * Resolves relative redirect targets the way the servlet container does
   * before the response leaves the application.
   * <p>
   * Spring Security 7 issues the login redirect as a relative URI
   * ({@code LoginUrlAuthenticationEntryPoint#favorRelativeUris} now defaults to
   * {@code true}); Spring Security 6 built the absolute URI itself. In the
   * running application both end up as the same absolute {@code Location}
   * header: embedded Tomcat resolves relative redirects against the request
   * because Spring Boot sets {@code server.tomcat.use-relative-redirects=false}
   * (default in Boot 3 and 4), and behind the reverse proxy the
   * {@code ForwardedHeaderFilter} ({@code server.forward-headers-strategy:
   * framework}) does the same with the forwarded host. MockMvc has neither, so
   * the comparison applies that resolution to both sides. Access decisions and
   * status codes are compared unchanged.
   */
  private static List<String> asSentByContainer(List<String> lines)
  {
    return lines.stream()
      .map(line -> line.replaceAll("(\\b3\\d\\d) (/[^ |]*)", "$1 http://localhost$2"))
      .toList();
  }

  private static void matrixLines(List<String> out, String method, String path,
    boolean withCsrf)
    throws Exception
  {
    String label = method + (withCsrf ? "+csrf" : "");
    for(User user : User.values())
    {
      Map<Vault, String> results = new TreeMap<>();
      for(Vault vault : Vault.values())
      {
        results.put(vault, outcome(perform(method, path, user, vault, withCsrf)));
      }
      String joined;
      if(results.values().stream().distinct().count() == 1)
      {
        joined = "all=" + results.get(Vault.SEALED_KEYS);
      }
      else
      {
        joined = results.entrySet().stream()
          .map(e -> e.getKey() + "=" + e.getValue())
          .collect(Collectors.joining(" | "));
      }
      out.add(String.format("%-11s %-42s %-10s -> %s", label, path, user, joined));
    }
  }

  private static void headerLines(List<String> out, String method, String path,
    User user)
    throws Exception
  {
    MockHttpServletResponse response =
      perform(method, path, user, Vault.UNSEALED_KEYS, false);
    out.add(method + " " + path + " " + user + " -> " + response.getStatus());
    new TreeMap<>(response.getHeaderNames().stream()
      .filter(name -> ! "Location".equalsIgnoreCase(name))
      .collect(Collectors.toMap(name -> name,
        name -> String.join(", ", response.getHeaders(name)))))
      .forEach((name, value) -> out.add("  " + name + ": " + value));
  }

  private static MockHttpServletResponse perform(String method, String path,
    User user, Vault vault, boolean withCsrf)
    throws Exception
  {
    when(vaultService.getUnlockedKey()).thenReturn(vault.unsealed
      ? new SecretKeySpec(new byte[32], "AES") : null);
    when(vaultService.adminKeysIsEmpty()).thenReturn(vault.noKeys);

    MockHttpServletRequestBuilder builder =
      request(org.springframework.http.HttpMethod.valueOf(method),
        UriComponentsBuilder.fromUriString(path).build().toUri());

    if(user == User.ANON_XHR)
    {
      builder.header("X-Requested-With", "XMLHttpRequest");
    }
    if(user == User.ANON_JSON)
    {
      builder.header("Accept", "application/json");
    }
    if(user.authenticated())
    {
      builder.with(oidcLogin()
        .clientRegistration(registration)
        .idToken(token -> token
        .tokenValue("id-token-value")
        .subject("user-1")
        .claim("preferred_username", "user1")
        .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
        .expiresAt(Instant.parse("2099-01-01T00:00:00Z")))
        .authorities(Arrays.stream(user.roles)
          .map(SimpleGrantedAuthority::new)
          .toArray(GrantedAuthority[]::new)));
    }
    if(withCsrf)
    {
      builder.with(csrf());
    }

    return mockMvc.perform(builder).andReturn().getResponse();
  }

  private static String outcome(MockHttpServletResponse response)
  {
    String location = response.getHeader("Location");
    if(location == null)
    {
      return Integer.toString(response.getStatus());
    }
    return response.getStatus() + " " + normalizeLocation(location);
  }

  /**
   * Replaces the random parts of an authorization request (state, nonce,
   * PKCE challenge) with {@code *} so the matrix is deterministic.
   */
  private static String normalizeLocation(String location)
  {
    UriComponents uri = UriComponentsBuilder.fromUriString(location).build();
    if(uri.getQueryParams().isEmpty())
    {
      return location;
    }
    String query = new TreeMap<>(uri.getQueryParams()).entrySet().stream()
      .map(e -> e.getKey() + "="
      + (List.of("state", "nonce", "code_challenge").contains(e.getKey())
         ? "*" : String.join(",", e.getValue())))
      .collect(Collectors.joining("&"));
    return UriComponentsBuilder.fromUriString(location)
      .replaceQuery(null).build().toUriString() + "?" + query;
  }

  private static List<String> readGolden()
    throws IOException
  {
    try(InputStream in =
      ClientSecurityConfigGoldenTest.class.getResourceAsStream(GOLDEN))
    {
      assertThat(in)
        .as("golden file %s missing; review %s and copy it there", GOLDEN, ACTUAL)
        .isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
    }
  }

}
