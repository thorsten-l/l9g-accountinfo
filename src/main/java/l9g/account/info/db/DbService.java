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
 * Service responsible for managing database operations related to application
 * properties and secret data.
 * This includes handling application startup initialization, storing and
 * retrieving signature pad data, file data, and signed JWTs.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbService
{
  /**
   * Constant representing the key for the system user in properties.
   */
  public static final String KEY_SYSTEM_USER = "SYSTEM USER";

  /**
   * Constant representing an unset or undefined key/value.
   */
  public static final String KEY_UNSET = "*** unset ***";

  /**
   * Property key used to check if the database has been initialized.
   */
  public static final String KEY_DB_INITIALIZED = "database.initialized";

  /**
   * Repository for accessing and managing {@link SdbProperty} entities.
   */
  private final SdbPropertiesRepository sdbPropertiesRepository;

  /**
   * Repository for accessing and managing {@link SdbSecretData} entities.
   */
  private final SdbSecretDataRepository sdbSecretDataRepository;

  /**
   * Service for handling file storage operations.
   */
  private final FileStorageService fileStorageService;

  /**
   * Object mapper for JSON serialization/deserialization.
   */
  private final ObjectMapper objectMapper;

  /**
   * Handles the application startup event.
   *
   * This method is triggered when the application is ready and performs
   * necessary initialization tasks.
   */
  /**
   * Handles the application startup event.
   * This method is triggered when the application is ready and performs
   * necessary initialization tasks, such as checking if the database has been initialized.
   *
   * @throws Exception If an error occurs during startup initialization.
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

  /**
   * Finds the latest {@link SdbSecretData} entry for a given UUID and type.
   *
   * @param uuid The UUID of the secret data.
   * @param type The type of the secret data.
   *
   * @return The latest {@link SdbSecretData} object, or null if not found.
   *
   * @throws JsonProcessingException If there is an error processing JSON.
   */
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

  /**
   * Finds a {@link SignaturePad} by its UUID.
   *
   * @param uuid The UUID of the signature pad.
   *
   * @return The {@link SignaturePad} object, or null if not found.
   *
   * @throws JsonProcessingException If there is an error processing JSON.
   */
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

  /**
   * Saves or updates a {@link SignaturePad} in the database.
   *
   * @param publisher The publisher of the signature pad data.
   * @param signaturePad The {@link SignaturePad} object to save.
   *
   * @throws JsonProcessingException If there is an error processing JSON.
   */
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

  /**
   * Saves secret file data (e.g., photos) associated with a signature pad event.
   *
   * @param publisher The publisher of the file data.
   * @param fullname The full name associated with the file.
   * @param username The username associated with the file.
   * @param mail The email associated with the file.
   * @param padUuid The UUID of the signature pad.
   * @param side The side of the card (e.g., "front", "back").
   * @param file The {@link MultipartFile} containing the file data.
   *
   * @throws JsonProcessingException If there is an error processing JSON.
   * @throws IOException If an I/O error occurs during file processing.
   */
  public SdbSecretData saveSecretFileData(String publisher, String fullname,
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
    return data;
  }

  /**
   * Saves a signed JWT to the database.
   *
   * @param signedJWT The {@link SignedJWT} to save.
   *
   * @throws JsonProcessingException If there is an error processing JSON.
   * @throws IOException If an I/O error occurs.
   * @throws ParseException If parsing the JWT claims fails.
   */
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

  /**
   * Finds file data (e.g., images) by its database ID.
   *
   * @param id The database ID of the file data.
   *
   * @return A byte array containing the file data, or null if not found or not a file type.
   *
   * @throws IOException If an I/O error occurs during file loading.
   */
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

  /**
   * Finds an {@link SdbSecretData} entry by its database ID.
   *
   * @param id The database ID of the secret data.
   *
   * @return The {@link SdbSecretData} object, or null if not found.
   */
  public SdbSecretData findSdbSecretDataById(String id)
  {
    return sdbSecretDataRepository.findById(id).orElse(null);
  }

  /**
   * Finds a list of {@link SdbSecretData} entries by their type, ordered by name in ascending order.
   *
   * @param type The type of the secret data to find.
   *
   * @return A list of {@link SdbSecretData} objects, or null if none found.
   */
  public List<SdbSecretData> findSdbSecretDataByType(SdbSecretType type)
  {
    return sdbSecretDataRepository.findByTypeOrderByNameAsc(type).orElse(null);
  }

}
