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
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import java.security.GeneralSecurityException;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;
import l9g.account.info.config.LdapData;
import l9g.account.info.crypto.EncryptedValue;
import l9g.account.info.dto.DtoAddress;
import l9g.account.info.dto.DtoUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 *
 * Component for handling all interactions with an LDAP directory.
 * <p>
 * This class encapsulates the logic for connecting to an LDAP server,
 * searching for entries, and retrieving user data. Configuration for the LDAP
 * connection and queries is injected from application properties. It is
 * - * designed to be used by services that need to query user information from
 * - * LDAP.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LdapService
{
  @Value("${ldap.host.name}")
  private String ldapHostname;

  @Value("${ldap.host.port}")
  private int ldapPort;

  @Value("${ldap.host.ssl}")
  private boolean ldapSslEnabled;

  @Value("${ldap.bind.dn}")
  private String ldapBindDn;

  @EncryptedValue("${ldap.bind.password}")
  private String ldapBindPassword;

  //- ATTRIBUTES --------------------------------------------------------------
  private final LdapData ldapDataConfig;

  //---------------------------------------------------------------------------
  private LDAPConnection getConnection()
    throws Exception
  {
    log.debug("host={}", ldapHostname);
    log.debug("port={}", ldapPort);
    log.debug("ssl={}", ldapSslEnabled);
    log.debug("bind dn={}", ldapBindDn);
    log.trace("bind pw={}", ldapBindPassword);

    LDAPConnection ldapConnection;

    LDAPConnectionOptions options = new LDAPConnectionOptions();
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

  private SSLSocketFactory createSSLSocketFactory()
    throws
    GeneralSecurityException
  {
    SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
    return sslUtil.createSSLSocketFactory();
  }

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

  private String mapAttributeValue(
    SearchResultEntry entry, Map<String, String> map, String key)
  {
    String attributeName = map.get(key);
    return (attributeName != null) ? entry.getAttributeValue(attributeName) : null;
  }

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

  public SearchResultEntry findUserEntryByCardNumber(
    LDAPConnection connection, String cardNumber)
    throws LDAPException
  {
    SearchResultEntry entry = null;

    LdapData.Configuration userConfig = ldapDataConfig.getUser();
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

  public DtoUserInfo findUserInfoByCardNumber(String cardNumber)
    throws Exception
  {
    log.debug("searching for cardnumber {} within ldap.", cardNumber);
    log.debug("ldapDataConfig={}", ldapDataConfig);
    DtoUserInfo userInfo = null;

    try(LDAPConnection connection = getConnection())
    {

      LdapData.Configuration userConfig = ldapDataConfig.getUser();
      LdapData.Configuration localityConfig = ldapDataConfig.getLocality();

      SearchResultEntry entry = findUserEntryByCardNumber(connection, cardNumber);

      log.debug("entry={}", entry);

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

  public void saveCardInfoByCardnumber(String cardNumber, String publisher)
  {
    log.debug("saveCardInfoByCardnumber: {} / {}", cardNumber, publisher);
    /*
      chipcard-is-issued: soniaChipcardIsIssued
      chipcard-is-issued-by: soniaChipcardIsIssuedBy
      chipcard-is-issued-timestamp: soniaChipcardIsIssuedTimestamp
      user-log: soniaUserLog
     */
  }

}
