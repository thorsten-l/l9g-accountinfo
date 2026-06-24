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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jwt.SignedJWT;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpHeaders;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import l9g.account.info.db.DbService;
import l9g.account.info.db.model.SdbSecretData;
import l9g.account.info.db.model.SdbSecretType;
import l9g.account.info.service.SignaturePad;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import l9g.account.info.dto.DtoUserInfo;
import l9g.account.info.service.FileStorageService;
import l9g.account.info.service.LdapService;
import l9g.account.info.vault.VaultService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for administrative API endpoints related to
 * account and signature pad information.
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/admin/secret")
@RequiredArgsConstructor
@Tag(name = "Admin API", description = "Admin API for managing account and signature pad data")
public class ApiAdminController
{
  private final VaultService vaultService;

  /**
   * Database service for accessing and managing stored data.
   */
  private final DbService dbService;

  private final FileStorageService fileStorageService;

  private final LdapService ldapService;

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
  @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITADMIN')")
  @GetMapping(path = "/id.jpeg", produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<byte[]> photoByDbId(
    @RequestParam("id") String dbId,
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws IOException
  {
    log.debug("dbId={}", dbId);
    log.debug("principal={}", principal);
    auditRead(principal, "ID_IMAGE", dbId);
    return ResponseEntity.ok(fileStorageService.findFileDataById(dbId));
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
  @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITADMIN')")
  @GetMapping(path = "/signature.png", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> signaturePngByDbId(
    @RequestParam("id") String dbId,
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws IOException, ParseException
  {
    log.debug("dbId={}", dbId);
    log.debug("principal={}", principal);
    auditRead(principal, "SIGNATURE_PNG", dbId);

    SdbSecretData secretData = dbService.findSdbSecretDataById(dbId, false);

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
  @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITADMIN')")
  @GetMapping(path = "/signature.svg", produces = "image/svg+xml")
  public ResponseEntity<byte[]> signatureSvgByDbId(
    @RequestParam("id") String dbId,
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws IOException, ParseException
  {
    log.debug("dbId={}", dbId);
    log.debug("principal={}", principal);
    auditRead(principal, "SIGNATURE_SVG", dbId);

    SdbSecretData secretData = dbService.findSdbSecretDataById(dbId, false);

    if(secretData != null && secretData.getType() == SdbSecretType.ID_SIGNATURE_JWT)
    {
      SignaturePad signaturePad = authService.authCheck(secretData.getKey(), false);
      SignedJWT signedJWT = authService.verifyJwt(signaturePad, secretData.getSecret());
      String sigsvgBase64 = signedJWT.getJWTClaimsSet().getClaimAsString("sigsvg");
      return ResponseEntity.ok(Base64.getDecoder().decode(sigsvgBase64));
    }

    return ResponseEntity.notFound().build();
  }

  private void putIfNotNull(Map<String, Object> map, String key, Object object)
  {
    if(object != null)
    {
      map.put(key, object);
    }
  }

  /**
   * Emits a read-access audit entry at {@code INFO} level so it is forwarded via
   * Docker GELF to the central Graylog cluster. Records <em>who</em> (admin)
   * accessed <em>what</em> (action) for <em>which</em> target (record id, person
   * uid or search query) — the accountability trail required by Art. 5 Abs. 2 /
   * Art. 32 DSGVO and NIS2 monitoring.
   *
   * @param principal The authenticated admin (OIDC user).
   * @param action The logical read action (e.g. {@code ID_IMAGE}, {@code AUDIT_PERSON}).
   * @param target The accessed target (record id, person uid or query).
   */
  private void auditRead(DefaultOidcUser principal, String action, String target)
  {
    log.info("AUDIT_READ: admin={}, action={}, target={}",
      principal != null ? principal.getName() : "?", action, target);
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
  @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITADMIN')")
  @GetMapping(path = "/userinfo.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> userinfoJsonByDbId(
    @RequestParam("id") String dbId,
    @AuthenticationPrincipal DefaultOidcUser principal
  )
    throws IOException, ParseException
  {

    log.debug("dbId={}", dbId);
    log.debug("principal={}", principal);
    auditRead(principal, "USERINFO", dbId);

    SdbSecretData secretData = dbService.findSdbSecretDataById(dbId, false);

    if(secretData != null && secretData.getType() == SdbSecretType.ID_SIGNATURE_JWT)
    {
      SignaturePad signaturePad = authService.authCheck(secretData.getKey(), false);
      SignedJWT signedJWT = authService.verifyJwt(signaturePad, secretData.getSecret());

      if(signedJWT != null)
      {
        log.debug("jwt json = {}", signedJWT.getJWTClaimsSet().toJSONObject());

        Map<String, Object> map = new LinkedHashMap<>();

        Object publisherObj = objectMapper.readValue(
          signedJWT.getJWTClaimsSet().getClaimAsString("publisher"),
          map.getClass()
        );

        map.put("sub", signedJWT.getJWTClaimsSet().getSubject());
        map.put("mail", signedJWT.getJWTClaimsSet().getClaimAsString("mail"));
        map.put("sigpad", signedJWT.getJWTClaimsSet().getClaimAsString("sigpad"));
        map.put("iss", signedJWT.getJWTClaimsSet().getClaimAsString("iss"));
        map.put("name", signedJWT.getJWTClaimsSet().getClaimAsString("name"));
        putIfNotNull(map, "birthday", signedJWT.getJWTClaimsSet().getClaimAsString("birthday"));
        putIfNotNull(map, "barcode", signedJWT.getJWTClaimsSet().getClaimAsString("barcode"));
        map.put("customer", signedJWT.getJWTClaimsSet().getClaimAsString("customer"));
        map.put("employee-type", signedJWT.getJWTClaimsSet().getClaimAsString("employeetype"));
        map.put("publisher", publisherObj);
        Date iat = (Date)signedJWT.getJWTClaimsSet().getClaim("iat");
        map.put("iat", iat.getTime() / 1000);

        return ResponseEntity.ok(map);
      }
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Retrieves the listing of an {@code EXT_IDENTIFICATION_ARCHIVE} ZIP file
   * together with its general metadata. The encrypted archive is decrypted on
   * the fly; only the entry names and (uncompressed) sizes are returned, never
   * the file contents. Requires the vault to be unsealed.
   *
   * @param dbId The database ID of the archive secret data.
   * @param principal The authenticated OIDC user, used for authorization.
   *
   * @return A {@link ResponseEntity} containing general metadata and a list of
   * archive entries ({@code name}, {@code size}) with content type
   * `application/json`.
   *
   * @throws IOException If an I/O error occurs during data retrieval or while
   * reading the ZIP archive.
   */
  @Operation(summary = "Retrieve identification archive listing as JSON by database ID",
             description = "Fetches the file listing (name, size) and general metadata of an EXT_IDENTIFICATION_ARCHIVE ZIP. Requires ADMIN or AUDITADMIN role and unsealed vault.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "Archive listing successfully retrieved"),
               @ApiResponse(responseCode = "403", description = "Access denied"),
               @ApiResponse(responseCode = "404", description = "Archive not found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITADMIN')")
  @GetMapping(path = "/archive.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> archiveJsonByDbId(
    @RequestParam("id") String dbId,
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws IOException
  {
    log.debug("dbId={}", dbId);
    log.debug("principal={}", principal);
    auditRead(principal, "ARCHIVE_LIST", dbId);

    if(vaultService.getUnlockedKey() == null)
    {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "Archive listing is not allowed");
    }

    SdbSecretData secretData = dbService.findSdbSecretDataById(dbId, false);

    if(secretData != null
      && secretData.getType() == SdbSecretType.EXT_IDENTIFICATION_ARCHIVE)
    {
      byte[] zipBytes = fileStorageService.load(secretData);

      List<Map<String, Object>> files = new ArrayList<>();
      byte[] buffer = new byte[8192];
      try(ZipInputStream zis = new ZipInputStream(
        new ByteArrayInputStream(zipBytes)))
      {
        ZipEntry entry;
        while((entry = zis.getNextEntry()) != null)
        {
          if(entry.isDirectory())
          {
            zis.closeEntry();
            continue;
          }
          // Size from the central/local header may be unknown (-1) when the
          // entry used a streaming data descriptor; fall back to counting bytes.
          long size = entry.getSize();
          if(size < 0)
          {
            size = 0;
            int read;
            while((read = zis.read(buffer)) != -1)
            {
              size += read;
            }
          }
          Map<String, Object> fileInfo = new LinkedHashMap<>();
          fileInfo.put("name", entry.getName());
          fileInfo.put("size", size);
          files.add(fileInfo);
          zis.closeEntry();
        }
      }

      Map<String, Object> map = new LinkedHashMap<>();
      map.put("name", secretData.getName());
      try
      {
        map.put("description", objectMapper.readValue(
          secretData.getDescription(), Map.class));
      }
      catch(JsonProcessingException ex)
      {
        map.put("description", secretData.getDescription());
      }
      map.put("size", secretData.getSize());
      map.put("checksum", secretData.getChecksum());
      map.put("createTimestamp", secretData.getCreateTimestamp());
      map.put("files", files);

      return ResponseEntity.ok(map);
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Streams a single PDF entry from an {@code EXT_IDENTIFICATION_ARCHIVE} ZIP
   * for inline display only. The response is marked
   * {@code Content-Disposition: inline} and {@code Cache-Control: no-store};
   * only entries whose name ends with {@code .pdf} are served. The encrypted
   * archive is decrypted on the fly and never written to disk. Requires the
   * vault to be unsealed.
   *
   * @param dbId The database ID of the archive secret data.
   * @param name The exact ZIP entry name to display (must end with {@code .pdf}).
   * @param principal The authenticated OIDC user, used for authorization.
   *
   * @return A {@link ResponseEntity} carrying the PDF bytes with content type
   * `application/pdf`, or {@code 404} if the entry does not exist.
   *
   * @throws IOException If an I/O error occurs during data retrieval or while
   * reading the ZIP archive.
   */
  @Operation(summary = "Display a PDF from an identification archive inline",
             description = "Streams a single PDF entry of an EXT_IDENTIFICATION_ARCHIVE ZIP for inline display (no download). Requires ADMIN or AUDITADMIN role and unsealed vault.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "PDF successfully retrieved"),
               @ApiResponse(responseCode = "400", description = "Requested entry is not a PDF"),
               @ApiResponse(responseCode = "403", description = "Access denied"),
               @ApiResponse(responseCode = "404", description = "Archive or entry not found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITADMIN')")
  @GetMapping(path = "/archive/file", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> archiveFileByDbId(
    @RequestParam("id") String dbId,
    @RequestParam("name") String name,
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws IOException
  {
    log.debug("dbId={}, name={}", dbId, name);
    log.debug("principal={}", principal);
    auditRead(principal, "ARCHIVE_FILE", dbId + "/" + name);

    if(vaultService.getUnlockedKey() == null)
    {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "Archive access is not allowed");
    }

    if(name == null ||  ! name.toLowerCase().endsWith(".pdf"))
    {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "Only PDF files can be displayed");
    }

    SdbSecretData secretData = dbService.findSdbSecretDataById(dbId, false);

    if(secretData != null
      && secretData.getType() == SdbSecretType.EXT_IDENTIFICATION_ARCHIVE)
    {
      byte[] fileBytes = extractZipEntry(fileStorageService.load(secretData), name);

      if(fileBytes != null)
      {
        return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_PDF)
          .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
          .header(HttpHeaders.CACHE_CONTROL, "no-store")
          .body(fileBytes);
      }
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Extracts the bytes of a single, exactly-named entry from a ZIP archive held
   * in memory. The exact-name match prevents path traversal beyond the archive.
   *
   * @param zipBytes The (decrypted) ZIP archive bytes.
   * @param name The exact entry name to extract.
   *
   * @return The entry's bytes, or {@code null} if no matching entry exists.
   *
   * @throws IOException If an I/O error occurs while reading the archive.
   */
  private byte[] extractZipEntry(byte[] zipBytes, String name)
    throws IOException
  {
    byte[] buffer = new byte[8192];
    try(ZipInputStream zis = new ZipInputStream(
      new ByteArrayInputStream(zipBytes)))
    {
      ZipEntry entry;
      while((entry = zis.getNextEntry()) != null)
      {
        if( ! entry.isDirectory() && entry.getName().equals(name))
        {
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          int read;
          while((read = zis.read(buffer)) != -1)
          {
            baos.write(buffer, 0, read);
          }
          return baos.toByteArray();
        }
        zis.closeEntry();
      }
    }
    return null;
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
    auditRead(principal, "PAD", dbId);

    SdbSecretData secretData = dbService.findSdbSecretDataById(dbId, false);

    if(secretData != null && secretData.getType() == SdbSecretType.SIGNATURE_PAD_JSON)
    {
      return ResponseEntity.ok(authService.authCheck(secretData.getKey(), false));
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Retrieves a list of signature pads.
   * Requires administrator privileges.
   *
   * @param principal The authenticated OIDC user, used for authorization.
   *
   * @return A {@link ResponseEntity} containing a list of maps, each representing a signature pad.
   *
   * @throws Exception If an error occurs during data retrieval.
   */
  @Operation(summary = "Retrieve all signature pads",
             description = "Fetches a list of all registered signature pads, including their metadata. Requires ADMIN role.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "List of signature pads successfully retrieved"),
               @ApiResponse(responseCode = "403", description = "Access denied"),
               @ApiResponse(responseCode = "404", description = "No signature pads found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @GetMapping(path = "/pads", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Map<String, Object>>> pads(
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws Exception
  {
    log.debug("principal={}", principal);

    List<Map<String, Object>> result = new ArrayList<>();

    List<SdbSecretData> list = dbService.findSdbSecretDataByType(SdbSecretType.SIGNATURE_PAD_JSON, false);

    if(list != null && list.size() > 0)
    {
      list.forEach(secretData ->
      {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dbid", secretData.getId());
        map.put("padid", secretData.getKey());
        map.put("name", secretData.getName());
        map.put("description", secretData.getDescription());
        try
        {
          map.put("createTimestamp", secretData.getCreateTimestamp());
          Object createdBy = objectMapper.readValue(secretData.getModifiedBy(),
            map.getClass());
          map.put("createdBy", createdBy);
          map.put("modifyTimestamp", secretData.getModifyTimestamp());
          Object modifiedBy = objectMapper.readValue(secretData.getModifiedBy(),
            map.getClass());
          map.put("modifiedBy", modifiedBy);
        }
        catch(JsonProcessingException ex)
        {
          log.error("parse error {}", ex);
        }
        map.put("hidden", secretData.isHidden());
        map.put("immutable", secretData.isImmutable());
        result.add(map);
      });

      return ResponseEntity.ok(result);
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Retrieves a list of signatures.
   * Requires administrator privileges.
   *
   * @param principal The authenticated OIDC user, used for authorization.
   *
   * @return A {@link ResponseEntity} containing a list of maps, each representing a signature.
   *
   * @throws Exception If an error occurs during data retrieval.
   */
  @Operation(summary = "Retrieve all signatures",
             description = "Fetches a list of all stored signatures, including their metadata. Requires ADMIN role.",
             responses =
             {
               @ApiResponse(responseCode = "200", description = "List of signatures successfully retrieved"),
               @ApiResponse(responseCode = "403", description = "Access denied"),
               @ApiResponse(responseCode = "404", description = "No signatures found"),
               @ApiResponse(responseCode = "500", description = "Internal server error")
             })
  @GetMapping(path = "/signatures", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Map<String, Object>>> signatures(
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws Exception
  {
    log.debug("principal={}", principal);

    List<Map<String, Object>> result = new ArrayList<>();

    List<SdbSecretData> list = dbService.findSdbSecretDataByType(SdbSecretType.ID_SIGNATURE_JWT, false);

    if(list != null && list.size() > 0)
    {
      list.forEach(secretData ->
      {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dbid", secretData.getId());
        map.put("padid", secretData.getKey());
        try
        {
          Map description = objectMapper.readValue(secretData.getDescription(),
            map.getClass());
          description.put("username", secretData.getName());
          map.put("signedBy", description);
          map.put("createTimestamp", secretData.getCreateTimestamp());
          Object createdBy = objectMapper.readValue(secretData.getModifiedBy(),
            map.getClass());
          map.put("createdBy", createdBy);
          map.put("modifyTimestamp", secretData.getModifyTimestamp());
          Object modifiedBy = objectMapper.readValue(secretData.getModifiedBy(),
            map.getClass());
          map.put("modifiedBy", modifiedBy);
        }
        catch(JsonProcessingException ex)
        {
          log.error("parse error {}", ex);
        }
        result.add(map);
      });

      return ResponseEntity.ok(result);
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Retrieves a list of persons based on a search query.
   * Requires the vault to be unsealed.
   *
   * @param query The search query.
   * @param principal The authenticated OIDC user.
   * @return A list of matching users.
   * @throws Exception if data retrieval fails.
   */
  @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITADMIN')")
  @Operation(summary = "Search for persons",
             description = "Returns a list of persons matching the query. Requires ADMIN or AUDITADMIN role and unsealed vault.")
  @GetMapping(path = "/search/person")
  public ResponseEntity<List<DtoUserInfo>> personList(
    @RequestParam("query") String query,
    @AuthenticationPrincipal DefaultOidcUser principal
  )
    throws Exception
  {
    log.debug("personList called for query '{}'", query);
    auditRead(principal, "PERSON_SEARCH", query);

    if(vaultService.getUnlockedKey() == null)
    {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Query persons is not allowed");
    }

    List<DtoUserInfo> persons = ldapService.listPersons(query);

    if(persons != null && !persons.isEmpty())
    {
      return ResponseEntity.ok(persons);
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Retrieves audit data (stored secrets) for a specific person UID.
   * Requires the vault to be unsealed.
   *
   * @param uid The user ID of the person.
   * @param principal The authenticated OIDC user.
   * @return A list of secret data entries for the user.
   * @throws Exception if data retrieval fails.
   */
  @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITADMIN')")
  @Operation(summary = "Audit person secrets",
             description = "Returns a list of stored secrets for a specific person UID. Requires ADMIN or AUDITADMIN role and unsealed vault.")
  @GetMapping(path = "/audit/person/{uid}")
  public ResponseEntity<List<SdbSecretData>> auditPerson(
    @PathVariable("uid") String uid,
    @AuthenticationPrincipal DefaultOidcUser principal
  )
    throws Exception
  {
    log.debug("auditPerson called for uid '{}'", uid);
    auditRead(principal, "AUDIT_PERSON", uid);

    if(vaultService.getUnlockedKey() == null)
    {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audit person is not allowed");
    }

    List<SdbSecretData> list = dbService.findSdbSecretDataByName(uid);

    if(list != null && !list.isEmpty())
    {
      return ResponseEntity.ok(list);
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Keys that are stripped from the exported metadata: the publisher/operator
   * identifiers and any binary/biometric blobs (which are never part of a
   * metadata export).
   */
  private static final Set<String> EXPORT_STRIP_KEYS = Set.of(
    "publisher", "sigpng", "sigsvg", "jpegPhoto",
    "passImages", "portraitImages", "fileResponseDownload");

  /**
   * Exports the decrypted {@link SdbSecretData} metadata of a person as a ZIP
   * download. The export contains <strong>no</strong> publisher/operator data
   * and <strong>no</strong> binary content (neither the filesystem files nor
   * embedded image/signature blobs) — only the descriptive, decrypted metadata.
   * Requires the vault to be unsealed.
   *
   * @param uid The person's UID ({@code SdbSecretData.name}).
   * @param principal The authenticated OIDC user, used for authorization/audit.
   *
   * @return A {@code application/zip} attachment containing {@code <uid>-metadata.json}.
   *
   * @throws IOException If building the ZIP fails.
   */
  @Operation(summary = "Export person metadata as ZIP",
             description = "Exports the decrypted SdbSecretData metadata of a person (without publisher, without binary data) as a ZIP. Requires ADMIN or AUDITADMIN role and unsealed vault.")
  @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITADMIN')")
  @GetMapping(path = "/export/person/{uid}", produces = "application/zip")
  public ResponseEntity<byte[]> exportPerson(
    @PathVariable("uid") String uid,
    @AuthenticationPrincipal DefaultOidcUser principal)
    throws IOException
  {
    log.debug("exportPerson called for uid '{}'", uid);

    if(vaultService.getUnlockedKey() == null)
    {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "Export person is not allowed");
    }

    List<SdbSecretData> list = dbService.findSdbSecretDataByName(uid);
    if(list == null || list.isEmpty())
    {
      return ResponseEntity.notFound().build();
    }

    List<Map<String, Object>> metadata = new ArrayList<>(list.size());
    for(SdbSecretData data : list)
    {
      metadata.add(toExportMetadata(data));
    }

    byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
      .writeValueAsBytes(metadata);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try(ZipOutputStream zos = new ZipOutputStream(baos))
    {
      zos.putNextEntry(new ZipEntry(uid + "-metadata.json"));
      zos.write(json);
      zos.closeEntry();
    }

    log.info("USER_EXPORT: admin={}, uid={}, records={}",
      principal != null ? principal.getName() : "?", uid, list.size());

    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType("application/zip"))
      .header(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + uid + "-metadata.zip\"")
      .header(HttpHeaders.CACHE_CONTROL, "no-store")
      .body(baos.toByteArray());
  }

  /**
   * Builds the export metadata map for one record: descriptive columns plus the
   * decrypted {@code secret}/{@code description}, with publisher and binary
   * fields removed. The audit columns {@code createdBy}/{@code modifiedBy}
   * (which carry the publisher) are intentionally omitted.
   */
  private Map<String, Object> toExportMetadata(SdbSecretData data)
  {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", data.getId());
    map.put("type", data.getType());
    map.put("key", data.getKey());
    map.put("name", data.getName());
    map.put("size", data.getSize());
    map.put("checksum", data.getChecksum());
    map.put("createTimestamp", data.getCreateTimestamp());
    map.put("modifyTimestamp", data.getModifyTimestamp());
    map.put("immutable", data.isImmutable());
    map.put("hidden", data.isHidden());
    map.put("description", sanitizedNode(parseLenient(data.getDescription())));
    map.put("secret", sanitizedNode(parseSecret(data)));
    return map;
  }

  /**
   * Parses a possibly-JSON string into a {@link JsonNode}; returns a text node
   * for non-JSON input and {@code null} for blank input.
   */
  private JsonNode parseLenient(String value)
  {
    if(value == null || value.isBlank())
    {
      return null;
    }
    try
    {
      return objectMapper.readTree(value);
    }
    catch(JsonProcessingException e)
    {
      return objectMapper.getNodeFactory().textNode(value);
    }
  }

  /**
   * Returns the decrypted secret payload as a {@link JsonNode}. For signed-JWT
   * records the JWT claims are decoded (not the raw token); other types are
   * parsed as JSON. File-based types have no DB secret ({@code null}).
   */
  private JsonNode parseSecret(SdbSecretData data)
  {
    String secret = data.getSecret();
    if(secret == null || secret.isBlank())
    {
      return null;
    }
    if(data.getType() == SdbSecretType.ID_SIGNATURE_JWT)
    {
      try
      {
        return objectMapper.readTree(
          SignedJWT.parse(secret).getJWTClaimsSet().toString());
      }
      catch(ParseException | JsonProcessingException e)
      {
        log.warn("Could not decode JWT secret for export id={}", data.getId());
        return null;
      }
    }
    return parseLenient(secret);
  }

  /**
   * Recursively removes {@link #EXPORT_STRIP_KEYS} (publisher and binary blobs)
   * from the given node and returns it.
   */
  private JsonNode sanitizedNode(JsonNode node)
  {
    if(node == null)
    {
      return null;
    }
    if(node.isObject())
    {
      ObjectNode obj = (ObjectNode)node;
      for(String key : EXPORT_STRIP_KEYS)
      {
        obj.remove(key);
      }
      obj.forEach(this :: sanitizedNode);
    }
    else if(node.isArray())
    {
      node.forEach(this :: sanitizedNode);
    }
    return node;
  }

  /**
   * Manually erases <strong>all</strong> data of a person: every
   * {@link SdbSecretData} row, the associated encrypted files and the
   * {@link l9g.account.info.db.model.SdbLastSeen} entry
   * ({@link DbService#deleteUserData}). Implements an on-demand GDPR Art. 17
   * erasure. Uses {@code POST} (not {@code DELETE}) so the request passes the
   * reverse-proxy method allow-list. Requires the vault to be unsealed.
   *
   * @param uid The person's UID ({@code SdbSecretData.name}).
   * @param principal The authenticated OIDC user, used for authorization/audit.
   *
   * @return A summary of the deletion ({@code deletedRecords}, {@code failedRecords},
   * {@code complete}).
   */
  @Operation(summary = "Erase all data of a person",
             description = "Deletes all SdbSecretData rows, their files and the last-seen entry of a person. Requires ADMIN role and unsealed vault.")
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping(path = "/person/{uid}/delete",
               produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> deletePerson(
    @PathVariable("uid") String uid,
    @AuthenticationPrincipal DefaultOidcUser principal)
  {
    log.debug("deletePerson called for uid '{}'", uid);

    if(vaultService.getUnlockedKey() == null)
    {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "Erase person is not allowed");
    }

    DbService.UserDeletionResult result =
      dbService.deleteUserData(uid, fileStorageService);

    log.warn("USER_ERASE_MANUAL: admin={}, uid={}, deletedRecords={}, "
      + "failedRecords={}",
      principal != null ? principal.getName() : "?", uid,
      result.deletedRecords(), result.failedRecords());

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("uid", uid);
    body.put("deletedRecords", result.deletedRecords());
    body.put("failedRecords", result.failedRecords());
    body.put("complete", result.complete());

    return ResponseEntity.ok(body);
  }

}
