/*
 * Copyright 2025 Thorsten Ludewig <t.ludewig@gmail.com>.
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
package l9g.account.info.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.l9g.crypto.core.CryptoHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import l9g.account.info.db.SdbSecretDataRepository;
import l9g.account.info.db.model.SdbSecretData;
import l9g.account.info.db.model.SdbSecretType;
import l9g.account.info.vault.VaultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for storing and retrieving files in a hierarchical directory structure.
 * Files are encrypted before storage and decrypted upon retrieval using {@link CryptoHandler}.
 * This service also handles directory creation and cleanup.
 *
 * @author Thorsten Ludewig <t.ludewig@gmail.com>
 */
@Service
@Slf4j
public class FileStorageService
{
  /**
   * The base path where files are stored.
   */
  private final Path storageLocationPath;

  private final VaultService vaultService;

  private final SdbSecretDataRepository sdbSecretDataRepository;
  
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Constructs a {@code FileStorageService} and initializes the storage directory.
   * If the specified storage directory does not exist, it will be created.
   *
   * @param storageLocation The base directory path for file storage.
   *
   * @throws IOException If an I/O error occurs during directory creation.
   */
  public FileStorageService(
    @Value("${app.storage-location}") String storageLocation,
    VaultService vaultService, SdbSecretDataRepository sdbSecretDataRepository)
    throws IOException
  {
    this.storageLocationPath = Paths.get(storageLocation);
    this.vaultService = vaultService;
    this.sdbSecretDataRepository = sdbSecretDataRepository;
    Files.createDirectories(this.storageLocationPath);
  }

  /**
   * Generates a hierarchical file path for a given ID.
   * This method creates subdirectories based on parts of the ID to distribute files
   * and avoid very large directories.
   *
   * @param id The unique identifier for which to generate the path.
   *
   * @return The {@link Path} object representing the hierarchical file path.
   */
  private Path getHierarchicalPath(String id)
  {
    String idPart1 = id.substring(0, 2);
    String idPart2 = id.substring(2, 4);
    String idPart3 = id.substring(4, 6);
    return this.storageLocationPath.resolve(idPart1).resolve(idPart2)
      .resolve(idPart3).resolve(id);
  }

  /**
   * Saves the provided {@link SdbSecretData} by encrypting its value and storing it in the file system.
   *
   * @param secretData The {@link SdbSecretData} object containing the value to be saved.
   *
   * @throws IOException If an I/O error occurs during file writing or directory creation.
   * @throws IllegalArgumentException If the {@code secretData} has a null value.
   */
  public void save(SdbSecretData secretData)
    throws IOException
  {
    if(secretData.getValue() == null)
    {
      throw new IllegalArgumentException("Value to save cannot be null");
    }
    try
    {
      Path destinationFile = getHierarchicalPath(secretData.getId());
      Files.createDirectories(destinationFile.getParent());
      Files.write(destinationFile, CryptoHandler.getInstance().encrypt(secretData.getValue()));
    }
    catch(IOException e)
    {
      throw new IOException("Could not store encrypted file " + secretData.getId(), e);
    }
  }

  /**
   * Loads and decrypts the file data associated with the given {@link SdbSecretData} ID.
   *
   * @param secretData The {@link SdbSecretData} object containing the ID of the file to load.
   *
   * @return A byte array containing the decrypted file data.
   *
   * @throws IOException If an I/O error occurs during file reading or decryption.
   */
  public byte[] load(SdbSecretData secretData)
    throws IOException
  {
    log.debug("load {}/{}", secretData.getId(), secretData.getType());
    try
    {
      Path file = getHierarchicalPath(secretData.getId());
      return vaultService.decrypt(Files.readAllBytes(file));
    }
    catch(Throwable e)
    {
      throw new IOException(
        "Could not read encrypted file " + secretData.getId(), e);
    }
  }

  /**
   * Checks if a given directory is empty.
   *
   * @param dir The {@link Path} to the directory to check.
   *
   * @return {@code true} if the directory is empty, {@code false} otherwise.
   *
   * @throws IOException If an I/O error occurs while accessing the directory.
   */
  private boolean isDirEmpty(Path dir)
    throws IOException
  {
    try(var s = Files.list(dir))
    {
      return s.findAny().isEmpty();
    }
  }

  /**
   * Deletes a file and any empty parent directories created for it.
   *
   * @param secretData The {@link SdbSecretData} object containing the ID of the file to delete.
   *
   * @throws IOException If an I/O error occurs during file or directory deletion.
   */
  public void delete(SdbSecretData secretData)
    throws IOException
  {
    try
    {
      Path file = getHierarchicalPath(secretData.getId());
      if(Files.deleteIfExists(file))
      {
        Path parent = file.getParent();
        while(parent != null
          &&  ! parent.equals(this.storageLocationPath)
          && isDirEmpty(parent))
        {
          Files.delete(parent);
          parent = parent.getParent();
        }
      }
    }
    catch(IOException e)
    {
      throw new IOException("Could not delete file " + secretData.getId(), e);
    }
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
    save(data);
    return data;
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
    log.debug("findFileDataById id={}", id);
    Optional<SdbSecretData> optional = sdbSecretDataRepository.findById(id);
    byte[] fileData = null;
    if(optional.isPresent())
    {
      SdbSecretData secretData = optional.get();
      if(secretData.getType() == SdbSecretType.ID_FRONT_IMAGE
        || secretData.getType() == SdbSecretType.ID_BACK_IMAGE)
      {
        fileData = load(secretData);
      }
    }
    
    return fileData;
  }
  
}
