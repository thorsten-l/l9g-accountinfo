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
package l9g.account.info.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import l9g.account.info.db.model.SdbSecretData;
import l9g.account.info.db.model.SdbSecretType;
import l9g.account.info.dto.StorageObject;
import l9g.account.info.dto.StorageObject.EndUserData;
import l9g.account.info.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST API controller for receiving externally uploaded storage objects.
 * <p>
 * The endpoint is declared {@code permitAll} and is exempt from CSRF protection,
 * because it is called server-to-server. Its own three checks are therefore the
 * only protection it has:
 * <ol>
 * <li>a static bearer token ({@code app.storage.api.token}), compared in
 * constant time,</li>
 * <li>an HMAC-SHA256 signature over {@code "<timestamp>." + body}
 * ({@code app.storage.api.hmac-secret}), whose timestamp must be fresh within
 * {@code app.storage.api.timestamp-tolerance},</li>
 * <li>single use of that signature while its timestamp could still pass the
 * freshness check ({@code app.storage.api.replay-protection}).</li>
 * </ol>
 * Only {@code EXT_IDENTIFICATION_STATUS} and {@code EXT_IDENTIFICATION_ARCHIVE}
 * are accepted as object types.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/storage")
@Tag(name = "Storage API",
     description = "API for receiving externally uploaded storage objects")
public class StorageController
{
  /**
   * The set of secret types this endpoint is allowed to accept.
   */
  private static final Set<SdbSecretType> ALLOWED_TYPES = EnumSet.of(
    SdbSecretType.EXT_IDENTIFICATION_STATUS,
    SdbSecretType.EXT_IDENTIFICATION_ARCHIVE);

  /**
   * The HMAC algorithm used to verify the request signature.
   */
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  /**
   * The decrypted bearer token required to access this endpoint.
   */
  private final String apiToken;

  /**
   * The configured storage API id, logged for each received object.
   */
  private final String apiId;

  /**
   * The decrypted HMAC secret used to verify the {@code X-Signature} header.
   */
  private final String hmacSecret;

  /**
   * How far the {@code X-Timestamp} header may deviate from the current time in
   * either direction ({@code app.storage.api.timestamp-tolerance}). Requests
   * outside this window are rejected, which bounds how long an intercepted
   * request stays usable.
   */
  private final Duration timestampTolerance;

  /**
   * Signatures already accepted, held for as long as their timestamp could
   * still pass the freshness check, so that every signature is single-use.
   * {@code null} when {@code app.storage.api.replay-protection} is disabled.
   */
  private final Cache<String, Boolean> seenSignatures;

  /**
   * The publisher (JSON) recorded as the creator of stored objects
   * ({@code app.storage.api.created-by}).
   */
  private final String createdBy;

  /**
   * Service used to persist the received raw object data.
   */
  private final FileStorageService fileStorageService;

  /**
   * Mapper used to deserialize the JSON request body into a
   * {@link StorageObject}.
   */
  private final ObjectMapper objectMapper;

  /**
   * Constructs a {@code StorageController}.
   *
   * @param apiToken The bearer token protecting this endpoint
   * ({@code app.storage.api.token}).
   * @param apiId The storage API id ({@code app.storage.api.id}).
   * @param hmacSecret The HMAC secret used to verify the request signature
   * ({@code app.storage.api.hmac-secret}).
   * @param createdBy The publisher JSON recorded as creator
   * ({@code app.storage.api.created-by}).
   * @param timestampTolerance How far {@code X-Timestamp} may deviate from the
   * current time in either direction
   * ({@code app.storage.api.timestamp-tolerance}, default 5 minutes).
   * @param replayProtection Whether an accepted signature is remembered and
   * refused on a second use ({@code app.storage.api.replay-protection},
   * default {@code true}).
   * @param fileStorageService The service used to persist the received data.
   * @param objectMapper The mapper used to deserialize the request body.
   */
  public StorageController(
    @Value("${app.storage.api.token}") String apiToken,
    @Value("${app.storage.api.id}") String apiId,
    @Value("${app.storage.api.hmac-secret}") String hmacSecret,
    @Value("${app.storage.api.created-by}") String createdBy,
    @Value("${app.storage.api.timestamp-tolerance:5m}") Duration timestampTolerance,
    @Value("${app.storage.api.replay-protection:true}") boolean replayProtection,
    FileStorageService fileStorageService,
    ObjectMapper objectMapper)
  {
    this.apiToken = apiToken;
    this.apiId = apiId;
    this.hmacSecret = hmacSecret;
    this.createdBy = createdBy;
    this.timestampTolerance = timestampTolerance;
    this.fileStorageService = fileStorageService;
    this.objectMapper = objectMapper;

    // A signature stays acceptable for up to twice the tolerance (from the
    // earliest to the latest moment its timestamp passes the freshness check),
    // so entries have to outlive that span to actually prevent a replay.
    this.seenSignatures = replayProtection
      ? Caffeine.newBuilder()
        .expireAfterWrite(timestampTolerance.multipliedBy(2).plusMinutes(1))
        .maximumSize(100_000)
        .build()
      : null;

    log.debug("storage api: timestampTolerance={}s, replayProtection={}",
      timestampTolerance.toSeconds(), replayProtection);
  }

  /**
   * Receives a single storage object as the raw (JSON) request body. The body
   * is deserialized into a {@link StorageObject}; only the types
   * {@code EXT_IDENTIFICATION_STATUS} and {@code EXT_IDENTIFICATION_ARCHIVE}
   * are accepted.
   *
   * @param authorization The {@code Authorization} header carrying the bearer token.
   * @param timestamp The {@code X-Timestamp} header co-signed with the body.
   * @param signature The {@code X-Signature} header ({@code sha256=<hex>}).
   * @param body The raw request body holding the serialized {@link StorageObject}.
   *
   * @return A {@code 200 OK} response once the object has been accepted.
   */
  @PostMapping(path = "/objects",
               consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> receiveObject(
    @RequestHeader(name = "Authorization", required = false) String authorization,
    @RequestHeader(name = "X-Timestamp", required = false) String timestamp,
    @RequestHeader(name = "X-Signature", required = false) String signature,
    @RequestBody byte[] body)
  {
    log.debug("receiveObject body.length = {}", body.length);
    authorize(authorization);
    boolean valid = verifySignature(timestamp, signature, body);

    StorageObject object;
    try
    {
      object = objectMapper.readValue(body, StorageObject.class);
    }
    catch(IOException e)
    {
      log.error("Malformed storage object");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "Malformed storage object", e);
    }

    SdbSecretType type = object.type();
    if(type == null ||  ! ALLOWED_TYPES.contains(type))
    {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "Unsupported type: " + type);
    }

    EndUserData user = object.user();
    if(user == null)
    {
      // Rejected here rather than further down: FileStorageService.buildSecretData
      // dereferences object.user() unconditionally, so such a body could never
      // be stored — it only produced a misleading 500 instead of naming the
      // client error.
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "Missing user data");
    }

    byte[] file = object.data();      // ZIP bei ARCHIVE; null bei STATUS
    int length = file != null ? file.length : 0;

    log.debug("storage object received: type={}, user={}, "
      + "length={} bytes, id={}", type, user, length, apiId);

    log.trace("storage objects header received: timstamp={}, signature={}", timestamp, signature);
    log.debug("signature valid={}", valid);

    try
    {
      SdbSecretData data;
      if(type == SdbSecretType.EXT_IDENTIFICATION_ARCHIVE)
      {
        data = fileStorageService.saveSecretRawData(createdBy, apiId, object);
      }
      else
      {
        data = fileStorageService.saveSecretData(createdBy, apiId, object);
      }
      log.info("STORAGE_UPLOAD: type={}, username={}, length={} bytes, id={}",
        type, user.username(), length, data.getId());
    }
    catch(Exception e)
    {
      log.error("Failed to store object, type={}, user={}", type, user, e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
        "Failed to store object");
    }

    return ResponseEntity.ok().build();
  }

  /**
   * Validates the bearer token from the {@code Authorization} header against
   * the configured token using a constant-time comparison.
   *
   * @param authorization The {@code Authorization} header value.
   *
   * @throws ResponseStatusException with {@code 401 UNAUTHORIZED} if the token
   * is missing or invalid.
   */
  private void authorize(String authorization)
  {
    log.debug("authorize");
    String token = null;
    if(authorization != null && authorization.startsWith("Bearer "))
    {
      token = authorization.substring("Bearer ".length()).trim();
    }

    if(token == null || apiToken == null ||  ! MessageDigest.isEqual(
      token.getBytes(StandardCharsets.UTF_8),
      apiToken.getBytes(StandardCharsets.UTF_8)))
    {
      log.error("Invalid or missing bearer token");
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Invalid or missing bearer token");
    }
  }

  /**
   * Verifies the request signature. The sender computes
   * {@code HMAC-SHA256(hmacSecret, "<timestamp>." + body)} and transmits it as
   * {@code X-Signature: sha256=<hex>} together with the {@code X-Timestamp}
   * header. The timestamp is co-signed, binding the signature to the body.
   * <p>
   * Because this endpoint is reachable without a session and is exempt from CSRF
   * protection, the signature is the only thing standing between an intercepted
   * request and an unlimited number of replays. Three checks are therefore
   * applied, in this order:
   * <ol>
   * <li>the timestamp must be numeric and within
   * {@code app.storage.api.timestamp-tolerance} of the current time,</li>
   * <li>the HMAC must match, compared in constant time,</li>
   * <li>the signature must not have been accepted before (see
   * {@link #seenSignatures}).</li>
   * </ol>
   * The freshness check runs before the HMAC so that a stale request is rejected
   * without spending a MAC computation; the replay check runs last so that only
   * signatures which actually verified can ever enter the cache.
   *
   * @param timestamp The {@code X-Timestamp} header value, Unix epoch seconds or
   * milliseconds.
   * @param signature The {@code X-Signature} header value ({@code sha256=<hex>}).
   * @param body The raw request body that was signed.
   *
   * @return {@code true} once the signature has been accepted.
   *
   * @throws ResponseStatusException with {@code 401 UNAUTHORIZED} if a header is
   * missing, the timestamp is unusable or stale, the signature does not match,
   * or the signature has already been used.
   */
  private boolean verifySignature(String timestamp, String signature, byte[] body)
  {
    log.debug("verifySignature");
    boolean valid = false;
    if(timestamp == null || timestamp.isBlank()
      || signature == null || signature.isBlank())
    {
      log.error("Missing signature headers");
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Missing signature headers");
    }

    checkTimestampFreshness(timestamp);

    String provided = signature.startsWith("sha256=")
      ? signature.substring("sha256=".length()) : signature;

    String expected = sign(timestamp, body != null ? body : new byte[0]);

    if( ! MessageDigest.isEqual(
      provided.toLowerCase().getBytes(StandardCharsets.UTF_8),
      expected.getBytes(StandardCharsets.UTF_8)))
    {
      log.error("Invalid signature");
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Invalid signature");
    }
    else
    {
      checkNotReplayed(expected);
      valid = true;
    }

    return valid;
  }

  /**
   * Rejects a timestamp that cannot be parsed or that deviates from the current
   * time by more than the configured tolerance. Without this check any captured
   * request could be replayed forever.
   *
   * @param timestamp The {@code X-Timestamp} header value.
   *
   * @throws ResponseStatusException with {@code 401 UNAUTHORIZED} if the
   * timestamp is not numeric or lies outside the accepted window.
   */
  private void checkTimestampFreshness(String timestamp)
  {
    long epochMillis;

    try
    {
      epochMillis = toEpochMillis(Long.parseLong(timestamp.trim()));
    }
    catch(NumberFormatException e)
    {
      log.error("Invalid signature timestamp, not a number");
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Invalid signature timestamp");
    }

    long skewMillis = Math.abs(System.currentTimeMillis() - epochMillis);

    if(skewMillis > timestampTolerance.toMillis())
    {
      log.error("Signature timestamp outside tolerance, skew={}s, "
        + "tolerance={}s", skewMillis / 1000, timestampTolerance.toSeconds());
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Signature timestamp outside the accepted window");
    }
  }

  /**
   * Normalises a timestamp to epoch milliseconds, accepting both seconds and
   * milliseconds. Epoch seconds stay below 100,000,000,000 until the year 5138,
   * so any larger value can only be milliseconds. Both units are accepted
   * because the senders of this API were never held to one of them.
   *
   * @param timestamp The raw numeric timestamp.
   *
   * @return The timestamp in epoch milliseconds.
   */
  private static long toEpochMillis(long timestamp)
  {
    return Math.abs(timestamp) < 100_000_000_000L
      ? timestamp * 1000L : timestamp;
  }

  /**
   * Records a verified signature and rejects it if it was seen before, making
   * every signature single-use for as long as its timestamp could still be
   * considered fresh.
   * <p>
   * The cache is per instance. Behind a load balancer with more than one
   * replica, a replay could still succeed on a different instance within the
   * tolerance window; the window itself remains the hard bound in that setup.
   *
   * @param expectedSignature The verified signature, in lowercase hex.
   *
   * @throws ResponseStatusException with {@code 401 UNAUTHORIZED} if this
   * signature has already been accepted.
   */
  private void checkNotReplayed(String expectedSignature)
  {
    if(seenSignatures == null)
    {
      return;
    }

    if(seenSignatures.asMap()
      .putIfAbsent(expectedSignature, Boolean.TRUE) != null)
    {
      log.error("Signature replayed, request rejected");
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Signature has already been used");
    }
  }

  /**
   * Computes {@code HMAC-SHA256(hmacSecret, "<timestamp>." + body)} and returns
   * it as a lowercase hex string.
   *
   * @param timestamp The timestamp co-signed with the body.
   * @param body The raw body bytes.
   *
   * @return The lowercase hex-encoded HMAC.
   */
  private String sign(String timestamp, byte[] body)
  {
    try
    {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(
        hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
      mac.update(body);
      return HexFormat.of().formatHex(mac.doFinal());
    }
    catch(Exception e)
    {
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR, "Failed to compute HMAC signature", e);
    }
  }

}
