/*
 * Copyright 2025 Thorsten Ludewig (t.ludewig@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package l9g.account.info.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import l9g.account.info.db.model.SdbProperty;
import java.util.Optional;
import l9g.account.info.controller.api.AuthService;
import l9g.account.info.service.FileStorageService;
import l9g.account.info.db.model.SdbSecretData;
import l9g.account.info.db.model.SdbSecretType;
import l9g.account.info.service.SignaturePad;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * DbService is responsible for managing and updating the properties related to
 * tenants...
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbService
{
  public static final String KEY_SYSTEM_USER = "SYSTEM USER";

  public static final String KEY_UNSET = "*** unset ***";

  public static final String KEY_DB_INITIALIZED = "database.initialized";

  private final SdbPropertiesRepository sdbPropertiesRepository;

  private final SdbSecretDataRepository sdbSecretDataRepository;

  private final FileStorageService fileStorageService;

  private final ObjectMapper objectMapper;

  /**
   * Handles the application startup event.
   *
   * This method is triggered when the application is ready and performs
   * necessary initialization tasks.
   */
  @EventListener(ApplicationReadyEvent.class)
  @Order(100)
  public void onStartup()
    throws Exception
  {
    log.info("*** Application startup ***");

    Optional<SdbProperty> dbInitialized = sdbPropertiesRepository
      .findByKey(KEY_DB_INITIALIZED);

    if( ! dbInitialized.isPresent())
    {
      log.info("\n\n*** INITIALIZE DATABASE ***\n");
      sdbPropertiesRepository.save(new SdbProperty(KEY_SYSTEM_USER,
        KEY_DB_INITIALIZED, "true"));

      /*
      SdbSecretData data = new SdbSecretData(KEY_SYSTEM_USER, "data1", SdbSecretType.SIGNATURE_PAD_JSON);
      data.setSecret("This shoud be a SIGNATURE_PAD_JSON.");
      sdbSecretDataRepository.save(data);
      Thread.sleep(2000);

      data = new SdbSecretData(KEY_SYSTEM_USER, "data2", SdbSecretType.ID_BACK_IMAGE);
      data.setValue("This shoud be a ID_BACK_IMAGE.".getBytes());
      sdbSecretDataRepository.save(data);
      fileStorageService.save(data);
      Thread.sleep(2000);

      data = new SdbSecretData(KEY_SYSTEM_USER, "data2", SdbSecretType.ID_BACK_IMAGE);
      data.setValue("This shoud be a ID_BACK_IMAGE.".getBytes());
      sdbSecretDataRepository.save(data);
      fileStorageService.save(data);
      Thread.sleep(2000);

      data = new SdbSecretData(KEY_SYSTEM_USER, "data2", SdbSecretType.ID_BACK_IMAGE);
      data.setValue("This shoud be a ID_BACK_IMAGE.".getBytes());
      sdbSecretDataRepository.save(data);
      fileStorageService.save(data);
      Thread.sleep(2000);

      data = new SdbSecretData(KEY_SYSTEM_USER, "data3", SdbSecretType.ID_FRONT_IMAGE);
      data.setValue("This shoud be a ID_FRONT_IMAGE.".getBytes());
      sdbSecretDataRepository.save(data);
      fileStorageService.save(data);
      Thread.sleep(2000);

      data = new SdbSecretData(KEY_SYSTEM_USER, "data4", SdbSecretType.ID_SIGNATURE_JWT);
      data.setValue("This shoud be a ID_SIGNATURE_JWT.".getBytes());
      sdbSecretDataRepository.save(data);
      fileStorageService.save(data);
      Thread.sleep(2000);
       */
    }
    else
    {
      log.info("Database already initialized.");
    }

    /*
    SdbSecretData data = sdbSecretDataRepository.findByKeyOrderByModifyTimestampDesc("data1").get().getFirst();
    log.debug("data1={}\n{}", data, data.getSecret());

    data = sdbSecretDataRepository.findByKeyOrderByModifyTimestampDesc("data2").get().getFirst();
    log.debug("data2={}\n{}", data, new String(fileStorageService.load(data)));

    data = sdbSecretDataRepository.findByKeyOrderByModifyTimestampDesc("data3").get().getFirst();
    log.debug("data3={}\n{}", data, new String(fileStorageService.load(data)));

    data = sdbSecretDataRepository.findByKeyOrderByModifyTimestampDesc("data4").get().getFirst();
    log.debug("data4={}\n{}", data, new String(fileStorageService.load(data)));
     */
  }

  private SdbSecretData findSdbSecretData(String uuid, SdbSecretType type)
    throws JsonProcessingException
  {
    Optional<List<SdbSecretData>> optional = sdbSecretDataRepository
      .findByKeyAndTypeOrderByModifyTimestampDesc(uuid, type);

    SdbSecretData secretData = null;

    if(optional.isPresent() &&  ! optional.get().isEmpty())
    {
      secretData = optional.get().getFirst();
    }

    return secretData;
  }

  public SignaturePad findSignaturePadbyUUID(String uuid)
    throws JsonProcessingException
  {
    SignaturePad signaturePad = null;

    SdbSecretData secretData = findSdbSecretData(
      uuid, SdbSecretType.SIGNATURE_PAD_JSON);

    if(secretData != null)
    {
      signaturePad = objectMapper.readValue(
        secretData.getSecret(), SignaturePad.class);
      log.debug("signaturePad={}", signaturePad);
    }

    return signaturePad;
  }

  public void saveSignaturePad(String publisher, SignaturePad signaturePad)
    throws JsonProcessingException
  {
    log.debug("saveSignaturePad {} / {}", publisher, signaturePad);

    SdbSecretData secretData = findSdbSecretData(
      signaturePad.getUuid(), SdbSecretType.SIGNATURE_PAD_JSON);

    if(secretData == null)
    {
      secretData = new SdbSecretData(publisher,
        signaturePad.getUuid(), SdbSecretType.SIGNATURE_PAD_JSON);
      log.debug("create new secret data : {}", secretData.getKey());
    }

    log.debug("save signature pad = {}", signaturePad);
    secretData.setSecret(objectMapper.writeValueAsString(signaturePad));
    secretData.setName(signaturePad.getName());
    if(signaturePad.getClientEnvironment() != null)
    {
      secretData.setDescription(
        (String)signaturePad.getClientEnvironment().get("userAgent"));
    }
    sdbSecretDataRepository.save(secretData);
  }

  public void saveSecretFileData(String publisher, String fullname,
    String username, String mail, String padUuid, String side, MultipartFile file)
    throws JsonProcessingException, IOException
  {
    log.debug("saveSecretFileData");

    Map<String, String> descriptionMap = new HashMap<>();
    descriptionMap.put("name", fullname);
    descriptionMap.put("mail", mail);

    SdbSecretData data = new SdbSecretData(
      publisher, padUuid, SdbSecretType.fromString(side), true);
    data.setName(username);
    data.setDescription(objectMapper.writeValueAsString(descriptionMap));
    data.setValue(file.getBytes());

    sdbSecretDataRepository.save(data);
    fileStorageService.save(data);
  }

  public void saveSignedJWT(SignedJWT signedJWT)
    throws JsonProcessingException, IOException, ParseException
  {
    log.debug("saveSecretFileData");

    SdbSecretData data = new SdbSecretData(
      signedJWT.getJWTClaimsSet().getClaim("publisher").toString(),
      signedJWT.getJWTClaimsSet().getIssuer(),
      SdbSecretType.ID_SIGNATURE_JWT, true);

    data.setName(signedJWT.getJWTClaimsSet().getSubject());
    Map<String, String> descriptionMap = new HashMap<>();
    descriptionMap.put("name", signedJWT.getJWTClaimsSet().getClaimAsString("name"));
    descriptionMap.put("mail", signedJWT.getJWTClaimsSet().getClaimAsString("mail"));

    data.setDescription(objectMapper.writeValueAsString(descriptionMap));
    data.setSecret(signedJWT.getParsedString());

    sdbSecretDataRepository.save(data);
  }

  public byte[] findFileDataById(String id)
    throws IOException
  {
    Optional<SdbSecretData> optional = sdbSecretDataRepository.findById(id);
    byte[] fileData = null;
    if(optional.isPresent())
    {
      SdbSecretData secretData = optional.get();
      if(secretData.getType() == SdbSecretType.ID_FRONT_IMAGE
        || secretData.getType() == SdbSecretType.ID_BACK_IMAGE)
      {
        fileData = fileStorageService.load(secretData);
      }
    }

    return fileData;
  }

  public SdbSecretData findSdbSecretDataById(String id)
  {
    return sdbSecretDataRepository.findById(id).orElse(null);
  }

}
