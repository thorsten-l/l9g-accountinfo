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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import l9g.account.info.dto.IssueType;
import lombok.RequiredArgsConstructor;

/**
 * Controller responsible for handling signature pad related web requests.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Signature Pad", description = "Endpoints for managing signature pad interactions")
public class SignaturePadController
{
  /**
   * Object mapper for JSON serialization/deserialization.
   */
  private final ObjectMapper objectMapper;

  /**
   * WebSocket base URL for real-time communication with signature pad devices
   */
  @Value("${app.ws-url}")
  private String wsBaseUrl;

  @Value("${app.javascript.log-level}")
  private String jsLogLevel;

  /**
   * Flag indicating whether heartbeat functionality is enabled for connection monitoring
   */
  @Value("${scheduler.heartbeat.enabled}")
  private boolean heartbeatEnabled;

  /**
   * Displays the main signature pad interface.
   * Sets up the necessary model attributes for localization and WebSocket connectivity.
   *
   * @param mode The mode of the signature pad, e.g., "UNSET" or a specific pickup mode.
   * @param principal The authenticated OIDC user.
   * @param request The HTTP servlet request.
   * @param model The Spring MVC model for passing data to the view.
   *
   * @return The name of the signature-pad template to render.
   *
   * @throws JsonProcessingException If there is an error processing JSON.
   */
  @Operation(summary = "Display the signature pad interface",
             description = "Renders the signature pad view, configuring it with locale, WebSocket URL, and heartbeat settings.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "Signature pad interface successfully displayed"),
               @ApiResponse(responseCode = "401", description = "Unauthorized if user is not authenticated"),
               @ApiResponse(responseCode = "500", description = "Internal server error due to JSON processing issues")
             })
  @GetMapping("/signature-pad")
  public String signaturePad(
    @RequestParam(name = "issueType", required = false,
                  defaultValue = "UNKNOWN") IssueType issueType,
    @AuthenticationPrincipal DefaultOidcUser principal,
    HttpServletRequest request, Model model)
    throws JsonProcessingException
  {
    log.debug("signaturePad principal={}", principal);
    log.debug("signaturePad issueType={}", issueType);
    Locale locale = LocaleContextHolder.getLocale();
    log.debug("locale={}", locale);

    HttpSession session = request.getSession(true);

    if(issueType == IssueType.UNKNOWN)
    {
      if(session.getAttribute("ISSUE_TYPE") != null)
      {
        issueType = (IssueType)session.getAttribute("ISSUE_TYPE");
      }
    }
    else
    {
      session.setAttribute("ISSUE_TYPE", issueType);
    }

    Map<String, String> publisher = new HashMap<>();
    publisher.put("name", principal.getFullName());
    publisher.put("username", principal.getPreferredUsername());
    publisher.put("mail", principal.getEmail());

    model.addAttribute("principal", principal);
    model.addAttribute("publisher", publisher);
    model.addAttribute("issueType", issueType.getIssueType());
    model.addAttribute("jsLogLevel", jsLogLevel);
    model.addAttribute("locale", locale.toString());
    model.addAttribute("wsBaseUrl", wsBaseUrl);
    model.addAttribute("heartbeatEnabled", heartbeatEnabled);
    return "signature-pad";
  }

}
