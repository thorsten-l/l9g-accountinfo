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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import l9g.account.info.service.SignaturePad;
import l9g.account.info.service.SignaturePadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for handling authentication and authorization checks for signature pads.
 * This class provides methods to verify the authenticity and validity of signature pads
 * and to validate incoming JSON Web Tokens (JWTs) from these devices.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.l9g.account.info.controller.api.AuthServicecom)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService
{
  /**
   * Service for managing signature pad data and operations.
   */
  private final SignaturePadService signaturePadService;

  /**
   * Performs an authentication check for a signature pad.
   * Retrieves the signature pad by its UUID and optionally checks its validation status.
   *
   * @param padUuid The unique identifier of the signature pad.
   * @param checkValidity If true, also checks if the signature pad has been validated.
   *
   * @return The {@link SignaturePad} object if authentication is successful.
   *
   * @throws ResponseStatusException If the signature pad is not found, cannot be read, or is not valid.
   */
  SignaturePad authCheck(String padUuid, boolean checkValidity)
    throws ResponseStatusException
  {
    log.info("Pad UUID: {} ({})", padUuid, checkValidity ? "true" : "false");

    SignaturePad signaturePad = null;

    try
    {
      signaturePad =
        signaturePadService.findSignaturePadByUUID(padUuid);
    }
    catch(Throwable t)
    {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "ERROR: Unable to read signature pad storage."
      );
    }

    if(signaturePad == null)
    {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Signature pad UUID not found!"
      );
    }

    if(checkValidity &&  ! signaturePad.isValidated())
    {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Signature pad UUID not valid!"
      );
    }

    return signaturePad;
  }

  /**
   * Verifies the signature of a given JWT using the signature pad's public key.
   *
   * @param signaturePad The {@link SignaturePad} associated with the JWT.
   * @param jwt The JWT string to verify.
   *
   * @return A {@link SignedJWT} object if verification is successful.
   *
   * @throws ResponseStatusException If the JWT cannot be parsed, or if signature verification fails.
   */
  SignedJWT verifyJwt(SignaturePad signaturePad, String jwt)
    throws ResponseStatusException
  {
    SignedJWT signedJwt = null;
    try
    {
      RSAKey publicJwk = (RSAKey)JWK.parse(signaturePad.getPublicJwk());

      log.debug("publicJwk={}", publicJwk);

      signedJwt = SignedJWT.parse(jwt);
      JWSVerifier verifier = new RSASSAVerifier(publicJwk);

      if( ! signedJwt.verify(verifier))
      {
        throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "JWT signature verification failed!"
        );
      }
    }
    catch(ParseException ex)
    {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "JWT signature could not be parsed! : " + ex.getMessage()
      );
    }
    catch(JOSEException ex)
    {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "JOSE failure! : " + ex.getMessage()
      );
    }

    return signedJwt;
  }

}
