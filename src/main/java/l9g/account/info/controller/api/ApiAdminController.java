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

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/admin")
@RequiredArgsConstructor
public class ApiAdminController
{
  private final DbService dbService;

  private final AuthService authService;

  private final ObjectMapper objectMapper;

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
        map.put("publisher", publisherObj);
        Date iat = (Date)signedJWT.getJWTClaimsSet().getClaim("iat");
        map.put("iat", iat.getTime() / 1000);

        return ResponseEntity.ok(map);
      }
    }

    return ResponseEntity.notFound().build();
  }

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
