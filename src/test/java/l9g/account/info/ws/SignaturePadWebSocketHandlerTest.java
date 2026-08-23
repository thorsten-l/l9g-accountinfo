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
package l9g.account.info.ws;

import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import l9g.account.info.dto.DtoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SignaturePadWebSocketHandler}: session bookkeeping and
 * event fan-out to the connected signature pads.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class SignaturePadWebSocketHandlerTest
{
  private static final String PAD_A = "11111111-2222-3333-4444-555555555555";

  private static final String PAD_B = "66666666-7777-8888-9999-000000000000";

  private SignaturePadWebSocketHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new SignaturePadWebSocketHandler();
  }

  /**
   * Creates a mocked WebSocket session.
   *
   * @param id The session id.
   * @param padUuid The pad UUID placed in the session attributes, may be null.
   * @param open Whether the session reports itself as open.
   *
   * @return The mocked session.
   */
  private static WebSocketSession session(String id, String padUuid,
    boolean open)
  {
    WebSocketSession session = mock(WebSocketSession.class);
    Map<String, Object> attributes = new HashMap<>();
    if(padUuid != null)
    {
      attributes.put(SignaturePadWebSocketConfig.SIGNATURE_PAD_UUID, padUuid);
    }
    when(session.getId()).thenReturn(id);
    when(session.getAttributes()).thenReturn(attributes);
    when(session.isOpen()).thenReturn(open);
    return session;
  }

  // ---------------------------------------------------- connection lifecycle

  @Test
  @DisplayName("a session with a valid pad UUID is stored")
  void validPadUuidIsStored()
    throws Exception
  {
    WebSocketSession session = session("s1", PAD_A, true);

    handler.afterConnectionEstablished(session);

    assertThat(handler.getSessionsBySessionId()).containsOnlyKeys("s1");
  }

  @Test
  @DisplayName("a session without the pad UUID attribute is not stored")
  void missingPadUuidIsNotStored()
    throws Exception
  {
    handler.afterConnectionEstablished(session("s1", null, true));

    assertThat(handler.getSessionsBySessionId()).isEmpty();
  }

  @Test
  @DisplayName("a malformed pad UUID is rejected with IllegalArgumentException")
  void malformedPadUuidIsRejected()
  {
    WebSocketSession session = session("s1", "not-a-uuid", true);

    assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
      .isInstanceOf(IllegalArgumentException.class);

    assertThat(handler.getSessionsBySessionId()).isEmpty();
  }

  @Test
  @DisplayName("a closed connection removes the session")
  void closedConnectionRemovesSession()
    throws Exception
  {
    WebSocketSession session = session("s1", PAD_A, true);
    handler.afterConnectionEstablished(session);

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    assertThat(handler.getSessionsBySessionId()).isEmpty();
  }

  @Test
  @DisplayName("a transport error closes and removes the session")
  void transportErrorClosesAndRemovesSession()
    throws Exception
  {
    WebSocketSession session = session("s1", PAD_A, true);
    handler.afterConnectionEstablished(session);

    handler.handleTransportError(session, new IOException("connection reset"));

    verify(session).close();
    assertThat(handler.getSessionsBySessionId()).isEmpty();
  }

  @Test
  @DisplayName("partial messages are not supported")
  void partialMessagesAreNotSupported()
  {
    assertThat(handler.supportsPartialMessages()).isFalse();
  }

  // -------------------------------------------------- fireEventToAllSessions

  @Test
  @DisplayName("a broadcast reaches every open session as a JSON text message")
  void broadcastReachesAllOpenSessions()
    throws Exception
  {
    WebSocketSession a = session("s1", PAD_A, true);
    WebSocketSession b = session("s2", PAD_B, true);
    handler.afterConnectionEstablished(a);
    handler.afterConnectionEstablished(b);

    handler.fireEventToAllSessions(new DtoEvent(DtoEvent.EVENT_HEARTBEAT));

    ArgumentCaptor<TextMessage> message =
      ArgumentCaptor.forClass(TextMessage.class);
    verify(a).sendMessage(message.capture());
    verify(b).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
    assertThat(message.getValue().getPayload())
      .contains("\"event\":\"heartbeat\"")
      .contains("\"timestamp\":");
  }

  @Test
  @DisplayName("a broadcast to no sessions is a no-op")
  void broadcastWithoutSessionsIsNoOp()
  {
    assertThatCode(() -> handler
      .fireEventToAllSessions(new DtoEvent(DtoEvent.EVENT_HEARTBEAT)))
      .doesNotThrowAnyException();
  }

  /**
   * The regression guard for the heartbeat outage: a closed session must be
   * reaped and the remaining pads must still receive the event. Before the fix
   * the cleanup ran {@code remove} inside {@code HashMap.forEach} and threw a
   * {@link ConcurrentModificationException} the first time a pad had
   * disconnected — which permanently killed the heartbeat job, since nothing
   * ever cleared the closed entry again.
   */
  @Test
  @DisplayName("a closed session is reaped and the remaining pads still receive the event")
  void closedSessionIsReapedAndOthersStillReceive()
    throws Exception
  {
    WebSocketSession open = session("s1", PAD_A, true);
    WebSocketSession closed = session("s2", PAD_B, false);
    handler.afterConnectionEstablished(open);
    handler.afterConnectionEstablished(closed);

    handler.fireEventToAllSessions(new DtoEvent(DtoEvent.EVENT_HEARTBEAT));

    assertThat(handler.getSessionsBySessionId()).containsOnlyKeys("s1");
    verify(open).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
    verify(closed, never())
      .sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
  }

  @Test
  @DisplayName("repeated broadcasts keep working after a pad disconnected")
  void broadcastKeepsWorkingAfterDisconnect()
    throws Exception
  {
    WebSocketSession open = session("s1", PAD_A, true);
    WebSocketSession closed = session("s2", PAD_B, false);
    handler.afterConnectionEstablished(open);
    handler.afterConnectionEstablished(closed);

    for(int i = 0; i < 5; i ++ )
    {
      handler.fireEventToAllSessions(new DtoEvent(DtoEvent.EVENT_HEARTBEAT));
    }

    verify(open, org.mockito.Mockito.times(5))
      .sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
  }

  /**
   * The same cleanup bug also broke {@code AdminController.getSignaturePadSessions},
   * which iterates the exposed map from request threads. Iterating it while
   * entries are added and removed must not throw.
   */
  @Test
  @DisplayName("the exposed session map can be iterated while it is being modified")
  void exposedMapIsSafeToIterate()
    throws Exception
  {
    for(int i = 0; i < 20; i ++ )
    {
      handler.afterConnectionEstablished(session("s" + i, PAD_A, true));
    }

    assertThatCode(() ->
    {
      int seen = 0;
      for(String id : handler.getSessionsBySessionId().keySet())
      {
        handler.getSessionsBySessionId().remove(id);
        seen ++ ;
      }
      assertThat(seen).isPositive();
    }).doesNotThrowAnyException();
  }

  // ------------------------------------------------------- fireEventToPad

  @Test
  @DisplayName("a targeted event reaches only the addressed pad")
  void targetedEventReachesOnlyItsPad()
    throws Exception
  {
    WebSocketSession a = session("s1", PAD_A, true);
    WebSocketSession b = session("s2", PAD_B, true);
    handler.afterConnectionEstablished(a);
    handler.afterConnectionEstablished(b);

    handler.fireEventToPad(new DtoEvent(DtoEvent.EVENT_CLEAR), PAD_A);

    ArgumentCaptor<TextMessage> message =
      ArgumentCaptor.forClass(TextMessage.class);
    verify(a).sendMessage(message.capture());
    verify(b, never()).sendMessage(
      org.mockito.ArgumentMatchers.any(TextMessage.class));
    assertThat(message.getValue().getPayload())
      .contains("\"event\":\"clear\"");
  }

  @Test
  @DisplayName("a targeted event to an unknown pad is silently ignored")
  void targetedEventToUnknownPadIsIgnored()
    throws Exception
  {
    WebSocketSession a = session("s1", PAD_A, true);
    handler.afterConnectionEstablished(a);

    handler.fireEventToPad(new DtoEvent(DtoEvent.EVENT_CLEAR), PAD_B);

    verify(a, never()).sendMessage(
      org.mockito.ArgumentMatchers.any(TextMessage.class));
  }

  @Test
  @DisplayName("a send failure on one pad is swallowed and does not propagate")
  void sendFailureIsSwallowed()
    throws Exception
  {
    WebSocketSession a = session("s1", PAD_A, true);
    handler.afterConnectionEstablished(a);
    doThrow(new IOException("broken pipe")).when(a)
      .sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));

    assertThatCode(() -> handler
      .fireEventToPad(new DtoEvent(DtoEvent.EVENT_SHOW), PAD_A))
      .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a null pad UUID is ignored instead of throwing")
  void nullPadUuidIsIgnored()
    throws Exception
  {
    WebSocketSession a = session("s1", PAD_A, true);
    handler.afterConnectionEstablished(a);

    assertThatCode(() -> handler
      .fireEventToPad(new DtoEvent(DtoEvent.EVENT_SHOW), null))
      .doesNotThrowAnyException();

    verify(a, never()).sendMessage(
      org.mockito.ArgumentMatchers.any(TextMessage.class));
  }

  // --------------------------------------------------------- wire format

  /**
   * Compatibility guard: over twenty signature pads are deployed in the field
   * and parse these messages with their own JavaScript. The payload shape must
   * not change, so the exact JSON of every event kind is pinned here.
   */
  @Test
  @DisplayName("the JSON sent to the pads keeps its exact shape")
  void wireFormatIsStable()
    throws Exception
  {
    WebSocketSession pad = session("s1", PAD_A, true);
    handler.afterConnectionEstablished(pad);

    handler.fireEventToAllSessions(new DtoEvent(DtoEvent.EVENT_HEARTBEAT));
    handler.fireEventToPad(
      new DtoEvent(DtoEvent.EVENT_SHOW, "091600045759"), PAD_A);

    ArgumentCaptor<TextMessage> messages =
      ArgumentCaptor.forClass(TextMessage.class);
    verify(pad, org.mockito.Mockito.times(2)).sendMessage(messages.capture());

    com.fasterxml.jackson.databind.ObjectMapper mapper =
      new com.fasterxml.jackson.databind.ObjectMapper();
    com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> asMap =
      new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>()
    {
    };

    Map<String, Object> heartbeat =
      mapper.readValue(messages.getAllValues().get(0).getPayload(), asMap);
    assertThat(heartbeat.keySet())
      .as("a heartbeat carries exactly event and timestamp")
      .containsExactlyInAnyOrder("event", "timestamp");
    assertThat(heartbeat).containsEntry("event", "heartbeat");

    Map<String, Object> show =
      mapper.readValue(messages.getAllValues().get(1).getPayload(), asMap);
    assertThat(show.keySet())
      .as("an event with a message carries event, timestamp and message")
      .containsExactlyInAnyOrder("event", "timestamp", "message");
    assertThat(show)
      .containsEntry("event", "show")
      .containsEntry("message", "091600045759");
  }

  @Test
  @DisplayName("the event identifiers the pads match on are unchanged")
  void eventIdentifiersAreStable()
  {
    assertThat(DtoEvent.EVENT_HEARTBEAT).isEqualTo("heartbeat");
    assertThat(DtoEvent.EVENT_SHOW).isEqualTo("show");
    assertThat(DtoEvent.EVENT_HIDE).isEqualTo("hide");
    assertThat(DtoEvent.EVENT_CLEAR).isEqualTo("clear");
    assertThat(DtoEvent.EVENT_ERROR).isEqualTo("error");
    assertThat(DtoEvent.EVENT_UNKNOWN)
      .as("the historic typo in this value is part of the wire contract")
      .isEqualTo("unkown");
  }

}
