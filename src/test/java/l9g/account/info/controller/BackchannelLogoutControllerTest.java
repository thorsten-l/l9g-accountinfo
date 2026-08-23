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
package l9g.account.info.controller;

import l9g.account.info.service.LogoutTokenVerifier;
import l9g.account.info.service.SessionStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BackchannelLogoutController}: the token must be
 * verified before any session is invalidated, and both request shapes the
 * identity provider may use have to be accepted.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class BackchannelLogoutControllerTest
{
  private static final String TOKEN = "header.payload.signature";

  private LogoutTokenVerifier logoutTokenVerifier;

  private SessionStoreService sessionStore;

  private BackchannelLogoutController controller;

  @BeforeEach
  void setUp()
  {
    logoutTokenVerifier = mock(LogoutTokenVerifier.class);
    sessionStore = mock(SessionStoreService.class);
    controller =
      new BackchannelLogoutController(logoutTokenVerifier, sessionStore);
  }

  @Test
  @DisplayName("a verified token invalidates exactly the session it names")
  void verifiedTokenInvalidatesItsSession()
  {
    when(logoutTokenVerifier.verifyAndExtractSid(TOKEN))
      .thenReturn("session-1");

    assertThat(controller.handleBackchannelLogout(TOKEN, null).getStatusCode())
      .isEqualTo(HttpStatus.OK);

    verify(sessionStore).invalidateByOAuth2Sid("session-1");
  }

  /**
   * The central regression guard for the unauthenticated session-termination
   * defect: when verification fails, the session store must not be touched at
   * all. Previously a forged, unsigned token was decoded without any signature
   * check and the referenced session was terminated with a {@code 200 OK}.
   */
  @Test
  @DisplayName("a rejected token leaves every session untouched")
  void rejectedTokenLeavesSessionsUntouched()
  {
    when(logoutTokenVerifier.verifyAndExtractSid(any()))
      .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "Invalid logout token"));

    assertThatThrownBy(
      () -> controller.handleBackchannelLogout("forged.token.here", null))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(sessionStore, never()).invalidateByOAuth2Sid(any());
  }

  @Test
  @DisplayName("the token is verified before the session store is consulted")
  void verificationHappensFirst()
  {
    when(logoutTokenVerifier.verifyAndExtractSid(TOKEN))
      .thenReturn("session-1");

    controller.handleBackchannelLogout(TOKEN, null);

    org.mockito.InOrder order =
      org.mockito.Mockito.inOrder(logoutTokenVerifier, sessionStore);
    order.verify(logoutTokenVerifier).verifyAndExtractSid(TOKEN);
    order.verify(sessionStore).invalidateByOAuth2Sid("session-1");
  }

  // ------------------------------------------------------- request shapes

  @Test
  @DisplayName("a bare token in the request body is accepted")
  void bareBodyTokenIsAccepted()
  {
    when(logoutTokenVerifier.verifyAndExtractSid(TOKEN))
      .thenReturn("session-1");

    assertThat(controller.handleBackchannelLogout(null, TOKEN).getStatusCode())
      .isEqualTo(HttpStatus.OK);

    verify(sessionStore).invalidateByOAuth2Sid("session-1");
  }

  @Test
  @DisplayName("a form-encoded body is unwrapped to the bare token")
  void formEncodedBodyIsUnwrapped()
  {
    when(logoutTokenVerifier.verifyAndExtractSid(TOKEN))
      .thenReturn("session-1");

    controller.handleBackchannelLogout(null, "logout_token=" + TOKEN);

    verify(logoutTokenVerifier).verifyAndExtractSid(TOKEN);
  }

  @Test
  @DisplayName("a form-encoded body with further parameters is unwrapped correctly")
  void formEncodedBodyWithExtraParametersIsUnwrapped()
  {
    when(logoutTokenVerifier.verifyAndExtractSid(TOKEN))
      .thenReturn("session-1");

    controller.handleBackchannelLogout(null,
      "logout_token=" + TOKEN + "&state=xyz");

    verify(logoutTokenVerifier).verifyAndExtractSid(TOKEN);
  }

  @Test
  @DisplayName("percent-encoding in the form body is decoded")
  void percentEncodingIsDecoded()
  {
    when(logoutTokenVerifier.verifyAndExtractSid(any()))
      .thenReturn("session-1");

    controller.handleBackchannelLogout(null,
      "logout_token=a.b%2Bc.d");

    verify(logoutTokenVerifier).verifyAndExtractSid("a.b+c.d");
  }

  @Test
  @DisplayName("the form parameter wins over the request body")
  void formParameterTakesPrecedence()
  {
    when(logoutTokenVerifier.verifyAndExtractSid(any()))
      .thenReturn("session-1");

    controller.handleBackchannelLogout(TOKEN, "some.other.token");

    verify(logoutTokenVerifier).verifyAndExtractSid(TOKEN);
  }

  @Test
  @DisplayName("a request without any token is passed on as null and rejected")
  void requestWithoutTokenIsRejected()
  {
    when(logoutTokenVerifier.verifyAndExtractSid(null))
      .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "Missing logout token"));

    assertThatThrownBy(() -> controller.handleBackchannelLogout(null, "   "))
      .isInstanceOf(ResponseStatusException.class);

    verify(sessionStore, never()).invalidateByOAuth2Sid(any());
  }

}
