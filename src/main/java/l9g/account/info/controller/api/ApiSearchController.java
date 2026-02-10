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
package l9g.account.info.controller.api;

import l9g.account.info.service.LdapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import l9g.account.info.dto.DtoUserInfo;
import org.springframework.http.ResponseEntity;

/**
 * REST API controller for user information retrieval.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(path = "/api/v1/search",
                produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Search API", description = "API for retrieving user details")
public class ApiSearchController
{
  /**
   * Service for authentication and authorization operations
   */
  private final AuthService authService;

  /**
   * Service for interacting with LDAP (Lightweight Directory Access Protocol) directory.
   */
  private final LdapService ldapService;

  /**
   * Retrieves card number for the specified customer number.
   *
   * @param padUuid The unique identifier of the requesting signature pad.
   * @param customerNumber The identifier of the user whose information is requested.
   * @param principal The authenticated OIDC user.
   *
   * @return A {@code ResponseEntity} containing a map with the card number if found, otherwise a not found response.
   *
   * @throws Exception If an error occurs during authentication or data retrieval.
   */
  @Operation(summary = "Retrieve cardnumber from customer number",
             description = "Retrieve cardnumber from customer number.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "Cardnumber successfully retrieved"),
               @ApiResponse(responseCode = "401", description = "Unauthorized if signature pad is not authenticated"),
               @ApiResponse(responseCode = "404", description = "Customer not found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @GetMapping(
    produces = MediaType.APPLICATION_JSON_VALUE, path = "customer")
  public ResponseEntity<Map<String, String>> customerNumber(
    @RequestHeader("SIGNATURE_PAD_UUID") String padUuid,
    @RequestParam("customer") String customerNumber,
    @AuthenticationPrincipal DefaultOidcUser principal
  )
    throws Exception
  {
    log.debug("customerNumber called for customer number '{}'", customerNumber);
    log.debug("principal={}", principal);

    // Authenticate signature pad
    authService.authCheck(padUuid, true);

    String cardNumber = ldapService.findCardNumberByCustomerNumber(customerNumber);

    if(cardNumber != null)
    {
      Map<String, String> map = new HashMap<>();
      map.put("card", cardNumber);
      return ResponseEntity.ok(map);
    }

    return ResponseEntity.notFound().build();
  }


  @GetMapping(
    produces = MediaType.APPLICATION_JSON_VALUE, path = "person")
  public ResponseEntity<List<DtoUserInfo>> personList(
    @RequestHeader("SIGNATURE_PAD_UUID") String padUuid,
    @RequestParam("query") String query,
    @AuthenticationPrincipal DefaultOidcUser principal
  )
    throws Exception
  {
    log.debug("personList called for query '{}'", query);
    log.debug("principal={}", principal);

    // Authenticate signature pad
    authService.authCheck(padUuid, true);

    List<DtoUserInfo> persons = ldapService.listPersons(query);

    if(persons != null && persons.size() > 0)
    {
      return ResponseEntity.ok(persons);
    }

    return ResponseEntity.notFound().build();
  }

}
