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
package l9g.account.info.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Decodes JWT tokens for display purposes.
 * <p>
 * <strong>This service does not verify anything.</strong> It splits a token and
 * base64url-decodes its payload so the claims can be rendered; the signature is
 * never checked. It is therefore only safe to use on tokens whose authenticity
 * has already been established elsewhere — currently
 * {@code AppController} passes the id, access and refresh token of the
 * authenticated Spring Security session to it.
 * <p>
 * To <em>verify</em> a token, use a
 * {@link org.springframework.security.oauth2.jwt.JwtDecoder} built from the
 * identity provider's JWKS endpoint; see
 * {@code l9g.account.info.config.LogoutTokenConfig} for a working example. It
 * handles key rotation, {@code kid} selection and the standard claim checks,
 * none of which this class does.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Service
@Slf4j
public class JwtService
{
  /**
   * Splits a JWT token string into its three constituent parts: header, payload, and signature.
   *
   * @param jwt The JWT string to split.
   *
   * @return A string array containing the header, payload, and signature in that order.
   *
   * @throws IllegalArgumentException If the JWT format is invalid (does not have three parts).
   */
  public String[] splitJwt(String jwt)
  {
    String[] parts = jwt.split("\\.");
    if(parts.length != 3)
    {
      throw new IllegalArgumentException("Ungültiges JWT-Format");
    }
    return parts;
  }

  // decode Jwt ///////////////////////////////////////////////////////////
  /**
   * Decodes the payload section of a JWT token and returns it as a sorted map.
   * The payload is expected to be a Base64 URL-encoded JSON string.
   *
   * @param jwt The full JWT string from which to decode the payload.
   *
   * @return A {@link Map} containing the decoded payload claims, sorted by
   * natural order of keys. Values keep their JSON type, so numeric claims such
   * as {@code exp} and {@code iat} come back as numbers rather than strings.
   *
   * @throws RuntimeException If an error occurs during decoding or JSON parsing.
   */
  public Map<String, Object> decodeJwtPayload(String jwt)
  {
    try
    {
      String[] parts = splitJwt(jwt);

      String payload = parts[1];

      byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
      // an explicit charset: JWT payloads are UTF-8, the platform default is
      // not necessarily, which made non-ASCII claim values environment
      // dependent
      String decodedPayload = new String(decodedBytes, StandardCharsets.UTF_8);

      ObjectMapper objectMapper = new ObjectMapper();
      Map<String, Object> sorted = new TreeMap<>(Comparator.naturalOrder());
      sorted.putAll(objectMapper.readValue(decodedPayload, HashMap.class));

      return sorted;
    }
    catch(Exception e)
    {
      throw new RuntimeException("Fehler beim Decodieren des JWT-Tokens", e);
    }
  }

}
