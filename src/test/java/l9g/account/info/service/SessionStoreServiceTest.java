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

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SessionStoreService}, the map from the OIDC
 * {@code sid} claim to the local HTTP session that OIDC backchannel logout
 * relies on.
 * <p>
 * The eight-hour Caffeine TTL is not covered: the caches are built without an
 * injectable {@code Ticker}, so expiry cannot be simulated without changing
 * {@code src/main}.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class SessionStoreServiceTest
{
  private static final String SID = "oidc-sid-1";

  private SessionStoreService store;

  @BeforeEach
  void setUp()
  {
    store = new SessionStoreService();
  }

  private static HttpSession httpSession(String id)
  {
    HttpSession session = mock(HttpSession.class);
    when(session.getId()).thenReturn(id);
    return session;
  }

  @Test
  @DisplayName("a stored session is retrievable by both the OIDC sid and the session id")
  void sessionIsRetrievableByBothKeys()
  {
    HttpSession session = httpSession("JSESSIONID-1");

    store.put(SID, session);

    assertThat(store.getByOAuth2Sid(SID)).isSameAs(session);
    assertThat(store.getByHttpSessionId("JSESSIONID-1")).isSameAs(session);
  }

  @Test
  @DisplayName("unknown keys yield null")
  void unknownKeysYieldNull()
  {
    assertThat(store.getByOAuth2Sid("nope")).isNull();
    assertThat(store.getByHttpSessionId("nope")).isNull();
  }

  @Test
  @DisplayName("put rejects null arguments with a descriptive message")
  void putRejectsNullArguments()
  {
    assertThatThrownBy(() -> store.put(null, httpSession("JSESSIONID-1")))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("sid must not be null");

    assertThatThrownBy(() -> store.put(SID, null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("session must not be null");
  }

  @Test
  @DisplayName("remove clears both lookup directions")
  void removeClearsBothDirections()
  {
    store.put(SID, httpSession("JSESSIONID-1"));

    store.remove(SID);

    assertThat(store.getByOAuth2Sid(SID)).isNull();
    assertThat(store.getByHttpSessionId("JSESSIONID-1")).isNull();
  }

  @Test
  @DisplayName("removing an unknown sid is a no-op")
  void removingUnknownSidIsNoOp()
  {
    assertThatCode(() -> store.remove("nope")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("backchannel logout invalidates the session and clears both caches")
  void invalidateByOAuth2SidInvalidatesAndClears()
  {
    HttpSession session = httpSession("JSESSIONID-1");
    store.put(SID, session);

    store.invalidateByOAuth2Sid(SID);

    verify(session).invalidate();
    assertThat(store.getByOAuth2Sid(SID)).isNull();
    assertThat(store.getByHttpSessionId("JSESSIONID-1")).isNull();
  }

  @Test
  @DisplayName("an already invalidated session does not break backchannel logout")
  void alreadyInvalidatedSessionIsTolerated()
  {
    HttpSession session = httpSession("JSESSIONID-1");
    doThrow(new IllegalStateException("session already invalidated"))
      .when(session).invalidate();
    store.put(SID, session);

    assertThatCode(() -> store.invalidateByOAuth2Sid(SID))
      .doesNotThrowAnyException();

    assertThat(store.getByOAuth2Sid(SID)).isNull();
    assertThat(store.getByHttpSessionId("JSESSIONID-1")).isNull();
  }

  @Test
  @DisplayName("backchannel logout for an unknown sid touches nothing")
  void invalidateUnknownSidIsNoOp()
  {
    HttpSession session = httpSession("JSESSIONID-1");
    store.put(SID, session);

    store.invalidateByOAuth2Sid("some-other-sid");

    verify(session, never()).invalidate();
    assertThat(store.getByOAuth2Sid(SID)).isSameAs(session);
  }

  @Test
  @DisplayName("invalidateByOAuth2Sid rejects a null sid")
  void invalidateRejectsNullSid()
  {
    assertThatThrownBy(() -> store.invalidateByOAuth2Sid(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("sid must not be null");
  }

  /**
   * Re-registering the same OIDC {@code sid} with a new HTTP session — which
   * happens when a user re-authenticates while the identity provider keeps the
   * sid — no longer orphans the previous session's entry. Before the fix that
   * entry was unreachable ({@code remove} resolves the session through the sid
   * cache, which by then pointed at the new session) and survived until the
   * eight-hour TTL elapsed.
   */
  @Test
  @DisplayName("re-using a sid drops the previous session's entry")
  void reusingSidDropsPreviousSessionEntry()
  {
    HttpSession first = httpSession("JSESSIONID-1");
    HttpSession second = httpSession("JSESSIONID-2");

    store.put(SID, first);
    store.put(SID, second);

    assertThat(store.getByHttpSessionId("JSESSIONID-1"))
      .as("the superseded session must no longer be reachable")
      .isNull();
    assertThat(store.getByHttpSessionId("JSESSIONID-2")).isSameAs(second);
    assertThat(store.getByOAuth2Sid(SID)).isSameAs(second);

    store.remove(SID);

    assertThat(store.getByOAuth2Sid(SID)).isNull();
    assertThat(store.getByHttpSessionId("JSESSIONID-2")).isNull();
    assertThat(store.getByHttpSessionId("JSESSIONID-1")).isNull();
  }

  @Test
  @DisplayName("re-registering the very same session changes nothing")
  void reregisteringTheSameSessionIsIdempotent()
  {
    HttpSession session = httpSession("JSESSIONID-1");

    store.put(SID, session);
    store.put(SID, session);

    assertThat(store.getByOAuth2Sid(SID)).isSameAs(session);
    assertThat(store.getByHttpSessionId("JSESSIONID-1")).isSameAs(session);
  }

  /**
   * A container may refuse {@code getId()} on an already invalidated session.
   * Losing the cleanup is acceptable — the entry expires with the cache TTL —
   * but the exception must never reach the user who is currently logging in.
   */
  @Test
  @DisplayName("a broken previous session does not break the login")
  void brokenPreviousSessionDoesNotBreakLogin()
  {
    HttpSession broken = mock(HttpSession.class);
    when(broken.getId())
      .thenReturn("JSESSIONID-1")
      .thenThrow(new IllegalStateException("session already invalidated"));
    HttpSession fresh = httpSession("JSESSIONID-2");

    store.put(SID, broken);

    assertThatCode(() -> store.put(SID, fresh)).doesNotThrowAnyException();

    assertThat(store.getByOAuth2Sid(SID)).isSameAs(fresh);
    assertThat(store.getByHttpSessionId("JSESSIONID-2")).isSameAs(fresh);
  }

  /**
   * {@code shutdown()} clears the cache references but deliberately does not
   * invalidate the sessions themselves — their lifecycle belongs to the servlet
   * container, and invalidating them here would interfere with a graceful
   * restart. The Javadoc used to claim the opposite; it has been corrected.
   */
  @Test
  @DisplayName("shutdown clears the caches without invalidating the sessions")
  void shutdownClearsCachesWithoutInvalidating()
  {
    HttpSession session = httpSession("JSESSIONID-1");
    store.put(SID, session);

    store.shutdown();

    assertThat(store.getByOAuth2Sid(SID)).isNull();
    assertThat(store.getByHttpSessionId("JSESSIONID-1")).isNull();
    verify(session, never()).invalidate();
  }

}
