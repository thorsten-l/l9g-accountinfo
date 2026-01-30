/*
 * Copyright 2024 Thorsten Ludewig <t.ludewig@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 *
 * @author Thorsten Ludewig <t.ludewig@gmail.com>
 */
@Component
@Slf4j
public class AppAuthoritiesConverter
{
  // private final PosUserRepository posUserRepository;

  // @Value("${app.oauth2.client-id}")
  private final String resourceAccessRoles;

  public AppAuthoritiesConverter(
    @Value("${app.oauth2.client-id}") String resourceAccessRoles)
  {
    this.resourceAccessRoles = resourceAccessRoles;
  }
  
  public Collection<GrantedAuthority> convert(OidcUser oidcUser, Jwt jwt)
  {
    log.debug("app.oauth2.client-id={}", resourceAccessRoles);
    
    /*
    List<String> realmRoles =
      (jwt.getClaimAsMap("realm_access") != null
      && jwt.getClaimAsMap("realm_access").get("roles") != null)
      ? (List<String>)jwt.getClaimAsMap("realm_access").get("roles")
      : new ArrayList<String>();
*/
    List<String> resourceRoles =
      (jwt.getClaimAsMap("resource_access") != null
      && jwt.getClaimAsMap("resource_access")
        .get(resourceAccessRoles) != null
      && ((Map)jwt.getClaimAsMap("resource_access")
        .get(resourceAccessRoles)).get("roles") != null)
      ? ((Map<String, List<String>>)jwt
        .getClaimAsMap("resource_access")
        .get(resourceAccessRoles)).get("roles")
      : new ArrayList<String>();

    /*
    List<GrantedAuthority> authorities = Stream.concat(realmRoles.stream(),
      resourceRoles.stream())
      .map(role -> "ROLE_" + role)
      .map(SimpleGrantedAuthority :: new)
      .collect(Collectors.toList());
*/
    
    List<GrantedAuthority> authorities = 
      resourceRoles.stream()
      .map(role -> "ROLE_" + role.toUpperCase())
      .map(SimpleGrantedAuthority :: new)
      .collect(Collectors.toList());

    return authorities;
  }

}
