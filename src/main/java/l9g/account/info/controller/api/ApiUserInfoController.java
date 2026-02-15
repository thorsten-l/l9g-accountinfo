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

import l9g.account.info.dto.DtoUserInfo;
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
import jakarta.servlet.http.HttpServletRequest;

/**
 * REST API controller for user information retrieval.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(path = "/api/v1/userinfo",
                produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User Info API", description = "API for retrieving user details")
public class ApiUserInfoController
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
   * Retrieves user information for the specified user ID.
   * Returns comprehensive user data including personal details, addresses,
   * and profile photo for display on signature pad devices.
   *
   * @param padUuid The unique identifier of the requesting signature pad.
   * @param cardNumber The identifier of the user whose information is requested.
   * @param principal The authenticated OIDC user.
   *
   * @return User information data transfer object containing all user details.
   *
   * @throws Exception If an error occurs during authentication or data retrieval.
   */
  @Operation(summary = "Retrieve user information",
             description = "Fetches comprehensive user data for display on signature pad devices, based on card number.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "User information successfully retrieved"),
               @ApiResponse(responseCode = "401", description = "Unauthorized if signature pad is not authenticated"),
               @ApiResponse(responseCode = "404", description = "User not found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @GetMapping(
    produces = MediaType.APPLICATION_JSON_VALUE)
  public DtoUserInfo userinfo(
    @RequestHeader("SIGNATURE_PAD_UUID") String padUuid,
    @RequestParam("card") String cardNumber,
    HttpServletRequest request,
    @AuthenticationPrincipal DefaultOidcUser principal
  )
    throws Exception
  {
    log.info("USER_SEARCH: {}, {}, {}, {}", request.getSession(true).getId(), 
      principal.getName(), padUuid, cardNumber);
    log.debug("userinfo called for card number '{}'", cardNumber);
    log.debug("principal={}", principal);

    // Authenticate signature pad
    authService.authCheck(padUuid, true);

    DtoUserInfo userInfo = ldapService.findUserInfoByCustomerNumber(cardNumber);
    if(userInfo != null)
    {
      log.info("USER_FOUND: {}, {}, {}, {}, {}, {}", request.getSession(true).getId(), 
              principal.getName(), padUuid, userInfo.customer(), 
              userInfo.barcode(), userInfo.uid());
      return userInfo;
    }

    return new DtoUserInfo("ERROR: card number not found : " + cardNumber);
  }

}
