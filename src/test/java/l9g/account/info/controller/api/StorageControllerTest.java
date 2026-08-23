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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import l9g.account.info.db.model.SdbSecretData;
import l9g.account.info.db.model.SdbSecretType;
import l9g.account.info.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StorageController}.
 * <p>
 * This endpoint carries the highest risk in the application: {@code /api/v1/storage/**}
 * is declared {@code permitAll} and CSRF-exempt in {@code ClientSecurityConfig},
 * so the bearer-token check and the HMAC signature check inside this controller
 * are the only protection it has.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class StorageControllerTest
{
  private static final String API_TOKEN = "s3cr3t-bearer-token";

  private static final String API_ID = "storage-api-1";

  private static final String HMAC_SECRET = "hmac-shared-secret";

  private static final String CREATED_BY = "{\"username\":\"storage-api\"}";

  private static final String STATUS_BODY =
    "{\"type\":\"EXT_IDENTIFICATION_STATUS\","
    + "\"user\":{\"pid\":\"p1\",\"mail\":\"a@b.c\",\"name\":\"A B\","
    + "\"username\":\"abuser\",\"givenName\":\"A\",\"surname\":\"B\"},"
    + "\"status\":{\"success\":true}}";

  /**
   * Freshness tolerance used by the controller under test.
   */
  private static final Duration TOLERANCE = Duration.ofMinutes(5);

  private FileStorageService fileStorageService;

  private StorageController controller;

  @BeforeEach
  void setUp()
  {
    fileStorageService = mock(FileStorageService.class);
    controller = newController(TOLERANCE, true);
  }

  /**
   * Creates a controller with an explicit freshness tolerance and replay
   * setting.
   *
   * @param tolerance The accepted timestamp deviation.
   * @param replayProtection Whether a signature may only be used once.
   *
   * @return The controller under test.
   */
  private StorageController newController(Duration tolerance,
    boolean replayProtection)
  {
    return new StorageController(API_TOKEN, API_ID, HMAC_SECRET, CREATED_BY,
      tolerance, replayProtection, fileStorageService, new ObjectMapper());
  }

  /**
   * Reimplements {@code StorageController.sign}: HMAC-SHA256 over
   * {@code timestamp + "." + body}, rendered as lowercase hex.
   *
   * @param secret The shared HMAC secret.
   * @param timestamp The value of the {@code X-Timestamp} header.
   * @param body The raw request body.
   *
   * @return The expected signature as lowercase hex.
   *
   * @throws Exception If the MAC cannot be computed.
   */
  private static String sign(String secret, String timestamp, byte[] body)
    throws Exception
  {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
      "HmacSHA256"));
    mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
    mac.update(body);
    return HexFormat.of().formatHex(mac.doFinal());
  }

  private static String bearer()
  {
    return "Bearer " + API_TOKEN;
  }

  /**
   * @return The current time as Unix epoch seconds.
   */
  private static String nowSeconds()
  {
    return String.valueOf(Instant.now().getEpochSecond());
  }

  /**
   * @param offset Offset applied to the current time.
   *
   * @return The shifted time as Unix epoch seconds.
   */
  private static String secondsAt(Duration offset)
  {
    return String.valueOf(Instant.now().plus(offset).getEpochSecond());
  }

  private void storageAccepts()
    throws Exception
  {
    when(fileStorageService.saveSecretData(any(), any(), any()))
      .thenReturn(new SdbSecretData("pub", API_ID,
        SdbSecretType.EXT_IDENTIFICATION_STATUS));
  }

  // ---------------------------------------------------------------- bearer

  @Test
  @DisplayName("missing Authorization header is rejected with 401")
  void missingAuthorizationIsUnauthorized()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();

    assertThatThrownBy(() -> controller.receiveObject(
      null, ts, sign(HMAC_SECRET, ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.UNAUTHORIZED);

    verify(fileStorageService, never()).saveSecretData(any(), any(), any());
  }

  @Test
  @DisplayName("the 'Bearer ' prefix is case-sensitive")
  void lowercaseBearerPrefixIsUnauthorized()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();

    assertThatThrownBy(() -> controller.receiveObject(
      "bearer " + API_TOKEN, ts, sign(HMAC_SECRET, ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("a wrong bearer token is rejected with 401")
  void wrongTokenIsUnauthorized()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();

    assertThatThrownBy(() -> controller.receiveObject(
      "Bearer not-the-token", ts, sign(HMAC_SECRET, ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("trailing whitespace in the bearer token is trimmed and accepted")
  void bearerTokenIsTrimmed()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();
    when(fileStorageService.saveSecretData(any(), any(), any()))
      .thenReturn(new SdbSecretData("pub", API_ID,
        SdbSecretType.EXT_IDENTIFICATION_STATUS));

    ResponseEntity<Void> response = controller.receiveObject(
      "Bearer " + API_TOKEN + "   ", ts, sign(HMAC_SECRET, ts, body), body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  // ------------------------------------------------------------- signature

  @Test
  @DisplayName("missing X-Timestamp / X-Signature headers are rejected with 401")
  void missingSignatureHeadersAreUnauthorized()
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(
      () -> controller.receiveObject(bearer(), null, "abc", body))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.UNAUTHORIZED);

    assertThatThrownBy(
      () -> controller.receiveObject(bearer(), nowSeconds(), "  ", body))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("a tampered body invalidates the signature")
  void tamperedBodyIsUnauthorized()
    throws Exception
  {
    byte[] original = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();
    String signature = sign(HMAC_SECRET, ts, original);
    byte[] tampered = STATUS_BODY.replace("abuser", "abusfr")
      .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(
      () -> controller.receiveObject(bearer(), ts, signature, tampered))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("a signature computed with the wrong secret is rejected")
  void wrongSecretIsUnauthorized()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();

    assertThatThrownBy(() -> controller.receiveObject(
      bearer(), ts, sign("other-secret", ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("the signature is accepted bare, with sha256= prefix and in uppercase hex")
  void signatureFormatsAccepted()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();
    String hex = sign(HMAC_SECRET, ts, body);
    storageAccepts();
    // the same signature is presented three times here, so replay protection
    // is disabled: this test is about the accepted encodings, not about reuse
    StorageController formats = newController(TOLERANCE, false);

    assertThat(formats.receiveObject(bearer(), ts, hex, body)
      .getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(formats.receiveObject(bearer(), ts, "sha256=" + hex, body)
      .getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(formats
      .receiveObject(bearer(), ts, hex.toUpperCase(), body)
      .getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * The core of the replay fix: a signature that verified once must not verify
   * again. Before the fix a captured timestamp/signature pair could be replayed
   * an unlimited number of times, each replay storing the object again.
   */
  @Test
  @DisplayName("a replayed signature is rejected with 401")
  void replayedSignatureIsRejected()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();
    String signature = sign(HMAC_SECRET, ts, body);
    storageAccepts();

    assertThat(controller.receiveObject(bearer(), ts, signature, body)
      .getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThatThrownBy(
      () -> controller.receiveObject(bearer(), ts, signature, body))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("already been used")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.UNAUTHORIZED);

    verify(fileStorageService, org.mockito.Mockito.times(1))
      .saveSecretData(any(), any(), any());
  }

  @Test
  @DisplayName("the sha256= prefix does not circumvent replay protection")
  void replayWithDifferentEncodingIsRejected()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();
    String signature = sign(HMAC_SECRET, ts, body);
    storageAccepts();

    controller.receiveObject(bearer(), ts, signature, body);

    assertThatThrownBy(() -> controller.receiveObject(
      bearer(), ts, "sha256=" + signature.toUpperCase(), body))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("already been used");
  }

  @Test
  @DisplayName("an ancient timestamp is rejected with 401")
  void ancientTimestampIsRejected()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ancient = "1";

    assertThatThrownBy(() -> controller.receiveObject(
      bearer(), ancient, sign(HMAC_SECRET, ancient, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("outside the accepted window")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.UNAUTHORIZED);

    verify(fileStorageService, never()).saveSecretData(any(), any(), any());
  }

  @Test
  @DisplayName("a timestamp just outside the tolerance is rejected in both directions")
  void timestampOutsideToleranceIsRejected()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);

    for(Duration offset : java.util.List.of(
      TOLERANCE.plusSeconds(30).negated(), TOLERANCE.plusSeconds(30)))
    {
      String ts = secondsAt(offset);
      assertThatThrownBy(() -> controller.receiveObject(
        bearer(), ts, sign(HMAC_SECRET, ts, body), body))
        .as("offset %s must be rejected", offset)
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("outside the accepted window");
    }
  }

  @Test
  @DisplayName("a timestamp inside the tolerance is accepted in both directions")
  void timestampInsideToleranceIsAccepted()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    storageAccepts();

    for(Duration offset : java.util.List.of(
      TOLERANCE.minusSeconds(30).negated(), TOLERANCE.minusSeconds(30)))
    {
      String ts = secondsAt(offset);
      assertThat(controller
        .receiveObject(bearer(), ts, sign(HMAC_SECRET, ts, body), body)
        .getStatusCode())
        .as("offset %s must be accepted", offset)
        .isEqualTo(HttpStatus.OK);
    }
  }

  @Test
  @DisplayName("a non-numeric X-Timestamp is rejected with 401")
  void nonNumericTimestampIsRejected()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = "not-a-timestamp";

    assertThatThrownBy(() -> controller.receiveObject(
      bearer(), ts, sign(HMAC_SECRET, ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("Invalid signature timestamp")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.UNAUTHORIZED);

    verify(fileStorageService, never()).saveSecretData(any(), any(), any());
  }

  /**
   * The senders of this API were never held to one timestamp unit, and the
   * header was previously not parsed at all, so both epoch seconds and epoch
   * milliseconds have to keep working.
   */
  @Test
  @DisplayName("epoch milliseconds are accepted as well as epoch seconds")
  void epochMillisecondsAreAccepted()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = String.valueOf(Instant.now().toEpochMilli());
    storageAccepts();

    assertThat(controller
      .receiveObject(bearer(), ts, sign(HMAC_SECRET, ts, body), body)
      .getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("with replay protection disabled a signature may be reused, the window still applies")
  void replayProtectionCanBeDisabled()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();
    String signature = sign(HMAC_SECRET, ts, body);
    storageAccepts();
    StorageController lenient = newController(TOLERANCE, false);

    assertThat(lenient.receiveObject(bearer(), ts, signature, body)
      .getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(lenient.receiveObject(bearer(), ts, signature, body)
      .getStatusCode()).isEqualTo(HttpStatus.OK);

    String ancient = "1";
    assertThatThrownBy(() -> lenient.receiveObject(
      bearer(), ancient, sign(HMAC_SECRET, ancient, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("outside the accepted window");
  }

  // ------------------------------------------------------------------ body

  @Test
  @DisplayName("a malformed JSON body is rejected with 400")
  void malformedBodyIsBadRequest()
    throws Exception
  {
    byte[] body = "{ this is not json".getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();

    assertThatThrownBy(() -> controller.receiveObject(
      bearer(), ts, sign(HMAC_SECRET, ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("a type outside ALLOWED_TYPES is rejected with 400")
  void unsupportedTypeIsBadRequest()
    throws Exception
  {
    byte[] body = "{\"type\":\"ID_FRONT_IMAGE\"}"
      .getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();

    assertThatThrownBy(() -> controller.receiveObject(
      bearer(), ts, sign(HMAC_SECRET, ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("Unsupported type")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(fileStorageService, never()).saveSecretData(any(), any(), any());
    verify(fileStorageService, never()).saveSecretRawData(any(), any(), any());
  }

  @Test
  @DisplayName("a body without a type is rejected with 400")
  void missingTypeIsBadRequest()
    throws Exception
  {
    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();

    assertThatThrownBy(() -> controller.receiveObject(
      bearer(), ts, sign(HMAC_SECRET, ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ------------------------------------------------------------ delegation

  @Test
  @DisplayName("EXT_IDENTIFICATION_STATUS is routed to saveSecretData")
  void statusTypeGoesToSaveSecretData()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();
    when(fileStorageService.saveSecretData(any(), any(), any()))
      .thenReturn(new SdbSecretData("pub", API_ID,
        SdbSecretType.EXT_IDENTIFICATION_STATUS));

    ResponseEntity<Void> response = controller.receiveObject(
      bearer(), ts, sign(HMAC_SECRET, ts, body), body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(fileStorageService).saveSecretData(eq(CREATED_BY), eq(API_ID), any());
    verify(fileStorageService, never()).saveSecretRawData(any(), any(), any());
  }

  @Test
  @DisplayName("EXT_IDENTIFICATION_ARCHIVE is routed to saveSecretRawData")
  void archiveTypeGoesToSaveSecretRawData()
    throws Exception
  {
    byte[] body = ("{\"type\":\"EXT_IDENTIFICATION_ARCHIVE\","
      + "\"user\":{\"username\":\"abuser\"},"
      + "\"data\":\"" + java.util.Base64.getEncoder()
        .encodeToString("ZIP".getBytes(StandardCharsets.UTF_8)) + "\"}")
      .getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();
    when(fileStorageService.saveSecretRawData(any(), any(), any()))
      .thenReturn(new SdbSecretData("pub", API_ID,
        SdbSecretType.EXT_IDENTIFICATION_ARCHIVE));

    ResponseEntity<Void> response = controller.receiveObject(
      bearer(), ts, sign(HMAC_SECRET, ts, body), body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(fileStorageService)
      .saveSecretRawData(eq(CREATED_BY), eq(API_ID), any());
    verify(fileStorageService, never()).saveSecretData(any(), any(), any());
  }

  @Test
  @DisplayName("a storage failure is reported as 500")
  void storageFailureIsInternalServerError()
    throws Exception
  {
    byte[] body = STATUS_BODY.getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();
    when(fileStorageService.saveSecretData(any(), any(), any()))
      .thenThrow(new java.io.IOException("disk on fire"));

    assertThatThrownBy(() -> controller.receiveObject(
      bearer(), ts, sign(HMAC_SECRET, ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("Failed to store object")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * A body with an allowed type but no {@code user} object is now a client
   * error. It could never be stored anyway —
   * {@code FileStorageService.buildSecretData} dereferences
   * {@code object.user()} unconditionally — so the previous {@code 500} only
   * hid which side was at fault.
   */
  @Test
  @DisplayName("a missing user object is rejected with 400 before anything is stored")
  void missingUserIsBadRequest()
    throws Exception
  {
    byte[] body = "{\"type\":\"EXT_IDENTIFICATION_STATUS\",\"status\":{}}"
      .getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();

    assertThatThrownBy(() -> controller.receiveObject(
      bearer(), ts, sign(HMAC_SECRET, ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .hasMessageContaining("Missing user data")
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(fileStorageService, never()).saveSecretData(any(), any(), any());
    verify(fileStorageService, never()).saveSecretRawData(any(), any(), any());
  }

  @Test
  @DisplayName("an archive body without a user object is rejected as well")
  void missingUserOnArchiveIsBadRequest()
    throws Exception
  {
    byte[] body = "{\"type\":\"EXT_IDENTIFICATION_ARCHIVE\"}"
      .getBytes(StandardCharsets.UTF_8);
    String ts = nowSeconds();

    assertThatThrownBy(() -> controller.receiveObject(
      bearer(), ts, sign(HMAC_SECRET, ts, body), body))
      .isInstanceOf(ResponseStatusException.class)
      .extracting(e -> ((ResponseStatusException)e).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(fileStorageService, never()).saveSecretRawData(any(), any(), any());
  }

}
