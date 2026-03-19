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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import l9g.account.info.db.model.SdbProperty;
import java.util.Optional;
import l9g.account.info.db.model.SdbSecretData;
import l9g.account.info.db.model.SdbSecretType;
import l9g.account.info.db.model.SdbVaultAdminKey;
import l9g.account.info.service.SignaturePad;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
   * Repository for managing vault admin key entries.
   */
  private final SdbVaultAdminKeyRepository sdbVaultAdminKeyRepository;

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
    }
    else
    {
      log.info("Database already initialized.");
    }
  }

  /**
   * Finds the latest {@link SdbSecretData} entry for a given UUID and type.
   *
   * @param uuid The UUID of the secret data.
   * @param type The type of the secret data.
   * @param hidden The hidden status of the secret data.
   *
   * @return The latest {@link SdbSecretData} object, or null if not found.
   *
   * @throws JsonProcessingException If there is an error processing JSON.
   */
  private SdbSecretData findSdbSecretData(String uuid, SdbSecretType type, boolean hidden)
    throws JsonProcessingException
  {
    Optional<List<SdbSecretData>> optional = sdbSecretDataRepository
      .findByKeyAndTypeAndHiddenOrderByModifyTimestampDesc(uuid, type, hidden);

    SdbSecretData secretData = null;

    if(optional.isPresent() &&  ! optional.get().isEmpty())
    {
      secretData = optional.get().getFirst();
    }

    return secretData;
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
   * Finds a {@link SignaturePad} by its UUID and hidden status.
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
   * Finds a {@link SignaturePad} by its UUID, regardless of its hidden status.
   *
   * @param uuid The UUID of the signature pad.
   *
   * @return The {@link SignaturePad} object, or null if not found.
   *
   * @throws JsonProcessingException If there is an error processing JSON.
   */
  public SignaturePad findSignaturePadByUUIDAny(String uuid)
    throws JsonProcessingException
  {
    return findSignaturePadbyUUID(uuid);
  }

  /**
   * Finds a {@link SignaturePad} by its UUID and hidden status.
   *
   * @param uuid The UUID of the signature pad.
   * @param hidden The hidden status of the signature pad.
   *
   * @return The {@link SignaturePad} object, or null if not found.
   *
   * @throws JsonProcessingException If there is an error processing JSON.
   */
  public SignaturePad findSignaturePadbyUUID(String uuid, boolean hidden)
    throws JsonProcessingException
  {
    SignaturePad signaturePad = null;

    SdbSecretData secretData = findSdbSecretData(
      uuid, SdbSecretType.SIGNATURE_PAD_JSON, hidden);

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
   * Deletes a secret data entry by its ID.
   * Only non-validated signature pads can be deleted.
   *
   * @param id The database ID of the secret data to delete.
   * @throws ResponseStatusException if the data is a validated signature pad or not found.
   */
  public void deleteSecretDataById(String id)
  {
    SdbSecretData secretData = sdbSecretDataRepository.findById(id).orElse(null);
    if(secretData != null)
    {
      if(secretData.getType() == SdbSecretType.SIGNATURE_PAD_JSON)
      {
        try
        {
          SignaturePad pad = objectMapper.readValue(secretData.getSecret(), SignaturePad.class);
          if(pad.isValidated())
          {
            log.error("Attempted to delete validated signature pad: {}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete validated signature pad.");
          }
        }
        catch(JsonProcessingException e)
        {
          log.error("Error parsing signature pad data for deletion: {}", id);
        }
      }
      sdbSecretDataRepository.delete(secretData);
    }
  }

  /**
   * Saves or updates an {@link SdbSecretData} entry in the database.
   *
   * @param secretData The {@link SdbSecretData} object to save.
   */
  public void saveSecretData(DefaultOidcUser principal, SdbSecretData secretData)
  {
    Map<String,String> publisher = new LinkedHashMap<>();
    publisher.put("mail", principal.getEmail());
    publisher.put("name", principal.getFullName());
    publisher.put("username", principal.getPreferredUsername());
    log.debug("saveSecretData publisher={}", publisher);
    try
    {
      secretData.setModifiedBy(objectMapper.writeValueAsString(publisher));
    }
    catch(JsonProcessingException ex)
    {
      log.error("publischer unknown : {}", ex );
    }
    sdbSecretDataRepository.save(secretData);
  }

  /**
   * Finds an {@link SdbSecretData} entry by its database ID and hidden status.
   *
   * @param id The database ID of the secret data.
   * @param hidden The hidden status of the secret data.
   *
   * @return The {@link SdbSecretData} object, or null if not found.
   */
  public SdbSecretData findSdbSecretDataById(String id, boolean hidden)
  {
    return sdbSecretDataRepository.findByIdAndHidden(id, hidden).orElse(null);
  }

  /**
   * Finds a list of {@link SdbSecretData} entries by their type and hidden status, ordered by name in ascending order and modification timestamp in descending order.
   *
   * @param type The type of the secret data to find.
   * @param hidden The hidden status of the secret data.
   *
   * @return A list of {@link SdbSecretData} objects, or null if none found.
   */
  public List<SdbSecretData> findSdbSecretDataByType(SdbSecretType type, boolean hidden)
  {
    return sdbSecretDataRepository.findByTypeAndHiddenOrderByNameAscModifyTimestampDesc(type, hidden).orElse(null);
  }

  /**
   * Finds a list of {@link SdbSecretData} entries by their type, ordered by name in ascending order and modification timestamp in descending order.
   *
   * @param type The type of the secret data to find.
   *
   * @return A list of {@link SdbSecretData} objects, or null if none found.
   */
  public List<SdbSecretData> findSdbSecretDataByType(SdbSecretType type)
  {
    return sdbSecretDataRepository.findByTypeOrderByNameAscModifyTimestampDesc(type).orElse(null);
  }

  /**
   * Finds a list of {@link SdbSecretData} entries by their name, ordered by modification timestamp in descending order.
   *
   * @param name The name associated with the secret data.
   *
   * @return A list of {@link SdbSecretData} objects, or null if none found.
   */
  public List<SdbSecretData> findSdbSecretDataByName(String name)
  {
    return sdbSecretDataRepository.findByNameOrderByModifyTimestampDesc(name).orElse(null);
  }

  /**
   * Retrieves the total count of {@link SdbSecretData} entries for each {@link SdbSecretType}.
   *
   * @return A map where the keys are the string representations of the secret types and the values are the total counts.
   */
  public Map<String, Object> getTotalSecretDataCounts()
  {
    Map<String, Object> counts = new LinkedHashMap<>();
    for(SdbSecretType type : SdbSecretType.values())
    {
      counts.put(type.name(), sdbSecretDataRepository.countByType(type));
    }
    return counts;
  }

  /**
   * Retrieves the count of {@link SdbSecretData} entries for each {@link SdbSecretType} for a specific name.
   *
   * @param name The name associated with the secret data.
   *
   * @return A map where the keys are the string representations of the secret types and the values are the counts for the specified name.
   */
  public Map<String, Object> getSecretDataCountsByName(String name)
  {
    Map<String, Object> counts = new LinkedHashMap<>();
    for(SdbSecretType type : SdbSecretType.values())
    {
      counts.put(type.name(), sdbSecretDataRepository.countByTypeAndName(type, name));
    }
    return counts;
  }

  /**
   * Performs periodic database cleanup tasks.
   * This method is intended to be called by a scheduled job.
   */
  public void cleanupJob()
  {
    log.info("Executing database cleanup...");
    // TODO: implement cleanup logic
  }

  /**
   * Checks if there are any vault admin keys in the database.
   *
   * @return {@code true} if no vault admin keys exist, {@code false} otherwise.
   */
  public boolean vaultAdminKeysIsEmpty()
  {
    return sdbVaultAdminKeyRepository.count() == 0;
  }

  /**
   * Adds a new vault admin key to the database.
   *
   * @param publisher The identifier of the publisher.
   * @param key The {@link SdbVaultAdminKey} to save.
   */
  public void saveVaultAdminKey(String publisher, SdbVaultAdminKey key)
  {
    log.debug("saveVaultAdminKey publisher={}", publisher);
    sdbVaultAdminKeyRepository.save(key);
  }

  /**
   * Finds all vault admin keys for a specific admin ID.
   *
   * @param adminId The identifier of the admin.
   * @return A list of {@link SdbVaultAdminKey} objects.
   */
  public List<SdbVaultAdminKey> findVaultAdminKeysByAdminId(String adminId)
  {
    return sdbVaultAdminKeyRepository.findByAdminIdIgnoreCase(adminId);
  }

  /**
   * Finds all vault admin keys in the database.
   *
   * @return A list of all {@link SdbVaultAdminKey} objects.
   */
  public List<SdbVaultAdminKey> findAllVaultAdminKeys()
  {
    return sdbVaultAdminKeyRepository.findAll();
  }

  /**
   * Deletes a vault admin key by its credential ID.
   *
   * @param credentialId The unique credential identifier.
   */
  @Transactional
  public void deleteVaultAdminKeyByCredentialId(String credentialId)
  {
    log.info("deleteVaultAdminKeyByCredentialId {}", credentialId);
    sdbVaultAdminKeyRepository.deleteByCredentialId(credentialId);
  }
}
