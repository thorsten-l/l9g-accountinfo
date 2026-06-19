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
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * This endpoint is protected exclusively by a static bearer token
 * ({@code app.storage.api.token}) and accepts the raw file content in the
 * request body.
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
   * @param fileStorageService The service used to persist the received data.
   * @param objectMapper The mapper used to deserialize the request body.
   */
  public StorageController(
    @Value("${app.storage.api.token}") String apiToken,
    @Value("${app.storage.api.id}") String apiId,
    @Value("${app.storage.api.hmac-secret}") String hmacSecret,
    @Value("${app.storage.api.created-by}") String createdBy,
    FileStorageService fileStorageService,
    ObjectMapper objectMapper)
  {
    this.apiToken = apiToken;
    this.apiId = apiId;
    this.hmacSecret = hmacSecret;
    this.createdBy = createdBy;
    this.fileStorageService = fileStorageService;
    this.objectMapper = objectMapper;
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
    authorize(authorization);
    boolean valid = verifySignature(timestamp, signature, body);

    StorageObject object;
    try
    {
      object = objectMapper.readValue(body, StorageObject.class);
    }
    catch(IOException e)
    {
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
    String token = null;
    if(authorization != null && authorization.startsWith("Bearer "))
    {
      token = authorization.substring("Bearer ".length()).trim();
    }

    if(token == null || apiToken == null ||  ! MessageDigest.isEqual(
      token.getBytes(StandardCharsets.UTF_8),
      apiToken.getBytes(StandardCharsets.UTF_8)))
    {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Invalid or missing bearer token");
    }
  }

  /**
   * Verifies the request signature. The sender computes
   * {@code HMAC-SHA256(hmacSecret, "<timestamp>." + body)} and transmits it as
   * {@code X-Signature: sha256=<hex>} together with the {@code X-Timestamp}
   * header. The timestamp is co-signed, binding the signature to the body.
   *
   * @param timestamp The {@code X-Timestamp} header value (Unix epoch seconds).
   * @param signature The {@code X-Signature} header value ({@code sha256=<hex>}).
   * @param body The raw request body that was signed.
   *
   * @throws ResponseStatusException with {@code 401 UNAUTHORIZED} if either
   * header is missing or the signature does not match.
   */
  private boolean verifySignature(String timestamp, String signature, byte[] body)
  {
    boolean valid = false;
    if(timestamp == null || timestamp.isBlank()
      || signature == null || signature.isBlank())
    {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Missing signature headers");
    }

    String provided = signature.startsWith("sha256=")
      ? signature.substring("sha256=".length()) : signature;

    String expected = sign(timestamp, body != null ? body : new byte[0]);

    if( ! MessageDigest.isEqual(
      provided.toLowerCase().getBytes(StandardCharsets.UTF_8),
      expected.getBytes(StandardCharsets.UTF_8)))
    {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Invalid signature");
    }
    else
    {
      valid = true;
    }

    return valid;
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
