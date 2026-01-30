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

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping(path = "/oidc-backchannel-logout")
public class BackchannelLogoutController
{
  private final JwtService jwtService;

  private final SessionStoreService sessionStore;

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
