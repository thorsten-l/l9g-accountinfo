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
package l9g.account.info.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import l9g.account.info.service.JwtService;
import l9g.account.info.service.SessionStoreService;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for handling OpenID Connect (OIDC) backchannel logout requests.
 * This controller processes logout tokens to invalidate user sessions, ensuring
 * proper logout synchronization across all clients.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping(path = "/oidc-backchannel-logout")
@Tag(name = "Logout", description = "Backchannel logout endpoint for OIDC")
public class BackchannelLogoutController
{
  /**
   * Service for handling JWT (JSON Web Token) related operations.
   */
  private final JwtService jwtService;

  /**
   * Service for storing and invalidating user sessions.
   */
  private final SessionStoreService sessionStore;

  /**
   * Handles OIDC backchannel logout requests.
   * This method receives a logout token, extracts the session ID (sid), and
   * invalidates the corresponding user session.
   *
   * @param logoutToken The logout token received from the OIDC provider.
   *
   * @return A ResponseEntity with HTTP 200 OK status.
   */
  @Operation(summary = "Handle OIDC backchannel logout",
             description = "Receives a logout token and invalidates the associated user session.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "Logout successful"),
               @ApiResponse(responseCode = "400", description = "Invalid logout token"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @PostMapping
  public ResponseEntity<Void> handleBackchannelLogout(@RequestBody String logoutToken)
  {
    log.debug("handleBackchannelLogout {}", logoutToken);
    Map<String, String> jwt = jwtService.decodeJwtPayload(logoutToken);
    log.debug("jwt {}", jwt);
    sessionStore.invalidateByOAuth2Sid(jwt.get("sid"));
    return ResponseEntity.ok().build();
  }

}
