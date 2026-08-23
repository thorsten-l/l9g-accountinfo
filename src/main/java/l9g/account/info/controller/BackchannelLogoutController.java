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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import l9g.account.info.service.LogoutTokenVerifier;
import l9g.account.info.service.SessionStoreService;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for handling OpenID Connect (OIDC) backchannel logout requests.
 * This controller processes logout tokens to invalidate user sessions, ensuring
 * proper logout synchronization across all clients.
 * <p>
 * The endpoint is deliberately reachable without authentication and is exempt
 * from CSRF protection, because the identity provider calls it server-to-server.
 * The logout token is therefore the only credential and is fully verified by
 * {@link LogoutTokenVerifier} before any session is touched.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping(path = "/oidc-backchannel-logout")
@Tag(name = "Logout", description = "Backchannel logout endpoint for OIDC")
public class BackchannelLogoutController
{
  /**
   * Name of the form parameter carrying the logout token, as defined by the
   * OpenID Connect Backchannel Logout specification.
   */
  private static final String LOGOUT_TOKEN_PARAMETER = "logout_token";

  /**
   * Verifier for incoming logout tokens.
   */
  private final LogoutTokenVerifier logoutTokenVerifier;

  /**
   * Service for storing and invalidating user sessions.
   */
  private final SessionStoreService sessionStore;

  /**
   * Handles OIDC backchannel logout requests. The logout token is verified
   * first; only then is the referenced session invalidated.
   * <p>
   * The specification sends the token as a {@code logout_token} form parameter.
   * Depending on how the request is proxied the body may instead arrive
   * unparsed, so both shapes are accepted — the form parameter takes precedence.
   *
   * @param logoutTokenParameter The {@code logout_token} form parameter, if the
   * request was parsed as a form.
   * @param body The raw request body, used when no form parameter was bound.
   *
   * @return {@code 200 OK} once the session has been invalidated.
   *
   * @throws org.springframework.web.server.ResponseStatusException with
   * {@code 400 BAD REQUEST} if the logout token is missing or cannot be
   * verified.
   */
  @Operation(summary = "Handle OIDC backchannel logout",
             description = "Receives a logout token, verifies it against the identity provider and invalidates the associated user session.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "Logout successful"),
               @ApiResponse(responseCode = "400", description = "Invalid logout token"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @PostMapping
  public ResponseEntity<Void> handleBackchannelLogout(
    @RequestParam(name = LOGOUT_TOKEN_PARAMETER, required = false) String logoutTokenParameter,
    @RequestBody(required = false) String body)
  {
    log.debug("handleBackchannelLogout");

    String logoutToken = logoutTokenParameter != null
      ? logoutTokenParameter : extractTokenFromBody(body);

    String sid = logoutTokenVerifier.verifyAndExtractSid(logoutToken);
    sessionStore.invalidateByOAuth2Sid(sid);

    log.info("BACKCHANNEL_LOGOUT: sid={}", sid);
    return ResponseEntity.ok().build();
  }

  /**
   * Extracts the logout token from an unparsed request body, accepting both a
   * bare token and a {@code logout_token=...} form encoding.
   *
   * @param body The raw request body, may be {@code null}.
   *
   * @return The token, or {@code null} if the body carries none.
   */
  private String extractTokenFromBody(String body)
  {
    if(body == null || body.isBlank())
    {
      return null;
    }

    String trimmed = body.trim();

    if( ! trimmed.startsWith(LOGOUT_TOKEN_PARAMETER + "="))
    {
      return trimmed;
    }

    String value =
      trimmed.substring(LOGOUT_TOKEN_PARAMETER.length() + 1);
    int end = value.indexOf('&');
    if(end >= 0)
    {
      value = value.substring(0, end);
    }

    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

}
