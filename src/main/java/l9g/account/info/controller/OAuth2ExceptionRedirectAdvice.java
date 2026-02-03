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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Handles OAuth2 client-related exceptions by invalidating the session and redirecting to the home page.
 * This class acts as a global advice for controllers, specifically catching
 * {@link ClientAuthorizationException} and {@link ClientAuthorizationRequiredException}.
 */
@Slf4j
@ControllerAdvice(annotations = Controller.class)
public class OAuth2ExceptionRedirectAdvice
{

  @ExceptionHandler(
  {
    ClientAuthorizationException.class,
    ClientAuthorizationRequiredException.class
  })
  /**
   * Handles OAuth2 client authorization errors.
   * When a {@link ClientAuthorizationException} or {@link ClientAuthorizationRequiredException} occurs,
   * this method clears the security context, invalidates the HTTP session, and redirects the user to the root URL.
   *
   * @param request The current {@link HttpServletRequest}.
   * @param ex The caught {@link Exception}.
   *
   * @return A redirect string to the root URL ("/").
   */
  public String handleOAuth2ClientErrors(HttpServletRequest request, Exception ex)
  {
    log.warn("OAuth2 client error on {} -> invalidate session and redirect to / {}",
      request.getRequestURI(), ex.getMessage());

    // 1) SecurityContext leeren
    SecurityContextHolder.clearContext();

    // 2) HTTP-Session invalidieren (falls vorhanden)
    HttpSession session = request.getSession(false);
    if(session != null)
    {
      try
      {
        session.invalidate();
      }
      catch(IllegalStateException ignored)
      {
        // bereits invalidiert
      }
    }

    // 3) Redirect
    return "redirect:/";
  }

}
