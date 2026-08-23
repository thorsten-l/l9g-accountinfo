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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AppAuthoritiesConverter}, the single place where the
 * OAuth2 token's {@code resource_access} claim is turned into Spring Security
 * authorities. Every {@code hasRole(...)} rule in
 * {@link ClientSecurityConfig} depends on the mapping asserted here.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class AppAuthoritiesConverterTest
{
  private static final String CLIENT_ID = "accountinfo";

  private AppAuthoritiesConverter converter;

  @BeforeEach
  void setUp()
  {
    converter = new AppAuthoritiesConverter(CLIENT_ID);
  }

  /**
   * Builds a minimal {@link Jwt} carrying the given claims. The
   * {@code OidcUser} argument of {@code convert} is unused by the
   * implementation, so tests pass {@code null} for it.
   *
   * @param claims The claims to place in the token.
   *
   * @return A {@link Jwt} instance carrying those claims.
   */
  private static Jwt jwtWith(Map<String, Object> claims)
  {
    Jwt.Builder builder = Jwt.withTokenValue("token")
      .header("alg", "none");
    claims.forEach(builder :: claim);
    return builder.build();
  }

  private static Jwt withRoles(String clientId, List<String> roles)
  {
    return jwtWith(Map.of("resource_access",
      Map.of(clientId, Map.of("roles", roles))));
  }

  @Test
  @DisplayName("roles are prefixed with ROLE_ and upper-cased")
  void rolesArePrefixedAndUpperCased()
  {
    Jwt jwt = withRoles(CLIENT_ID, List.of("admin", "Publisher", "TABADMIN"));

    assertThat(converter.convert(null, jwt))
      .extracting(GrantedAuthority :: getAuthority)
      .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_PUBLISHER",
        "ROLE_TABADMIN");
  }

  @Test
  @DisplayName("a token without resource_access yields no authorities")
  void missingResourceAccessYieldsNoAuthorities()
  {
    assertThat(converter.convert(null, jwtWith(Map.of("sub", "user1"))))
      .isEmpty();
  }

  @Test
  @DisplayName("roles of a different client are ignored")
  void rolesOfOtherClientsAreIgnored()
  {
    assertThat(converter.convert(null,
      withRoles("some-other-client", List.of("admin")))).isEmpty();
  }

  @Test
  @DisplayName("a client entry without a roles list yields no authorities")
  void clientEntryWithoutRolesYieldsNoAuthorities()
  {
    Jwt jwt = jwtWith(Map.of("resource_access",
      Map.of(CLIENT_ID, Map.of("something-else", List.of("admin")))));

    assertThat(converter.convert(null, jwt)).isEmpty();
  }

  @Test
  @DisplayName("an empty roles list yields no authorities")
  void emptyRolesListYieldsNoAuthorities()
  {
    assertThat(converter.convert(null, withRoles(CLIENT_ID, List.of())))
      .isEmpty();
  }

  /**
   * Regression guard: the {@code realm_access} handling is commented out in
   * {@code AppAuthoritiesConverter}, i.e. realm-wide Keycloak roles are
   * deliberately NOT granted. Re-enabling that block would silently widen
   * authorization for every user, so this test pins the current contract.
   */
  @Test
  @DisplayName("realm_access roles are deliberately ignored")
  void realmAccessRolesAreIgnored()
  {
    Jwt jwt = jwtWith(Map.of(
      "realm_access", Map.of("roles", List.of("admin", "offline_access")),
      "resource_access", Map.of(CLIENT_ID, Map.of("roles", List.of("publisher")))));

    assertThat(converter.convert(null, jwt))
      .extracting(GrantedAuthority :: getAuthority)
      .containsExactly("ROLE_PUBLISHER");
  }

}
