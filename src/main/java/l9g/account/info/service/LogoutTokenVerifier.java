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

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifies OpenID Connect backchannel logout tokens before any session is acted
 * upon.
 * <p>
 * The logout endpoint is reachable without authentication and is exempt from
 * CSRF protection, so the logout token itself is the only credential. Accepting
 * an unverified token would let anyone terminate the session of any user whose
 * {@code sid} they know or can guess. Every check mandated by section 2.6 of the
 * OpenID Connect Backchannel Logout specification is therefore enforced here:
 * <ol>
 * <li>the JWS signature, against the identity provider's JWKS (handled by the
 * injected {@link JwtDecoder}, which also validates {@code iss}, {@code exp} and
 * {@code nbf}),</li>
 * <li>{@code aud} must contain this client's id,</li>
 * <li>{@code events} must carry the backchannel-logout event,</li>
 * <li>{@code nonce} must be absent — its presence marks an ID token being
 * replayed as a logout token,</li>
 * <li>a usable {@code sid} must be present.</li>
 * </ol>
 * Every failure results in {@code 400 BAD REQUEST} and leaves all sessions
 * untouched.
 * <p>
 * Not covered: {@code jti}-based replay detection. A captured logout token stays
 * usable until it expires, but it can only ever terminate the one session it was
 * legitimately issued for.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
public class LogoutTokenVerifier
{
  /**
   * The event identifier that marks a JWT as a backchannel logout token.
   */
  public static final String BACKCHANNEL_LOGOUT_EVENT =
    "http://schemas.openid.net/event/backchannel-logout";

  /**
   * Decoder validating the signature as well as {@code iss}, {@code exp} and
   * {@code nbf}.
   */
  private final JwtDecoder jwtDecoder;

  /**
   * The client id this application is registered under; the logout token's
   * {@code aud} claim must contain it.
   */
  private final String expectedAudience;

  /**
   * Constructs a {@code LogoutTokenVerifier}.
   *
   * @param jwtDecoder The decoder used to verify the token's signature and
   * standard claims.
   * @param expectedAudience The client id that must appear in the token's
   * {@code aud} claim.
   */
  public LogoutTokenVerifier(JwtDecoder jwtDecoder, String expectedAudience)
  {
    this.jwtDecoder = jwtDecoder;
    this.expectedAudience = expectedAudience;
  }

  /**
   * Verifies a logout token and returns the session identifier it refers to.
   *
   * @param logoutToken The serialized logout token as received from the identity
   * provider.
   *
   * @return The verified {@code sid} claim, never {@code null} or blank.
   *
   * @throws ResponseStatusException with {@code 400 BAD REQUEST} if the token is
   * missing, unverifiable or not a valid backchannel logout token.
   */
  public String verifyAndExtractSid(String logoutToken)
  {
    if(logoutToken == null || logoutToken.isBlank())
    {
      log.error("LOGOUT_TOKEN_REJECTED: no logout token supplied");
      throw badRequest("Missing logout token");
    }

    Jwt jwt = decode(logoutToken.trim());

    checkAudience(jwt);
    checkBackchannelLogoutEvent(jwt);
    checkNonceIsAbsent(jwt);

    String sid = jwt.getClaimAsString("sid");
    if(sid == null || sid.isBlank())
    {
      log.error("LOGOUT_TOKEN_REJECTED: no sid claim, issuer={}",
        jwt.getIssuer());
      throw badRequest("Logout token without sid claim");
    }

    log.debug("logout token verified, issuer={}, sid={}", jwt.getIssuer(), sid);
    return sid;
  }

  /**
   * Verifies the token's signature and its standard claims.
   *
   * @param logoutToken The serialized token.
   *
   * @return The decoded token.
   *
   * @throws ResponseStatusException with {@code 400 BAD REQUEST} if the token
   * cannot be verified.
   */
  private Jwt decode(String logoutToken)
  {
    try
    {
      return jwtDecoder.decode(logoutToken);
    }
    catch(JwtException e)
    {
      log.error("LOGOUT_TOKEN_REJECTED: verification failed : {}",
        e.getMessage());
      throw badRequest("Invalid logout token");
    }
  }

  /**
   * Ensures the token was issued for this client.
   *
   * @param jwt The decoded token.
   */
  private void checkAudience(Jwt jwt)
  {
    List<String> audience = jwt.getAudience();
    if(audience == null ||  ! audience.contains(expectedAudience))
    {
      log.error("LOGOUT_TOKEN_REJECTED: audience {} does not contain {}",
        audience, expectedAudience);
      throw badRequest("Logout token was not issued for this client");
    }
  }

  /**
   * Ensures the token declares the backchannel logout event. Without this check
   * any other token issued for this client — an ID token, for instance — would
   * be accepted as a logout instruction.
   *
   * @param jwt The decoded token.
   */
  private void checkBackchannelLogoutEvent(Jwt jwt)
  {
    Object events = jwt.getClaim("events");
    if( ! (events instanceof Map<?, ?> eventMap)
      ||  ! eventMap.containsKey(BACKCHANNEL_LOGOUT_EVENT))
    {
      log.error("LOGOUT_TOKEN_REJECTED: no backchannel-logout event, "
        + "events={}", events);
      throw badRequest("Logout token without backchannel-logout event");
    }
  }

  /**
   * Ensures the token is not an ID token being replayed as a logout token; the
   * specification prohibits a {@code nonce} claim in a logout token.
   *
   * @param jwt The decoded token.
   */
  private void checkNonceIsAbsent(Jwt jwt)
  {
    if(jwt.hasClaim("nonce"))
    {
      log.error("LOGOUT_TOKEN_REJECTED: nonce claim present, "
        + "this is not a logout token");
      throw badRequest("Logout token must not contain a nonce claim");
    }
  }

  /**
   * Builds the uniform rejection response.
   *
   * @param reason The reason reported to the identity provider.
   *
   * @return The exception to throw.
   */
  private ResponseStatusException badRequest(String reason)
  {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
  }

}
