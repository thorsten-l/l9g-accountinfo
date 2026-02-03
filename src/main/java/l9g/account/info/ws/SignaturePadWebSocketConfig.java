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
package l9g.account.info.ws;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import l9g.account.info.service.SignaturePad;
import l9g.account.info.service.SignaturePadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeFailureException;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Configuration for WebSocket connectivity, specifically for signature pads.
 * This class registers the WebSocket handler and defines an interceptor for
 * handling handshake authentication using a signature pad UUID.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Configuration
@EnableWebSocket
@Slf4j
@RequiredArgsConstructor
public class SignaturePadWebSocketConfig implements WebSocketConfigurer
{
  /**
   * HTTP header name or WebSocket sub-protocol for passing the signature pad UUID.
   */
  public static final String SIGNATURE_PAD_UUID = "SIGNATURE_PAD_UUID";

  /**
   * Service for managing signature pad operations and data persistence.
   */
  private final SignaturePadService signaturePadService;

  /**
   * Registers WebSocket handlers with the specified registry.
   * This method configures the WebSocket endpoint "/ws/signature-pad",
   * sets up a custom handshake handler, adds an API key handshake interceptor for authentication,
   * and allows all origins.
   *
   * @param registry The {@link WebSocketHandlerRegistry} to register handlers with.
   */
  @Override
  public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry)
  {
    log.debug("registerWebSocketHandlers");

    DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
    handshakeHandler.setSupportedProtocols(SIGNATURE_PAD_UUID);

    registry
      .addHandler(webSocketHandler(), "/ws/signature-pad")
      .setHandshakeHandler(handshakeHandler)
      .addInterceptors(new ApiKeyHandshakeInterceptor())
      .setAllowedOrigins("*");
  }

  /**
   * Creates and configures a {@link SignaturePadWebSocketHandler} bean.
   * This handler manages WebSocket connections and messages from signature pads.
   *
   * @return A new instance of {@link SignaturePadWebSocketHandler}.
   */
  @Bean
  SignaturePadWebSocketHandler webSocketHandler()
  {
    log.debug("webSocketHandler");
    return new SignaturePadWebSocketHandler();
  }

  /**
   * Handshake interceptor for authenticating WebSocket connections using an API key (signature pad UUID).
   * It extracts the API key from the Sec-WebSocket-Protocol header and validates it against registered signature pads.
   */
  private class ApiKeyHandshakeInterceptor implements HandshakeInterceptor
  {

    /**
     * Intercepts the WebSocket handshake request before it is processed.
     * This method extracts the signature pad UUID from the "Sec-WebSocket-Protocol" header,
     * authenticates it against registered signature pads, and sets the UUID as an attribute for the session.
     *
     * @param request The current HTTP request.
     * @param response The current HTTP response.
     * @param wsHandler The WebSocket handler that will be used.
     * @param attributes A map of attributes to be passed to the WebSocket session.
     *
     * @return {@code true} if the handshake should proceed, {@code false} otherwise.
     *
     * @throws HandshakeFailureException If an authentication or authorization failure occurs.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public boolean beforeHandshake(
      @NonNull ServerHttpRequest request,
      @NonNull ServerHttpResponse response,
      @NonNull WebSocketHandler wsHandler,
      @NonNull Map<String, Object> attributes)
      throws HandshakeFailureException, IOException
    {
      log.debug("*** beforeHandshake");
      HttpHeaders headers = request.getHeaders();

      //attributes.put("SIGNATURE_PAD_UUID", "759f10c1-155d-4913-b9a9-844b6e2c2f29");
      //return true;
      List<String> protocolHeaders = headers.get(WebSocketHttpHeaders.SEC_WEBSOCKET_PROTOCOL);

      if(protocolHeaders == null || protocolHeaders.isEmpty())
      {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }

      String apiKey = null;
      for(String h : protocolHeaders)
      {
        log.debug("ph={}", h);
        if(h.startsWith(SIGNATURE_PAD_UUID + ","))
        {
          apiKey = h.split("\\,")[1].trim();
          break;
        }
      }

      log.debug("WebSocket-Handshake: {}={}", SIGNATURE_PAD_UUID, apiKey);

      if(apiKey == null || apiKey.isBlank())
      {
        log.warn("Missing API-Key");
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }

      SignaturePad signaturePad = signaturePadService.findSignaturePadByUUID(apiKey);

      if(signaturePad == null)
      {
        log.warn("Unkown API-Key: {}", apiKey);
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }

      if( ! signaturePad.isValidated())
      {
        log.warn("Invalid API-Key: {}", apiKey);
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }

      attributes.put("SIGNATURE_PAD_UUID", apiKey);
      return true;
    }

    /**
     * Called after the handshake is completed.
     * This method does nothing in this implementation.
     *
     * @param request The current HTTP request.
     * @param response The current HTTP response.
     * @param wsHandler The WebSocket handler that was used.
     * @param exception An exception if the handshake failed, or null if successful.
     */
    @Override
    public void afterHandshake(
      @NonNull ServerHttpRequest request,
      @NonNull ServerHttpResponse response,
      @NonNull WebSocketHandler wsHandler,
      @Nullable Exception exception)
    {
    }

  }

}
