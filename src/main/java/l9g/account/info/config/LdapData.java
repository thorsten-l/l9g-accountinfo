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

import java.util.Map;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for LDAP data retrieval.
 * Maps properties with the prefix "ldap.configuration" to define how user and locality data
 * are fetched from an LDAP directory.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Configuration
@ConfigurationProperties(prefix = "ldap.configuration")
@Data
@ToString
public class LdapData
{
  /**
   * LDAP configuration specific to user data.
   */
  private LdapConfig user;

  /**
   * LDAP configuration specific to locality data.
   */
  private LdapConfig locality;

  /**
   * Represents the configuration for a specific LDAP query,
   * including base distinguished name, search scope, filter, and attributes to retrieve.
   */
  @Data
  @ToString
  public static class LdapConfig
  {
    /**
     * The base distinguished name for the LDAP search.
     */
    private String baseDn;

    /**
     * The search scope for the LDAP query (e.g., "SUB", "ONE").
     */
    private String scope;

    /**
     * The LDAP filter expression to apply to the search.
     */
    private String filter;

    /**
     * The LDAP filter expression to apply to the person search.
     */
    private String filterCommonName;
    
    /**
     * User attribute enabled = value
     */
    private String enabledValue;

    /**
     * A map of attributes to retrieve from the LDAP entries, mapping desired names to LDAP attribute names.
     */
    private Map<String, String> attributes;

  }

}
