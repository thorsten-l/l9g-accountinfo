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

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/signature-pad",
                produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ApiScanController
{
  private final DbService dbService;

  private final PublisherService publisherService;

  /**
   * DTO für den Barcode-Scan-Request
   */
  public static class ScanRequest
  {
    private String cardNumber;

    public String getCardNumber()
    {
      return cardNumber;
    }

    public void setCardNumber(String cardNumber)
    {
      this.cardNumber = cardNumber;
    }

  }

  /**
   * DTO für die Standard-Response
   */
  public static class ApiResponse
  {
    private String status;

    private String message;

    public ApiResponse()
    {
    }

    public ApiResponse(String status, String message)
    {
      this.status = status;
      this.message = message;
    }

    public String getStatus()
    {
      return status;
    }

    public void setStatus(String status)
    {
      this.status = status;
    }

    public String getMessage()
    {
      return message;
    }

    public void setMessage(String message)
    {
      this.message = message;
    }

  }

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

  @PostMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse> uploadPhoto(
    @RequestParam("fullname") String fullname,
    @RequestParam("userid") String userid,
    @RequestParam("mail") String mail,
    @RequestParam("paduuid") String padUuid,
    @RequestParam("side") String side,
    @RequestParam("file") MultipartFile file,
    @AuthenticationPrincipal DefaultOidcUser principal
  ) throws IOException
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
