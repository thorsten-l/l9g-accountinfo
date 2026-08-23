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
package l9g.account.info.controller.api;

import java.util.List;
import l9g.account.info.dto.DtoUserInfo;
import l9g.account.info.service.LdapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApiSearchController}, whose only job besides delegating
 * is to strip LDAP filter metacharacters from the user-supplied query. That
 * sanitization is the sole barrier against LDAP filter injection on this
 * endpoint, so its exact character set is pinned here.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class ApiSearchControllerTest
{
  private static final String PAD_UUID = "11111111-2222-3333-4444-555555555555";

  private AuthService authService;

  private LdapService ldapService;

  private ApiSearchController controller;

  @BeforeEach
  void setUp()
  {
    authService = mock(AuthService.class);
    ldapService = mock(LdapService.class);
    controller = new ApiSearchController(authService, ldapService);
  }

  /**
   * Runs a query through the controller and returns the string that actually
   * reached the LDAP service.
   *
   * @param query The raw query as sent by the client.
   *
   * @return The sanitized query passed on to {@link LdapService}.
   *
   * @throws Exception If the controller call fails.
   */
  private String sanitizedQueryFor(String query)
    throws Exception
  {
    org.mockito.Mockito.reset(ldapService);
    when(ldapService.listPersons(any())).thenReturn(List.of());

    controller.personList(PAD_UUID, query, null);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(ldapService).listPersons(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("the signature pad is authenticated before the directory is queried")
  void padIsAuthenticatedFirst()
    throws Exception
  {
    when(ldapService.listPersons(any())).thenReturn(List.of());

    controller.personList(PAD_UUID, "muster", null);

    verify(authService).authCheck(PAD_UUID, true);
  }

  @Test
  @DisplayName("a failed pad authentication prevents the directory query")
  void failedAuthenticationSkipsQuery()
    throws Exception
  {
    when(authService.authCheck(PAD_UUID, true)).thenThrow(
      new org.springframework.web.server.ResponseStatusException(
        HttpStatus.NOT_FOUND, "Signature pad UUID not found!"));

    try
    {
      controller.personList(PAD_UUID, "muster", null);
    }
    catch(Exception expected)
    {
      // the endpoint is expected to fail here
    }

    verify(ldapService, never()).listPersons(any());
  }

  @ParameterizedTest
  @CsvSource(value =
  {
    "muster              ; muster",
    "(uid=*)             ; uid",
    "a)(objectClass=*    ; aobjectClass",
    "muster*             ; muster",
    "a&b|c               ; abc",
    "back\\slash        ; backslash",
    "plus+sign           ; plussign",
    "*)(uid=*))(|(uid=*  ; uiduid"
  }, delimiter = ';')
  @DisplayName("LDAP filter metacharacters are stripped from the query")
  void ldapMetacharactersAreStripped(String raw, String expected)
    throws Exception
  {
    assertThat(sanitizedQueryFor(raw.trim())).isEqualTo(expected.trim());
  }

  /**
   * Documents the exact scope of this controller's blacklist, which deliberately
   * stayed as it was: it removes {@code ( ) & | = * \ +} and nothing else, so
   * {@code <}, {@code >}, {@code ~}, {@code !}, the comma and a NUL byte pass
   * through here.
   * <p>
   * That is not a hole. In the production filter template
   * {@code (&(givenName=%s*)(sn=%s*)…)} the value always lands in an assertion's
   * <em>value</em> position, where those characters are literal per RFC 4515 —
   * {@code <=} and {@code ~=} require the character in the <em>attribute</em>
   * position. And they never reach the directory in any case:
   * {@code LdapService.sanitizeFilterValue} applies a whitelist to every value
   * before it is interpolated, which is where the actual protection lives (see
   * {@code LdapServiceTest} and {@code LdapServiceInjectionTest}).
   * <p>
   * This layer is therefore defence in depth, and this test keeps its behaviour
   * visible rather than treating it as the barrier.
   */
  @Test
  @DisplayName("this layer passes <, >, !, ~, comma and NUL through - the whitelist in LdapService stops them")
  void unstrippedFilterCharacters()
    throws Exception
  {
    assertThat(sanitizedQueryFor("a<b>c!d~e,f")).isEqualTo("a<b>c!d~e,f");
    String withNul = "nul" + ((char)0) + "byte";
    assertThat(sanitizedQueryFor(withNul)).isEqualTo(withNul);
  }

  @Test
  @DisplayName("matching persons are returned with 200")
  void matchingPersonsAreReturned()
    throws Exception
  {
    List<DtoUserInfo> persons = List.of(
      new DtoUserInfo("Marie", "Muster", "mmuster", "1990-05-17", "1234",
        "customer"));
    when(ldapService.listPersons("muster")).thenReturn(persons);

    assertThat(controller.personList(PAD_UUID, "muster", null))
      .satisfies(response ->
      {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(persons);
      });
  }

  @Test
  @DisplayName("an empty or null result yields 404")
  void emptyResultYieldsNotFound()
    throws Exception
  {
    when(ldapService.listPersons("muster")).thenReturn(List.of());
    assertThat(controller.personList(PAD_UUID, "muster", null).getStatusCode())
      .isEqualTo(HttpStatus.NOT_FOUND);

    when(ldapService.listPersons("muster")).thenReturn(null);
    assertThat(controller.personList(PAD_UUID, "muster", null).getStatusCode())
      .isEqualTo(HttpStatus.NOT_FOUND);
  }

}
