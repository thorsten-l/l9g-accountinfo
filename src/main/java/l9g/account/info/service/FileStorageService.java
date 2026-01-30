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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import l9g.account.info.crypto.CryptoHandler;
import l9g.account.info.db.model.SdbSecretData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 *
 * @author Thorsten Ludewig <t.ludewig@gmail.com>
 */
@Service
@Slf4j
public class FileStorageService
{
  private final Path storageLocationPath;

  public FileStorageService(
    @Value("${app.storage-location}") String storageLocation)
    throws IOException
  {
    this.storageLocationPath = Paths.get(storageLocation);
    Files.createDirectories(this.storageLocationPath);
  }

  private Path getHierarchicalPath(String id)
  {
    String idPart1 = id.substring(0, 2);
    String idPart2 = id.substring(2, 4);
    String idPart3 = id.substring(4, 6);
    return this.storageLocationPath.resolve(idPart1).resolve(idPart2)
      .resolve(idPart3).resolve(id);
  }

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

  public byte[] load(SdbSecretData secretData)
    throws IOException
  {
    try
    {
      Path file = getHierarchicalPath(secretData.getId());
      return CryptoHandler.getInstance().decrypt(Files.readAllBytes(file));
    }
    catch(IOException e)
    {
      throw new IOException(
        "Could not read encrypted file " + secretData.getId(), e);
    }
  }

  private boolean isDirEmpty(Path dir)
    throws IOException
  {
    try(var s = Files.list(dir))
    {
      return s.findAny().isEmpty();
    }
  }

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

}
