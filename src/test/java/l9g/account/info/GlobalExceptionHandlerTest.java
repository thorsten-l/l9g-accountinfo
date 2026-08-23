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
package l9g.account.info;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}. Pure Mockito stubs of the
 * servlet API are used, so no Spring context and no servlet container is
 * involved.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class GlobalExceptionHandlerTest
{
  private GlobalExceptionHandler handler;

  private HttpServletRequest request;

  private HttpServletResponse response;

  private HttpSession session;

  @BeforeEach
  void setUp()
  {
    handler = new GlobalExceptionHandler();
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    session = mock(HttpSession.class);
    when(request.getSession()).thenReturn(session);
  }

  // ------------------------------------------------------------------ 400

  @Test
  @DisplayName("a bad request renders the 400 page with the request URI")
  void badRequestRendersErrorPage()
  {
    when(request.getRequestURI()).thenReturn("/app");

    ModelAndView mav = handler.handleBadRequestException(request,
      new IllegalArgumentException("missing parameter"));

    assertThat(mav.getViewName()).isEqualTo("error/400");
    assertThat(mav.getModel())
      .containsEntry("pageErrorRequestUri", "/app")
      .containsEntry("pageErrorException", "missing parameter");
  }

  // ------------------------------------------------------------------ 404

  /**
   * Static assets must not be answered with the HTML error page: a missing
   * stylesheet or webjar has to stay a bare 404 so the browser does not try to
   * parse an HTML document as CSS or JavaScript.
   */
  @ParameterizedTest
  @ValueSource(strings =
  {
    "/webjars/bootstrap/css/bootstrap.min.css",
    "/css/main.css",
    "/js/app.js",
    "/images/loading.png",
    "/favicon.ico",
    "/flags/4x3/de.svg",
    "/images/photo.jpg"
  })
  @DisplayName("a missing static asset yields a bare 404 without a view")
  void missingStaticAssetYieldsBare404(String uri)
    throws Exception
  {
    when(request.getRequestURI()).thenReturn(uri);

    ModelAndView mav = handler.handleNotFoundException(request, response,
      new RuntimeException("no resource"));

    assertThat(mav).isNull();
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  @DisplayName("a missing page yields the 404 error view")
  void missingPageYieldsErrorView()
    throws Exception
  {
    when(request.getRequestURI()).thenReturn("/admin/does-not-exist");

    ModelAndView mav = handler.handleNotFoundException(request, response,
      new RuntimeException("no resource"));

    assertThat(mav).isNotNull();
    assertThat(mav.getViewName()).isEqualTo("error/404");
    assertThat(mav.getModel())
      .containsEntry("pageErrorRequestUri", "/admin/does-not-exist");
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  // ------------------------------------------- ResponseStatusException

  @Test
  @DisplayName("a ResponseStatusException selects the view matching its status code")
  void responseStatusExceptionSelectsMatchingView()
  {
    when(request.getRequestURI()).thenReturn("/api/v1/admin/secret/pads");

    ModelAndView mav = handler.handleResponseStatusException(request, response,
      new ResponseStatusException(HttpStatus.FORBIDDEN,
        "Erase person is not allowed"));

    assertThat(mav.getViewName()).isEqualTo("error/403");
    assertThat(mav.getModel())
      .containsEntry("pageErrorException", "Erase person is not allowed");
    verify(response).setStatus(403);
  }

  @Test
  @DisplayName("the status code is propagated verbatim, even without a matching template")
  void responseStatusCodeIsPropagatedVerbatim()
  {
    when(request.getRequestURI()).thenReturn("/app");

    ModelAndView mav = handler.handleResponseStatusException(request, response,
      new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "short and stout"));

    assertThat(mav.getViewName()).isEqualTo("error/418");
    verify(response).setStatus(418);
  }

  // ------------------------------------------------------------------ 500

  @Test
  @DisplayName("an unhandled exception logs the user out and renders the 500 page")
  void unhandledExceptionLogsOutAndRendersErrorPage()
    throws Exception
  {
    when(request.getRequestURI()).thenReturn("/app");

    ModelAndView mav = handler.handleException(request,
      new IllegalStateException("boom"));

    verify(request).logout();
    assertThat(mav.getViewName()).isEqualTo("error/500");
    assertThat(mav.getModel())
      .containsEntry("pageErrorRequestUri", "/app")
      .containsEntry("pageErrorException", "boom")
      .containsEntry("pageErrorExceptionClassname",
        "java.lang.IllegalStateException");
    assertThat((String)mav.getModel().get("pageErrorStacktrace"))
      .isNotBlank()
      .contains("GlobalExceptionHandlerTest");
  }

  @Test
  @DisplayName("a failing logout does not prevent the 500 page from rendering")
  void failingLogoutIsSwallowed()
    throws Exception
  {
    when(request.getRequestURI()).thenReturn("/app");
    doThrow(new ServletException("no session")).when(request).logout();

    ModelAndView mav = handler.handleException(request,
      new IllegalStateException("boom"));

    assertThat(mav.getViewName()).isEqualTo("error/500");
  }

  // ------------------------------------------------------------------ 403

  @Test
  @DisplayName("access denied renders the 403 page and invalidates the session")
  void accessDeniedInvalidatesSession()
  {
    when(request.getRequestURI()).thenReturn("/admin/useraudit");

    ModelAndView mav = handler.handleAccessDeniedException(
      new AccessDeniedException("Access is denied"), request);

    assertThat(mav.getViewName()).isEqualTo("error/403");
    assertThat(mav.getModel())
      .containsEntry("pageErrorRequestUri", "/admin/useraudit")
      .containsEntry("pageErrorException", "Access is denied");
    verify(session).invalidate();
  }

  @Test
  @DisplayName("the 404 handler never touches the session")
  void notFoundHandlerDoesNotTouchTheSession()
    throws Exception
  {
    when(request.getRequestURI()).thenReturn("/admin/does-not-exist");

    assertThatCode(() -> handler.handleNotFoundException(request, response,
      new RuntimeException("no resource"))).doesNotThrowAnyException();

    verify(session, never()).invalidate();
    verify(request, never()).logout();
  }

}
