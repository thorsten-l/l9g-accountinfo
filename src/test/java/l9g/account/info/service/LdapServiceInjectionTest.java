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

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.SearchResultEntry;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import l9g.account.info.config.LdapData;
import l9g.account.info.dto.DtoUserInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * End-to-end tests for the two LDAP searches that interpolate request data into
 * a filter, run against an in-memory directory from the UnboundID SDK.
 * <p>
 * {@link LdapServiceTest} verifies that
 * {@link LdapService#sanitizeFilterValue(String)} is correct. These tests verify
 * the other half — that it is actually <em>applied</em> on both paths — by
 * letting a real directory answer the resulting filter. Without the
 * sanitization, the injection cases below return every entry in the directory.
 * <p>
 * The directory is in-process, so no network and no LDAP server is involved.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class LdapServiceInjectionTest
{
  private static final String BASE_DN = "dc=example,dc=org";

  /**
   * The production filter templates, copied verbatim from the deployed
   * configuration so the tests exercise the real interpolation positions.
   */
  private static final String FILTER_CARD =
    "(&(|(soniaChipcardBarcode=%s)(soniaCustomerNumber=%s)"
    + "(soniaCustomerNumber=00%s))(objectClass=soniaPerson)"
    + "(!(soniaIsUnregistered=*)))";

  private static final String FILTER_NAME =
    "(&(givenName=%s*)(sn=%s*)(objectClass=soniaPerson)"
    + "(!(soniaIsUnregistered=*)))";

  private static InMemoryDirectoryServer directory;

  private static LdapService ldapService;

  @BeforeAll
  static void startDirectory()
    throws Exception
  {
    InMemoryDirectoryServerConfig config =
      new InMemoryDirectoryServerConfig(BASE_DN);
    // no schema: the sonia* attributes are not part of the standard schema
    config.setSchema(null);
    config.addAdditionalBindCredentials("cn=admin", "secret");
    config.setListenerConfigs(
      InMemoryListenerConfig.createLDAPConfig("test", 0));

    directory = new InMemoryDirectoryServer(config);
    directory.startListening();

    directory.add("dn: " + BASE_DN, "objectClass: domain", "dc: example");
    // Names are stored lower case on purpose: without a schema the in-memory
    // directory matches case-exactly, whereas the production directory uses
    // caseIgnoreMatch for givenName and sn. listPersons lower-cases the query,
    // so lower-case test data reproduces the production matching behaviour.
    addPerson("mmuster", "marie", "muster", "091600045759", "123456");
    addPerson("jdoe", "john", "doe", "091600045760", "123457");
    addPerson("gmartin", "georg-martin", "schmidt", "091600045761", "123458");

    ldapService = new LdapService(ldapConfig());
    injectConnectionSettings(ldapService, directory.getListenPort());
  }

  @AfterAll
  static void stopDirectory()
  {
    if(directory != null)
    {
      directory.shutDown(true);
    }
  }

  private static void addPerson(String uid, String givenName, String surname,
    String barcode, String customerNumber)
    throws Exception
  {
    directory.add(
      "dn: uid=" + uid + "," + BASE_DN,
      "objectClass: soniaPerson",
      "uid: " + uid,
      "givenName: " + givenName,
      "sn: " + surname,
      "mail: " + uid + "@example.org",
      "soniaChipcardBarcode: " + barcode,
      "soniaCustomerNumber: " + customerNumber);
  }

  /**
   * Builds the LDAP configuration with the production filter templates.
   *
   * @return The configuration.
   */
  private static LdapData ldapConfig()
  {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("firstname", "givenName");
    attributes.put("lastname", "sn");
    attributes.put("username", "uid");
    attributes.put("mail", "mail");
    attributes.put("barcode", "soniaChipcardBarcode");
    attributes.put("customer", "soniaCustomerNumber");

    LdapData.LdapConfig user = new LdapData.LdapConfig();
    user.setBaseDn(BASE_DN);
    user.setScope("SUB");
    user.setFilter(FILTER_CARD);
    user.setFilterCommonName(FILTER_NAME);
    user.setAttributes(attributes);

    LdapData data = new LdapData();
    data.setUser(user);
    return data;
  }

  /**
   * Fills the {@code @Value}-injected connection fields, which are private and
   * have no setters.
   *
   * @param service The service to configure.
   * @param port The port the in-memory directory listens on.
   *
   * @throws Exception If a field cannot be set.
   */
  private static void injectConnectionSettings(LdapService service, int port)
    throws Exception
  {
    set(service, "ldapHostname", "localhost");
    set(service, "ldapPort", port);
    set(service, "ldapSslEnabled", false);
    set(service, "ldapBindDn", "cn=admin");
    set(service, "ldapBindPassword", "secret");
  }

  private static void set(Object target, String fieldName, Object value)
    throws Exception
  {
    Field field = LdapService.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static LDAPConnection connection()
    throws Exception
  {
    return new LDAPConnection("localhost", directory.getListenPort(),
      "cn=admin", "secret");
  }

  // ------------------------------------------------------- person search

  /**
   * Intended search semantics, pinned so they stay that way: with a single
   * token, {@code listPersons} calls
   * {@code String.format(template, "", query, "")}, so the value lands in the
   * SECOND placeholder. In the template {@code (&(givenName=%s*)(sn=%s*)…)}
   * that is {@code sn} — a one-word query searches the SURNAME by design, and
   * the given name is left open as {@code (givenName=*)}. A second token then
   * narrows by given name.
   * <p>
   * Easy to misread from the call site, hence this test: the argument order is
   * what selects the field, and the third {@code %s} is never used.
   */
  @Test
  @DisplayName("a single token searches the surname and still finds the person")
  void singleTokenSearchesTheSurname()
    throws Exception
  {
    List<DtoUserInfo> found = ldapService.listPersons("muster");

    assertThat(found).isNotNull().hasSize(1);
    assertThat(found.get(0).firstname()).isEqualTo("marie");
    assertThat(found.get(0).lastname()).isEqualTo("muster");
    assertThat(found.get(0).uid()).isEqualTo("mmuster");

    assertThat(ldapService.listPersons("marie"))
      .as("a given name alone matches nothing, it is compared against sn")
      .isNull();
  }

  @Test
  @DisplayName("a two-token search matches given name and surname")
  void twoTokenSearchWorks()
    throws Exception
  {
    assertThat(ldapService.listPersons("marie muster")).hasSize(1);
    assertThat(ldapService.listPersons("marie doe")).isNull();
  }

  /**
   * The compatibility case behind allowing the hyphen: a double-barrelled given
   * name must survive sanitization and still match in the givenName position.
   */
  @Test
  @DisplayName("a double-barrelled given name is searchable")
  void doubleBarrelledNameIsSearchable()
    throws Exception
  {
    List<DtoUserInfo> found = ldapService.listPersons("georg-martin schmidt");

    assertThat(found).isNotNull().hasSize(1);
    assertThat(found.get(0).firstname()).isEqualTo("georg-martin");
    assertThat(found.get(0).uid()).isEqualTo("gmartin");

    assertThat(ldapService.listPersons("georg schmidt"))
      .as("the hyphenated name is matched by prefix, so 'georg' also hits")
      .hasSize(1);
  }

  /**
   * The decisive assertion for the person search: an injected filter must not
   * turn the query into "return everything". Unsanitized, the first payload
   * expands to {@code (&(givenName=*)(objectClass=*)…)} and would list all three
   * directory entries.
   */
  @ParameterizedTest
  @ValueSource(strings =
  {
    ")(objectClass=*",
    "*)(objectClass=*",
    "*)(uid=*))(|(uid=*",
    "a)(|(objectClass=soniaPerson",
    "cn=admin)(objectClass=*"
  })
  @DisplayName("an injected name filter never returns the whole directory")
  void nameSearchInjectionIsNeutralised(String payload)
    throws Exception
  {
    List<DtoUserInfo> found = ldapService.listPersons(payload);

    assertThat(found)
      .as("payload '%s' must not enumerate the directory", payload)
      .isNull();
  }

  /**
   * Intended behaviour, pinned deliberately: a query that reduces to nothing —
   * an empty string, only spaces, or only characters that get removed such as
   * {@code *} — produces {@code (&(givenName=*)(sn=*)…)} and therefore lists
   * every person. Both search endpoints require an authenticated session (a
   * validated pad, or ADMIN/AUDITADMIN with an unsealed vault), so listing the
   * directory is a feature of those interfaces rather than an oversight.
   * <p>
   * This is unchanged by the sanitization work: the controller always stripped
   * {@code *} to an empty string, and the previously unsanitized admin path
   * produced the equally permissive {@code (givenName=**)}.
   */
  @ParameterizedTest
  @ValueSource(strings =
  {
    "", "   ", "*", "()*"
  })
  @DisplayName("a query that reduces to nothing lists every person, as intended")
  void emptyQueryMatchesEveryPerson(String payload)
    throws Exception
  {
    assertThat(ldapService.listPersons(payload))
      .as("payload '%s'", payload)
      .hasSize(3);
  }

  @Test
  @DisplayName("an injected name filter does not produce an LDAP error either")
  void nameSearchInjectionDoesNotBreakTheFilter()
  {
    assertThatCode(() -> ldapService.listPersons("\\)(&(objectClass=*"))
      .doesNotThrowAnyException();
  }

  // --------------------------------------------------------- card lookup

  @Test
  @DisplayName("an ordinary card number still finds the person")
  void ordinaryCardLookupWorks()
    throws Exception
  {
    try(LDAPConnection connection = connection())
    {
      SearchResultEntry entry = ldapService
        .findUserEntryByCustomerNumber(connection, "091600045759");

      assertThat(entry).isNotNull();
      assertThat(entry.getAttributeValue("uid")).isEqualTo("mmuster");
    }
  }

  @Test
  @DisplayName("the customer number variant with leading zeros still works")
  void customerNumberLookupWorks()
    throws Exception
  {
    try(LDAPConnection connection = connection())
    {
      assertThat(ldapService
        .findUserEntryByCustomerNumber(connection, "123456")).isNotNull();
    }
  }

  /**
   * The decisive assertion for the card lookup — the path that had no
   * sanitization at all. Unsanitized, {@code )(objectClass=*} closes the
   * barcode assertion and the {@code |} branch then matches every entry.
   */
  @ParameterizedTest
  @ValueSource(strings =
  {
    ")(objectClass=*",
    "*)(objectClass=*",
    "*",
    "0)(soniaCustomerNumber=*",
    "x)(|(objectClass=soniaPerson"
  })
  @DisplayName("an injected card number never matches a foreign entry")
  void cardLookupInjectionIsNeutralised(String payload)
    throws Exception
  {
    try(LDAPConnection connection = connection())
    {
      SearchResultEntry entry =
        ldapService.findUserEntryByCustomerNumber(connection, payload);

      assertThat(entry)
        .as("payload '%s' must not resolve to an entry", payload)
        .isNull();
    }
  }

  @Test
  @DisplayName("an injected card number does not produce an LDAP error either")
  void cardLookupInjectionDoesNotBreakTheFilter()
    throws Exception
  {
    try(LDAPConnection connection = connection())
    {
      assertThatCode(() -> ldapService
        .findUserEntryByCustomerNumber(connection, "\\)(&(objectClass=*"))
        .doesNotThrowAnyException();
    }
  }

}
