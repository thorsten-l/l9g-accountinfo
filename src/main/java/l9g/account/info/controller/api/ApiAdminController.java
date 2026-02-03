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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import l9g.account.info.db.DbService;
import l9g.account.info.db.model.SdbSecretData;
import l9g.account.info.db.model.SdbSecretType;
import l9g.account.info.service.SignaturePad;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for administrative API endpoints related to 
 * account and signature pad information.
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin API", description = "Admin API for managing account and signature pad data")
public class ApiAdminController
{
  /**
   * Database service for accessing and managing stored data.
   */
  private final DbService dbService;

  /**
   * Service for authentication and authorization checks.
   */
  private final AuthService authService;

  /**
   * Object mapper for JSON serialization/deserialization.
   */
  private final ObjectMapper objectMapper;

  /**
   * Retrieves a photo from the database by its ID.
   * Requires administrator privileges.
   *
   * @param dbId The database ID of the photo.
   * @param principal The authenticated OIDC user, used for authorization.
   *
   * @return A {@link ResponseEntity} containing the photo as a byte array with content type `image/jpeg`.
   *
   * @throws IOException If an I/O error occurs during photo retrieval.
   */
  @Operation(summary = "Retrieve photo by database ID",
             description = "Fetches a JPEG photo associated with a given database ID. Requires ADMIN role.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "Photo successfully retrieved"),
               @ApiResponse(responseCode = "403", description = "Access denied"),
               @ApiResponse(responseCode = "404", description = "Photo not found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @GetMapping(path = "/id.jpeg", produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<byte[]> photoByDbId(
    @RequestParam("id") String dbId,
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws IOException
  {
    log.debug("dbId={}", dbId);
    log.debug("principal={}", principal);
    return ResponseEntity.ok(dbService.findFileDataById(dbId));
  }

  /**
   * Retrieves a signature image in PNG format from the database by its ID.
   * This endpoint processes a JWT stored in the database to extract the PNG signature.
   * Requires administrator privileges.
   *
   * @param dbId The database ID of the signature data.
   * @param principal The authenticated OIDC user, used for authorization.
   *
   * @return A {@link ResponseEntity} containing the signature image as a byte array with content type `image/png`.
   *
   * @throws IOException If an I/O error occurs during data retrieval.
   * @throws ParseException If the JWT cannot be parsed.
   */
  @Operation(summary = "Retrieve signature as PNG by database ID",
             description = "Fetches a PNG signature image associated with a given database ID by extracting it from a stored JWT. Requires ADMIN role.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "Signature image successfully retrieved"),
               @ApiResponse(responseCode = "403", description = "Access denied"),
               @ApiResponse(responseCode = "404", description = "Signature not found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @GetMapping(path = "/signature.png", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> signaturePngByDbId(
    @RequestParam("id") String dbId,
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws IOException, ParseException
  {
    log.debug("dbId={}", dbId);
    log.debug("principal={}", principal);

    SdbSecretData secretData = dbService.findSdbSecretDataById(dbId);

    if(secretData != null && secretData.getType() == SdbSecretType.ID_SIGNATURE_JWT)
    {
      SignaturePad signaturePad = authService.authCheck(secretData.getKey(), false);
      SignedJWT signedJWT = authService.verifyJwt(signaturePad, secretData.getSecret());
      String sigpngBase64 = signedJWT.getJWTClaimsSet().getClaimAsString("sigpng");
      return ResponseEntity.ok(Base64.getDecoder().decode(sigpngBase64));
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Retrieves a signature image in SVG format from the database by its ID.
   * This endpoint processes a JWT stored in the database to extract the SVG signature.
   * Requires administrator privileges.
   *
   * @param dbId The database ID of the signature data.
   * @param principal The authenticated OIDC user, used for authorization.
   *
   * @return A {@link ResponseEntity} containing the signature image as a byte array with content type `image/svg+xml`.
   *
   * @throws IOException If an I/O error occurs during data retrieval.
   * @throws ParseException If the JWT cannot be parsed.
   */
  @Operation(summary = "Retrieve signature as SVG by database ID",
             description = "Fetches an SVG signature image associated with a given database ID by extracting it from a stored JWT. Requires ADMIN role.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "Signature image successfully retrieved"),
               @ApiResponse(responseCode = "403", description = "Access denied"),
               @ApiResponse(responseCode = "404", description = "Signature not found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @GetMapping(path = "/signature.svg", produces = "image/svg+xml")
  public ResponseEntity<byte[]> signatureSvgByDbId(
    @RequestParam("id") String dbId,
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws IOException, ParseException
  {
    log.debug("dbId={}", dbId);
    log.debug("principal={}", principal);

    SdbSecretData secretData = dbService.findSdbSecretDataById(dbId);

    if(secretData != null && secretData.getType() == SdbSecretType.ID_SIGNATURE_JWT)
    {
      SignaturePad signaturePad = authService.authCheck(secretData.getKey(), false);
      SignedJWT signedJWT = authService.verifyJwt(signaturePad, secretData.getSecret());
      String sigsvgBase64 = signedJWT.getJWTClaimsSet().getClaimAsString("sigsvg");
      return ResponseEntity.ok(Base64.getDecoder().decode(sigsvgBase64));
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Retrieves user information as a JSON object from the database by its ID.
   * This endpoint processes a JWT stored in the database to extract user details.
   * Requires administrator privileges.
   *
   * @param dbId The database ID of the user information data.
   * @param principal The authenticated OIDC user, used for authorization.
   *
   * @return A {@link ResponseEntity} containing a map of user information with content type `application/json`.
   *
   * @throws IOException If an I/O error occurs during data retrieval.
   * @throws ParseException If the JWT cannot be parsed.
   */
  @Operation(summary = "Retrieve user information as JSON by database ID",
             description = "Fetches user details associated with a given database ID by extracting them from a stored JWT. Requires ADMIN role.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "User information successfully retrieved"),
               @ApiResponse(responseCode = "403", description = "Access denied"),
               @ApiResponse(responseCode = "404", description = "User information not found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @GetMapping(path = "/userinfo.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> userinfoJsonByDbId(
    @RequestParam("id") String dbId,
    @AuthenticationPrincipal DefaultOidcUser principal
  )
    throws IOException, ParseException
  {

    log.debug("dbId={}", dbId);
    log.debug("principal={}", principal);

    SdbSecretData secretData = dbService.findSdbSecretDataById(dbId);

    if(secretData != null && secretData.getType() == SdbSecretType.ID_SIGNATURE_JWT)
    {
      SignaturePad signaturePad = authService.authCheck(secretData.getKey(), false);
      SignedJWT signedJWT = authService.verifyJwt(signaturePad, secretData.getSecret());

      if(signedJWT != null)
      {
        log.debug("jwt json = {}", signedJWT.getJWTClaimsSet().toJSONObject());

        Map<String, Object> map = new HashMap<>();

        Object publisherObj = objectMapper.readValue(
          signedJWT.getJWTClaimsSet().getClaimAsString("publisher"),
          map.getClass()
        );

        map.put("sub", signedJWT.getJWTClaimsSet().getSubject());
        map.put("mail", signedJWT.getJWTClaimsSet().getClaimAsString("mail"));
        map.put("sigpad", signedJWT.getJWTClaimsSet().getClaimAsString("sigpad"));
        map.put("iss", signedJWT.getJWTClaimsSet().getClaimAsString("iss"));
        map.put("name", signedJWT.getJWTClaimsSet().getClaimAsString("name"));
        map.put("cardnumber", signedJWT.getJWTClaimsSet().getClaimAsString("cardnumber"));
        map.put("publisher", publisherObj);
        Date iat = (Date)signedJWT.getJWTClaimsSet().getClaim("iat");
        map.put("iat", iat.getTime() / 1000);

        return ResponseEntity.ok(map);
      }
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Retrieves signature pad information as a JSON object from the database by its ID.
   * Requires administrator privileges.
   *
   * @param dbId The database ID of the signature pad data.
   * @param principal The authenticated OIDC user, used for authorization.
   *
   * @return A {@link ResponseEntity} containing the {@link SignaturePad} object with content type `application/json`.
   *
   * @throws IOException If an I/O error occurs during data retrieval.
   * @throws ParseException If the JWT cannot be parsed.
   */
  @Operation(summary = "Retrieve signature pad information as JSON by database ID",
             description = "Fetches signature pad details associated with a given database ID. Requires ADMIN role.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "Signature pad information successfully retrieved"),
               @ApiResponse(responseCode = "403", description = "Access denied"),
               @ApiResponse(responseCode = "404", description = "Signature pad not found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @GetMapping(path = "/pad.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SignaturePad> padByDbId(
    @RequestParam("id") String dbId,
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws IOException, ParseException
  {
    log.debug("dbId={}", dbId);
    log.debug("principal={}", principal);

    SdbSecretData secretData = dbService.findSdbSecretDataById(dbId);

    if(secretData != null && secretData.getType() == SdbSecretType.SIGNATURE_PAD_JSON)
    {
      return ResponseEntity.ok(authService.authCheck(secretData.getKey(), false));
    }

    return ResponseEntity.notFound().build();
  }

}
