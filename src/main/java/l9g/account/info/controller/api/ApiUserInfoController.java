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

import java.io.IOException;
import l9g.account.info.dto.DtoAddress;
import l9g.account.info.dto.DtoUserInfo;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * REST API controller for user information retrieval.
 * Provides endpoints for fetching user details and associated data
 * for signature pad operations.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(path = "/api/v1/userinfo",
                produces = MediaType.APPLICATION_JSON_VALUE)
public class ApiUserInfoController
{
  /**
   * Service for authentication and authorization operations
   */
  private final AuthService authService;

  /**
   * Retrieves user information for the specified user ID.
   * Returns comprehensive user data including personal details, addresses,
   * and profile photo for display on signature pad devices.
   *
   * @param padUuid the unique identifier of the requesting signature pad
   * @param cardNumber the identifier of the user whose information is requested
   *
   * @return user information data transfer object containing all user details
   *
   * @throws IOException if authentication fails or resource access fails
   * @throws ResponseStatusException if user not found
   */
  @GetMapping(
    produces = MediaType.APPLICATION_JSON_VALUE)
  public DtoUserInfo userinfo(
    @RequestHeader("SIGNATURE_PAD_UUID") String padUuid,
    @RequestParam("card") String cardNumber,
    @AuthenticationPrincipal DefaultOidcUser principal
  )
    throws IOException
  {
    log.debug("userinfo called for card number '{}'", cardNumber);
    log.debug("principal={}", principal);

    // Authenticate signature pad
    authService.authCheck(padUuid, true);

    // Demo implementation - only supports user123
    if( ! cardNumber.equalsIgnoreCase("091600045759") &&  ! cardNumber.equalsIgnoreCase("091600110911"))
    {
      log.error("ERROR: card number not found {}", cardNumber);
      return new DtoUserInfo("ERROR: card number not found : " + cardNumber);
    }
    else
    {

      // Create demo address information
      DtoAddress semester = new DtoAddress(
        "c/o M. Maier", "Musterstr. Str 1701", "38302", "Wolfenbüttel",
        "Niedersachsen", "Deutschland");
      DtoAddress home = new DtoAddress(
        null, "Neuer Weg 4711", "38302", "Wolfenbüttel",
        "Niedersachsen", "Deutschland");

      // Load and encode demo profile photo
      String jpegPhoto = null;

/*      ClassPathResource imgFile = new ClassPathResource("demo/MarieMuster.jpg");

      try(InputStream is = imgFile.getInputStream())
      {
        byte[] imageBytes = is.readAllBytes();
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        jpegPhoto = "data:image/jpeg;base64," + base64;
      }
*/

      // Create and return user information object
      DtoUserInfo userInfo = new DtoUserInfo("OK",
        jpegPhoto, "Marie", "Muster", "user123", "m.muster@the.net",
        "01.01.2005", semester, home);

      return userInfo;
    }
  }

}
