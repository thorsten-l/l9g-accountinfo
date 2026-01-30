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
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Service
@Slf4j
public final class SessionStoreService
{

  private static final Duration CACHE_TTL = Duration.ofHours(8);

  private final Cache<String, HttpSession> byOauth2SidCache;

  private final Cache<String, HttpSession> byHttpSessionIdCache;

  public SessionStoreService()
  {
    this.byOauth2SidCache = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_TTL)
      .build();

    this.byHttpSessionIdCache = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_TTL)
      .build();
  }

  public void put(String sid, HttpSession session)
  {
    Objects.requireNonNull(sid, "sid must not be null");
    Objects.requireNonNull(session, "session must not be null");

    byOauth2SidCache.put(sid, session);
    byHttpSessionIdCache.put(session.getId(), session);
  }

  public HttpSession getByOAuth2Sid(String sid)
  {
    return byOauth2SidCache.getIfPresent(sid);
  }

  public HttpSession getByHttpSessionId(String sessionId)
  {
    return byHttpSessionIdCache.getIfPresent(sessionId);
  }

  public void remove(String sid)
  {
    var session = byOauth2SidCache.getIfPresent(sid);
    if(session != null)
    {
      byHttpSessionIdCache.invalidate(session.getId());
    }
    byOauth2SidCache.invalidate(sid);
  }

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

  @PreDestroy
  public void shutdown()
  {
    byOauth2SidCache.invalidateAll();
    byHttpSessionIdCache.invalidateAll();
    byOauth2SidCache.cleanUp();
    byHttpSessionIdCache.cleanUp();
  }

}
