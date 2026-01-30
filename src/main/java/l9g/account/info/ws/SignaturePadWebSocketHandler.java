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
package l9g.account.info.ws;

import l9g.account.info.dto.DtoEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;

/**
 * WebSocket handler for managing real-time communication with signature pad devices.
 * Handles connection lifecycle, message routing, and event broadcasting to signature pads.
 * Maintains active sessions and provides methods to send events to specific pads or all connected devices.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@RequiredArgsConstructor
public class SignaturePadWebSocketHandler implements WebSocketHandler
{
  /**
   * Map storing active WebSocket sessions indexed by session ID
   */
  @Getter
  private final Map<String, WebSocketSession> sessionsBySessionId = new HashMap<>();

  /**
   * Object mapper for JSON serialization of outgoing messages
   */
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Invoked after a new WebSocket connection has been established.
   * Validates the signature pad UUID and stores the session if valid.
   *
   * @param session the WebSocket session that has been established
   *
   * @throws Exception if an error occurs during the establishment of the connection
   */
  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session)
    throws Exception
  {
    String padUuid = (String)session.getAttributes().get(SignaturePadWebSocketConfig.SIGNATURE_PAD_UUID);
    log.debug("afterConnectionEstablished: session id = {}/{}", session.getId(), padUuid);

    if(padUuid != null)
    {
      // Validate that the padUuid is a proper UUID format
      UUID uuid = UUID.fromString(padUuid);
      if(uuid.toString().equals(padUuid))
      {
        log.debug("store session");
        this.sessionsBySessionId.put(session.getId(), session);
      }
    }
  }

  /**
   * Handles incoming WebSocket messages from signature pad devices.
   * Currently logs incoming messages for debugging purposes.
   *
   * @param session the WebSocket session associated with the message
   * @param message the WebSocket message received
   *
   * @throws Exception if an error occurs while handling the message
   */
  @Override
  public void handleMessage(
    @NonNull WebSocketSession session,
    @NonNull WebSocketMessage<?> message)
    throws Exception
  {
    log.debug("handleMessage ({}) message.payload={}",
      session.getId(), message.getPayload().toString());
  }

  /**
   * Handles transport errors that occur during WebSocket communication.
   * Closes the session and removes it from the active sessions map.
   *
   * @param session the WebSocket session where the error occurred
   * @param exception the exception that was thrown
   *
   * @throws Exception if an error occurs while handling the transport error
   */
  @Override
  public void handleTransportError(@NonNull WebSocketSession session,
    @NonNull Throwable exception)
    throws Exception
  {
    log.error("handleTransportError: session id = {}, error: {}",
      session.getId(), exception.getMessage());
    session.close();
    sessionsBySessionId.remove(session.getId());
  }

  /**
   * Invoked after a WebSocket connection has been closed.
   * Removes the session from the active sessions map for cleanup.
   *
   * @param session the WebSocket session that was closed
   * @param closeStatus the status object containing the code and reason for the closure
   *
   * @throws Exception if any error occurs during the handling of the closed connection
   */
  @Override
  public void afterConnectionClosed(@NonNull WebSocketSession session,
    @NonNull CloseStatus closeStatus)
    throws Exception
  {
    log.debug("afterConnectionClosed {} status {}/{}",
      session.getId(), closeStatus.getCode(), closeStatus.getReason());
    sessionsBySessionId.remove(session.getId());
  }

  /**
   * Indicates whether this WebSocket handler supports partial messages.
   *
   * @return false, indicating that partial messages are not supported
   */
  @Override
  public boolean supportsPartialMessages()
  {
    return false;
  }

  /**
   * Broadcasts an event to all connected signature pad sessions.
   * Automatically cleans up closed sessions during the broadcast process.
   *
   * @param event the event to be sent to all WebSocket sessions
   *
   * @throws IOException if an I/O error occurs while sending the message
   */
  public void fireEventToAllSessions(DtoEvent event)
    throws IOException
  {
    log.trace("fireEvent to {} sessions", sessionsBySessionId.size());

    // Clean up closed sessions
    sessionsBySessionId.forEach((id, session) ->
    {
      if(session == null ||  ! session.isOpen())
      {
        sessionsBySessionId.remove(id);
      }
    });

    // Send event to all active sessions
    for(WebSocketSession session : sessionsBySessionId.values())
    {
      if(session != null && session.isOpen())
      {
        String json = objectMapper.writeValueAsString(event);
        session.sendMessage(new TextMessage(json));
        log.trace("Sent text message: {}", json);
      }
    }
  }

  /**
   * Sends an event to a specific signature pad identified by its UUID.
   * Only sends the message to sessions associated with the specified signature pad.
   *
   * @param event the event to send to the signature pad
   * @param padUuid the unique identifier of the target signature pad
   *
   * @throws IOException if an I/O error occurs while sending the message
   */
  public void fireEventToPad(DtoEvent event, String padUuid)
    throws IOException
  {
    log.trace("fireEvent to pad {}", padUuid);
    sessionsBySessionId.values().forEach(session ->
    {
      // Check if session is open and belongs to the target signature pad
      if(session.isOpen() && padUuid.equals((String)session.getAttributes().get(SignaturePadWebSocketConfig.SIGNATURE_PAD_UUID)))
      {
        try
        {
          String json = objectMapper.writeValueAsString(event);
          session.sendMessage(new TextMessage(json));
          log.trace("Sent text message: {}", json);
        }
        catch(Exception ex)
        {
          log.error("can't send message", ex);
        }
      }
    });
  }

}
