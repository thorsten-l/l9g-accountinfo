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
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Configuration
@EnableWebSocket
@Slf4j
@RequiredArgsConstructor
public class SignaturePadWebSocketConfig implements WebSocketConfigurer
{
  public static final String SIGNATURE_PAD_UUID = "SIGNATURE_PAD_UUID";

  private final SignaturePadService signaturePadService;

  /**
   * Registers WebSocket handlers with the specified registry.
   * This method is called to configure the WebSocket handlers for the application.
   *
   * @param registry the WebSocketHandlerRegistry to register handlers with
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
   *
   * @return a new instance of {@link SignaturePadWebSocketHandler}
   */
  @Bean
  SignaturePadWebSocketHandler webSocketHandler()
  {
    log.debug("webSocketHandler");
    return new SignaturePadWebSocketHandler();
  }

  private class ApiKeyHandshakeInterceptor implements HandshakeInterceptor
  {

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
