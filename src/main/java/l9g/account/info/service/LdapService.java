/*
 * Copyright 2025 Thorsten Ludewig (t.ludewig@gmail.com).
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

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.ModifyRequest;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLSocketFactory;
import l9g.account.info.config.LdapData;
import l9g.account.info.dto.DtoAddress;
import l9g.account.info.dto.DtoUserInfo;
import l9g.account.info.dto.IssueType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Component for handling all interactions with an LDAP directory.
 * <p>
 * This class encapsulates the logic for connecting to an LDAP server,
 * searching for entries, and retrieving user data. Configuration for the LDAP
 * connection and queries is injected from application properties. It is
 * designed to be used by services that need to query user information from
 * LDAP and update user attributes.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LdapService
{
  /**
   * The LDAP server hostname.
   */
  @Value("${ldap.host.name}")
  private String ldapHostname;

  /**
   * The LDAP server port.
   */
  @Value("${ldap.host.port}")
  private int ldapPort;

  /**
   * Indicates whether SSL/TLS is enabled for the LDAP connection.
   */
  @Value("${ldap.host.ssl}")
  private boolean ldapSslEnabled;

  /**
   * The Distinguished Name (DN) used for binding to the LDAP server.
   */
  @Value("${ldap.bind.dn}")
  private String ldapBindDn;

  /**
   * The encrypted password used for binding to the LDAP server.
   */
  @Value("${ldap.bind.password}")
  private String ldapBindPassword;

  //- ATTRIBUTES --------------------------------------------------------------
  /**
   * Configuration data for LDAP queries and attribute mappings.
   */
  private final LdapData ldapDataConfig;

  //---------------------------------------------------------------------------
  /**
   * Establishes and returns a new {@link LDAPConnection} to the configured LDAP server.
   * Connection details (host, port, SSL, bind DN, bind password) are sourced from application properties.
   *
   * @return An active {@link LDAPConnection} instance.
   *
   * @throws Exception If an error occurs during connection establishment or SSL configuration.
   */
  private LDAPConnection getConnection()
    throws Exception
  {
    log.debug("host={}", ldapHostname);
    log.debug("port={}", ldapPort);
    log.debug("ssl={}", ldapSslEnabled);
    log.trace("bind dn={}", ldapBindDn);

    LDAPConnection ldapConnection;

    LDAPConnectionOptions options = new LDAPConnectionOptions();
    options.setConnectTimeoutMillis(10_000);
    options.setResponseTimeoutMillis(30_000);

    if(ldapSslEnabled)
    {
      ldapConnection = new LDAPConnection(createSSLSocketFactory(), options,
        ldapHostname, ldapPort,
        ldapBindDn,
        ldapBindPassword);
    }
    else
    {
      ldapConnection = new LDAPConnection(options,
        ldapHostname, ldapPort,
        ldapBindDn,
        ldapBindPassword);
    }
    ldapConnection.setConnectionName(ldapHostname);
    return ldapConnection;
  }

  /**
   * Creates an {@link SSLSocketFactory} that trusts all certificates.
   * This is typically used for development or testing environments where
   * certificate validation might be bypassed.
   *
   * @return An {@link SSLSocketFactory} that trusts all certificates.
   *
   * @throws GeneralSecurityException If an error occurs during SSL utility initialization.
   */
  private SSLSocketFactory createSSLSocketFactory()
    throws
    GeneralSecurityException
  {
    SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
    return sslUtil.createSSLSocketFactory();
  }

  /**
   * Converts a string representation of an LDAP search scope into a {@link SearchScope} enum.
   * Supports "ONE" for one-level scope and "BASE" for base object scope,
   * defaulting to subtree scope for other values.
   *
   * @param ldapScope The string representation of the LDAP scope.
   *
   * @return The corresponding {@link SearchScope} enum constant.
   */
  private SearchScope scopeFromString(String ldapScope)
  {
    SearchScope scope = SearchScope.SUB;
    if("ONE".equalsIgnoreCase(ldapScope))
    {
      scope = SearchScope.ONE;
    }
    else if("BASE".equalsIgnoreCase(ldapScope))
    {
      scope = SearchScope.BASE;
    }
    return scope;
  }

  /**
   * Retrieves an attribute value from an LDAP entry based on a mapping key.
   *
   * @param entry The {@link SearchResultEntry} from which to retrieve the attribute.
   * @param map A map defining the mapping from a logical key to the actual LDAP attribute name.
   * @param key The logical key for the desired attribute.
   *
   * @return The string value of the LDAP attribute, or null if the attribute is not found or mapped.
   */
  private String mapAttributeValue(
    SearchResultEntry entry, Map<String, String> map, String key)
  {
    String attributeName = map.get(key);
    return (attributeName != null) ? entry.getAttributeValue(attributeName) : null;
  }

  /**
   * Constructs a {@link DtoAddress} object from an LDAP {@link SearchResultEntry}.
   * Attribute values are mapped using the provided map.
   *
   * @param entry The {@link SearchResultEntry} containing address-related attributes.
   * @param map A map defining the mapping from DTO field names to LDAP attribute names.
   *
   * @return A {@link DtoAddress} object populated with values from the LDAP entry.
   */
  private DtoAddress dtoAddressFromEntry(
    SearchResultEntry entry, Map<String, String> map)
  {
    return new DtoAddress(
      mapAttributeValue(entry, map, "co"),
      mapAttributeValue(entry, map, "street"),
      mapAttributeValue(entry, map, "zip"),
      mapAttributeValue(entry, map, "city"),
      mapAttributeValue(entry, map, "state"),
      mapAttributeValue(entry, map, "country")
    );
  }

  /**
   * Finds an LDAP user entry by a given card number.
   *
   * @param connection The active {@link LDAPConnection}.
   * @param cardNumber The card number to search for.
   *
   * @return The {@link SearchResultEntry} for the user, or null if not found.
   *
   * @throws LDAPException If an LDAP-specific error occurs during the search.
   */
  public SearchResultEntry findUserEntryByCardNumber(
    LDAPConnection connection, String cardNumber)
    throws LDAPException
  {
    SearchResultEntry entry = null;

    LdapData.LdapConfig userConfig = ldapDataConfig.getUser();
    String filter = String.format(userConfig.getFilter(), cardNumber);
    log.debug("LDAP filter: {}", filter);

    SearchRequest searchRequest = new SearchRequest(
      userConfig.getBaseDn(), scopeFromString(userConfig.getScope()),
      filter, userConfig.getAttributes().values().toArray(String[] :: new)
    );
    log.debug("searchRequest={}", searchRequest);

    SearchResult searchResult = connection.search(searchRequest);

    if(searchResult.getEntryCount() == 1)
    {
      entry = searchResult.getSearchEntries().getFirst();
    }

    return entry;
  }

  private DtoUserInfo userInfoFromEntry(
    LDAPConnection connection, SearchResultEntry entry)
    throws LDAPException
  {
    /**
     * Constructs a {@link DtoUserInfo} object from an LDAP {@link SearchResultEntry}.
     * This method retrieves various user attributes and constructs address objects by querying
     * related locality entries in LDAP.
     *
     * @param connection The active {@link LDAPConnection}.
     * @param entry The {@link SearchResultEntry} for the user.
     *
     * @return A {@link DtoUserInfo} object populated with the user's details, or null if the input entry is null.
     *
     * @throws LDAPException If an LDAP-specific error occurs during data retrieval.
     */
    DtoUserInfo userInfo = null;

    LdapData.LdapConfig userConfig = ldapDataConfig.getUser();
    LdapData.LdapConfig localityConfig = ldapDataConfig.getLocality();

    if(entry != null)
    {
      Map<String, String> map = userConfig.getAttributes();

      String status = "OK";
      String jpegPhoto = null;
      String firstname = mapAttributeValue(entry, map, "firstname");
      String lastname = mapAttributeValue(entry, map, "lastname");
      String uid = mapAttributeValue(entry, map, "username");
      String mail = mapAttributeValue(entry, map, "mail");
      String birthday = mapAttributeValue(entry, map, "birthday");

      String localityBaseDn = String.format(localityConfig.getBaseDn(), entry.getDN());

      log.debug("localityBaseDn={}", localityBaseDn);

      SearchRequest searchRequestLocality = new SearchRequest(
        localityBaseDn, scopeFromString(localityConfig.getScope()),
        localityConfig.getFilter(),
        localityConfig.getAttributes().values().toArray(String[] :: new)
      );

      SearchResult searchResultLocality = connection.search(searchRequestLocality);

      DtoAddress semester = null;
      DtoAddress home = null;

      map = localityConfig.getAttributes();

      for(SearchResultEntry localityEntry : searchResultLocality.getSearchEntries())
      {
        if(localityEntry.getDN().toLowerCase().startsWith("cn=home,"))
        {
          home = dtoAddressFromEntry(localityEntry, map);
        }
        else if(localityEntry.getDN().toLowerCase().startsWith("cn=semester,"))
        {
          semester = dtoAddressFromEntry(localityEntry, map);
        }
      }

      userInfo = new DtoUserInfo(status, jpegPhoto, firstname, lastname, uid,
        mail, birthday, semester, home);
    }

    return userInfo;
  }

  /**
   * Retrieves comprehensive user information as a {@link DtoUserInfo} object based on a card number.
   * This method performs LDAP searches for user and locality data using configured filters and attributes.
   *
   * @param cardNumber The card number to search for.
   *
   * @return A {@link DtoUserInfo} object containing the user's details, or null if the user is not found.
   *
   * @throws Exception If an error occurs during LDAP connection, search, or data processing.
   */
  public DtoUserInfo findUserInfoByCardNumber(String cardNumber)
    throws Exception
  {
    log.debug("searching for cardnumber {} within ldap.", cardNumber);
    log.debug("ldapDataConfig={}", ldapDataConfig);
    DtoUserInfo userInfo = null;

    try(LDAPConnection connection = getConnection())
    {
      SearchResultEntry entry = findUserEntryByCardNumber(connection, cardNumber);
      log.debug("entry={}", entry);
      userInfo = userInfoFromEntry(connection, entry);
    }
    catch(Exception e)
    {
      log.error("Error during LDAP search for cardNumber {}: {}",
        cardNumber, e.getMessage());
      throw e;
    }

    log.debug("userInfo={}", userInfo);
    return userInfo;
  }

  /**
   * Searches the LDAP directory for a card number associated with a given customer number.
   * This method uses a specific filter to query user entries by customer number.
   *
   * @param customerNumber The customer number to search for.
   *
   * @return The card number as a {@code String} if a matching user is found, otherwise {@code null}.
   *
   * @throws Exception If an error occurs during LDAP connection or search operations.
   */
  public String findCardNumberByCustomerNumber(String customerNumber)
    throws Exception
  {
    log.debug("searching for customer number {} within ldap.", customerNumber);
    log.debug("ldapDataConfig={}", ldapDataConfig);
    String cardNumber = null;

    try(LDAPConnection connection = getConnection())
    {

      LdapData.LdapConfig userConfig = ldapDataConfig.getUser();
      String filter = String.format(userConfig.getFilterCustomerNumber(), customerNumber, customerNumber, customerNumber);
      log.debug("LDAP filter: {}", filter);

      SearchRequest searchRequest = new SearchRequest(
        userConfig.getBaseDn(), scopeFromString(userConfig.getScope()),
        filter, userConfig.getAttributes().values().toArray(String[] :: new)
      );
      log.debug("searchRequest={}", searchRequest);

      SearchResult searchResult = connection.search(searchRequest);
      log.debug("searchResult={}", searchResult);

      if(searchResult.getEntryCount() == 1)
      {
        SearchResultEntry entry = searchResult.getSearchEntries().getFirst();
        cardNumber = entry.getAttributeValue("soniaChipcardBarcode");
      }
    }
    catch(Exception e)
    {
      log.error("Error during LDAP search for customer number {}: {}",
        customerNumber, e.getMessage());
      throw e;
    }

    log.debug("cardNumber={}", cardNumber);
    return cardNumber;
  }

  /**
   * Saves or updates card information for a user in the LDAP directory.
   * This method retrieves the user entry by card number and updates specific attributes
   * related to card issuance and user activity log.
   *
   * @param issueType The issue type ACCOUNT, ACCOUNT_CARD, CARD.
   * @param cardNumber The card number of the user.
   * @param publisher The publisher of the update, typically the authenticated user.
   * @param remoteIp The IP address of the client initiating the request.
   * @param padUuid The UUID of the signature pad used for the operation.
   * @param padName The name of the signature pad used for the operation.
   *
   * @throws Exception If an error occurs during LDAP connection, search, or modification.
   * @throws IllegalStateException If a required LDAP attribute mapping is missing or the user is not found.
   */
  public void saveCardInfoByCardnumber(IssueType issueType, String cardNumber,
    String publisher, String remoteIp, String padUuid, String padName)
    throws Exception
  {
    log.debug("saveCardInfoByCardnumber: {} / {} / {}", issueType, cardNumber, publisher);

    LdapData.LdapConfig userConfig = ldapDataConfig.getUser();
    Map<String, String> attributesMap = userConfig.getAttributes();

    try(LDAPConnection connection = getConnection())
    {

      SearchResultEntry entry = findUserEntryByCardNumber(connection, cardNumber);
      if(entry == null)
      {
        throw new IllegalStateException("User not found for cardNumber=" + cardNumber);
      }

      String dn = entry.getDN();

      long nowMs = System.currentTimeMillis();

      String issueDescription = switch(issueType)
      {
        case ACCOUNT ->
          "account form";
        case ACCOUNT_CARD ->
          "account form and chipcard";
        case CARD ->
          "chipcard";
        default ->
          "unknown";
      };

      String logLine = nowMs + "|" + remoteIp + "|" + publisher
        + "|accountinfo|issued <b>" + issueDescription + "</b> using device: '"
        + padName + " (" + padUuid + ")'";

      List<Modification> mods = new ArrayList<>(4);

      if(IssueType.ACCOUNT != issueType)
      {
        log.debug("modify chipcard attributes");
        String attrIssued = attributesMap.get("chipcard-is-issued");
        String attrIssuedBy = attributesMap.get("chipcard-is-issued-by");
        String attrIssuedTs = attributesMap.get("chipcard-is-issued-timestamp");

        requireAttr(attrIssued, "chipcard-is-issued");
        requireAttr(attrIssuedBy, "chipcard-is-issued-by");
        requireAttr(attrIssuedTs, "chipcard-is-issued-timestamp");

        mods.add(new Modification(ModificationType.REPLACE, attrIssued, "true"));
        mods.add(new Modification(ModificationType.REPLACE, attrIssuedBy, Objects.toString(publisher, "")));
        mods.add(new Modification(ModificationType.REPLACE, attrIssuedTs, Long.toString(nowMs)));
      }

      log.debug("modify (add) user-log");
      String attrUserLog = attributesMap.get("user-log");
      requireAttr(attrUserLog, "user-log");
      mods.add(new Modification(ModificationType.ADD, attrUserLog, logLine));

      ModifyRequest modifyRequest = new ModifyRequest(dn, mods);
      log.debug("LDAP modify dn={} mods={}", dn, mods.size());

      connection.modify(modifyRequest);
    }
    catch(Exception e)
    {
      log.error("Error during LDAP modify for cardNumber {}: {}", cardNumber, e.getMessage(), e);
      throw e;
    }
  }

  /**
   * Helper method to ensure that an LDAP attribute name is not null or blank.
   *
   * @param attrName The LDAP attribute name to check.
   * @param key The configuration key associated with the attribute, used for error messages.
   *
   * @throws IllegalStateException If the attribute name is null or blank.
   */
  private static void requireAttr(String attrName, String key)
  {
    if(attrName == null || attrName.isBlank())
    {
      throw new IllegalStateException("LDAP attribute mapping missing for key: " + key);
    }
  }

}
