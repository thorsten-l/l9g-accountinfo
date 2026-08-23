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
package l9g.account.info.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for storing and managing HTTP sessions, primarily indexed by OAuth2 session ID (sid).
 * This service uses Caffeine caches to maintain a mapping between OAuth2 sids and HTTP sessions,
 * enabling efficient lookup and invalidation of sessions during backchannel logout.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Service
@Slf4j
public final class SessionStoreService
{

  /**
   * Time-to-live for cache entries.
   */
  private static final Duration CACHE_TTL = Duration.ofHours(8);

  /**
   * Cache storing HTTP sessions, indexed by OAuth2 session ID (sid).
   */
  private final Cache<String, HttpSession> byOauth2SidCache;

  /**
   * Cache storing HTTP sessions, indexed by standard HTTP session ID.
   */
  private final Cache<String, HttpSession> byHttpSessionIdCache;

  /**
   * Constructs a new {@code SessionStoreService} and initializes the Caffeine caches
   * with a predefined time-to-live.
   */
  public SessionStoreService()
  {
    this.byOauth2SidCache = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_TTL)
      .build();

    this.byHttpSessionIdCache = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_TTL)
      .build();
  }

  /**
   * Stores an HTTP session, mapping it to both its OAuth2 session ID (sid) and its HTTP session ID.
   * <p>
   * If the sid is already mapped to a different HTTP session — which happens
   * when a user re-authenticates while the identity provider keeps the same
   * sid — that session's entry is dropped from the session-id cache first.
   * Without this, the old entry became unreachable: {@link #remove(String)}
   * resolves the session through the sid cache, which by then points at the new
   * session, so the stale reference survived until the cache TTL elapsed and a
   * backchannel logout left it behind.
   *
   * @param sid The OAuth2 session ID.
   * @param session The {@link HttpSession} object.
   *
   * @throws NullPointerException If sid or session is null.
   */
  public void put(String sid, HttpSession session)
  {
    Objects.requireNonNull(sid, "sid must not be null");
    Objects.requireNonNull(session, "session must not be null");

    evictPreviousSessionFor(sid, session);

    byOauth2SidCache.put(sid, session);
    byHttpSessionIdCache.put(session.getId(), session);
  }

  /**
   * Drops the session-id entry of the session previously registered under this
   * sid, unless it is the very same session being re-registered.
   *
   * @param sid The OAuth2 session ID being (re-)registered.
   * @param session The session now taking that sid.
   */
  private void evictPreviousSessionFor(String sid, HttpSession session)
  {
    HttpSession previous = byOauth2SidCache.getIfPresent(sid);

    if(previous == null || previous == session)
    {
      return;
    }

    try
    {
      String previousId = previous.getId();
      if( ! previousId.equals(session.getId()))
      {
        log.debug("sid {} is re-registered, dropping stale session id {}",
          sid, previousId);
        byHttpSessionIdCache.invalidate(previousId);
      }
    }
    catch(Throwable t)
    {
      // The previous session may already be invalidated and some containers
      // refuse getId() then. Losing the cleanup is harmless — the entry expires
      // with the cache TTL — while letting the exception escape would break the
      // login of the user who is currently authenticating.
      log.debug("could not read the id of the previous session for sid {}: {}",
        sid, t.getMessage());
    }
  }

  /**
   * Retrieves an {@link HttpSession} from the cache using its OAuth2 session ID (sid).
   *
   * @param sid The OAuth2 session ID.
   *
   * @return The {@link HttpSession} associated with the sid, or null if not found.
   */
  public HttpSession getByOAuth2Sid(String sid)
  {
    return byOauth2SidCache.getIfPresent(sid);
  }

  /**
   * Retrieves an {@link HttpSession} from the cache using its standard HTTP session ID.
   *
   * @param sessionId The HTTP session ID.
   *
   * @return The {@link HttpSession} associated with the session ID, or null if not found.
   */
  public HttpSession getByHttpSessionId(String sessionId)
  {
    return byHttpSessionIdCache.getIfPresent(sessionId);
  }

  /**
   * Removes an HTTP session from both caches using its OAuth2 session ID (sid).
   *
   * @param sid The OAuth2 session ID.
   */
  public void remove(String sid)
  {
    var session = byOauth2SidCache.getIfPresent(sid);
    if(session != null)
    {
      byHttpSessionIdCache.invalidate(session.getId());
    }
    byOauth2SidCache.invalidate(sid);
  }

  /**
   * Invalidates an {@link HttpSession} identified by its OAuth2 session ID (sid).
   * This method attempts to invalidate the underlying HTTP session and then removes it from the caches.
   *
   * @param sid The OAuth2 session ID to invalidate.
   *
   * @throws NullPointerException If sid is null.
   */
  public void invalidateByOAuth2Sid(String sid)
  {
    Objects.requireNonNull(sid, "sid must not be null");
    HttpSession session = getByOAuth2Sid(sid);

    if(session != null)
    {
      log.debug("invalidate http session id {}", session.getId());
      try
      {
        session.invalidate();
      }
      catch(Throwable t)
      {
        // is fine
      }
      remove(sid);
    }
  }

  /**
   * Clears both caches when the bean is destroyed.
   * <p>
   * Note that this drops the <em>references</em> only — the cached
   * {@link HttpSession} objects are deliberately <strong>not</strong>
   * invalidated. Their lifecycle belongs to the servlet container, which tears
   * them down itself on shutdown; invalidating them here would interfere with a
   * graceful restart. (An earlier version of this Javadoc claimed the sessions
   * were invalidated, which they never were.)
   */
  @PreDestroy
  public void shutdown()
  {
    byOauth2SidCache.invalidateAll();
    byHttpSessionIdCache.invalidateAll();
    byOauth2SidCache.cleanUp();
    byHttpSessionIdCache.cleanUp();
  }

}
