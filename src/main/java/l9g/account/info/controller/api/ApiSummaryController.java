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

import java.util.Map;
import l9g.account.info.dto.DtoUserInfo;
import l9g.account.info.service.LdapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import l9g.account.info.db.DbService;

/**
 * REST API controller for user information retrieval.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(path = "/api/v1/summary",
                produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Summary API", description = "API for retrieving summary details")
public class ApiSummaryController
{

  /**
   * Service for interacting with LDAP (Lightweight Directory Access Protocol) directory.
   */
  private final DbService dbService;

  @Operation(summary = "Retrieve db summary",
             description = "Fetches basic db summary.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "Db summary successfully retrieved"),
               @ApiResponse(responseCode = "401", description = "Unauthorized"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @GetMapping()
  public ResponseEntity<Map<String, Object>> summary(
    @AuthenticationPrincipal DefaultOidcUser principal
  )
  {
    log.debug("summary called");
    log.debug("principal={}", principal);

    Map<String, Object> result = dbService.getTotalSecretDataCounts();

    if(result == null || result.isEmpty())
    {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(result);
  }

}
