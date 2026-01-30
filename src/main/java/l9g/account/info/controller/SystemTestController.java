/*
 * Copyright 2024 Thorsten Ludewig (t.ludewig@gmail.com).
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Controller
@Slf4j
public class SystemTestController
{
  @GetMapping("/system/test/403")
  public String systemError403()
  {
    log.debug("systemError403");
    throw new AccessDeniedException("Access denied for testing purposes");
  }

  @GetMapping("/system/test/500")
  public String systemError500()
  {
    log.debug("systemError500");
    int i = 1 / 0; // division by zero
    System.out.println(i);
    return "home";
  }

  @GetMapping("/system/test/400")
  public String badRequest400(@RequestParam("no_params") String p)
  {
    log.debug("badRequest400");
    return "home";
  }
  
  @GetMapping("/system/test/400-2")
  public String badRequest400()
  {
    log.debug("badRequest400-2");
    return "home";
  }

}
