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
package l9g.account.info.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import l9g.account.info.db.DbService;
import l9g.account.info.service.PublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST controller for handling scan-related API requests, 
 * specifically for card scanning and photo uploads.
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/signature-pad",
                produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Scan API", description = "Endpoints for handling card scans and photo uploads")
public class ApiScanController
{
  /**
   * Database service for accessing and managing stored data.
   */
  private final DbService dbService;

  /**
   * Service for publishing events or messages.
   */
  private final PublisherService publisherService;

  /**
   * Data Transfer Object (DTO) for barcode scan requests.
   */
  public static class ScanRequest
  {
    /**
     * The card number obtained from the scan.
     */
    private String cardNumber;

    /**
     * Returns the card number.
     *
     * @return The card number.
     */
    public String getCardNumber()
    {
      return cardNumber;
    }

    /**
     * Sets the card number.
     *
     * @param cardNumber The card number to set.
     */
    public void setCardNumber(String cardNumber)
    {
      this.cardNumber = cardNumber;
    }

  }

  /**
   * Generic API response DTO for status and messages.
   */
  @Data
  @NoArgsConstructor
  public static class ApiResponse
  {
    /**
     * The status of the API response (e.g., "OK", "ERROR").
     */
    private String status;

    /**
     * A descriptive message related to the API response.
     */
    private String message;

    /**
     * Constructs an ApiResponse with a specified status and message.
     *
     * @param status The status of the response.
     * @param message The message of the response.
     */
    public ApiResponse(String status, String message)
    {
      this.status = status;
      this.message = message;
    }

  }

  /**
   * Processes a card scan request, validating the card number.
   *
   * @param request The {@link ScanRequest} containing the card number.
   * @param principal The authenticated OIDC user.
   *
   * @return A {@link ResponseEntity} with an {@link ApiResponse} indicating success or failure.
   */
  @Operation(summary = "Process card scan",
             description = "Receives a card number from a scan request and validates it. Currently hardcoded for testing."
  )
  @PostMapping(path = "/scan", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiResponse> scanCard(@RequestBody ScanRequest request,
    @AuthenticationPrincipal DefaultOidcUser principal)
  {
    String cardNumber = request.getCardNumber();
    log.debug("Gescannte Kartennummer: '{}'", cardNumber);
    log.debug("principal={}", principal);

    if(cardNumber == null ||  ! cardNumber.equals("091600045759"))
    {
      log.debug("Card not found");
      return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(new ApiResponse("ERROR", "Card not found"));
    }

    log.debug("OK - Card number found");
    return ResponseEntity
      .ok(new ApiResponse("OK", "Kartennummer erhalten"));
  }

  /**
   * Uploads a photo associated with a user and signature pad.
   * This endpoint receives multipart file data along with user and signature pad details.
   *
   * @param fullname The full name of the user.
   * @param userid The user ID.
   * @param mail The user's email address.
   * @param padUuid The UUID of the signature pad.
   * @param side The side of the card being photographed (e.g., "front", "back").
   * @param file The {@link MultipartFile} containing the photo.
   * @param principal The authenticated OIDC user.
   *
   * @return A {@link ResponseEntity} with an {@link ApiResponse} indicating success.
   *
   * @throws IOException If an I/O error occurs during file processing.
   */
  @Operation(summary = "Upload photo for a user/signature pad",
             description = "Accepts a multipart file along with user and signature pad details for storage."
  )
  @PostMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse> uploadPhoto(
    @RequestParam("fullname") String fullname,
    @RequestParam("userid") String userid,
    @RequestParam("mail") String mail,
    @RequestParam("paduuid") String padUuid,
    @RequestParam("side") String side,
    @RequestParam("file") MultipartFile file,
    @AuthenticationPrincipal DefaultOidcUser principal
  )
    throws IOException
  {
    log.debug("principal={}", principal);
    log.debug("fullname={}, userid={}, mail={}", fullname, userid, mail);
    // TODO: hier Foto und cardNumber + side verarbeiten (z.B. abspeichern)
    String filename = file.getOriginalFilename();
    long size = file.getSize();
    System.out.printf("padUuid=%s, Seite=%s, Datei=%s (%d Bytes)%n",
      padUuid, side, filename, size);

    dbService.saveSecretFileData(
      publisherService.principalToPublisherJSON(principal),
      fullname, userid, mail, padUuid, side, file);

    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(new ApiResponse("OK", "Foto " + side + " hochgeladen"));
  }

}
