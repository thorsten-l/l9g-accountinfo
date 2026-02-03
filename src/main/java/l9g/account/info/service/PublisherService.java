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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
/**
 * Service for converting user principal information into a publisher map or JSON string.
 * This is used to capture details about the user performing an action, such as name, username, and email.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublisherService
{
  /**
   * Object mapper for JSON serialization/deserialization.
   */
  private final ObjectMapper objectMapper;

  /**
   * Converts a {@link DefaultOidcUser} principal into a map of publisher information.
   *
   * @param principal The authenticated {@link DefaultOidcUser}.
   *
   * @return A map containing the full name, preferred username, and email of the principal.
   */
  public Map<String, String> principalToPublisherMap(DefaultOidcUser principal)
  {
    Map<String, String> publisher = new HashMap<>();
    publisher.put("name", principal.getFullName());
    publisher.put("username", principal.getPreferredUsername());
    publisher.put("mail", principal.getEmail());
    return publisher;
  }

  /**
   * Converts a {@link DefaultOidcUser} principal into a JSON string representing publisher information.
   *
   * @param principal The authenticated {@link DefaultOidcUser}.
   *
   * @return A JSON string containing the full name, preferred username, and email of the principal.
   *
   * @throws JsonProcessingException If there is an error during JSON serialization.
   */
  @SneakyThrows
  public String principalToPublisherJSON(DefaultOidcUser principal)
  {
    Map<String, String> publisher = principalToPublisherMap(principal);
    return objectMapper.writeValueAsString(publisher);
  }

}
