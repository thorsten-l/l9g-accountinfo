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
package l9g.account.info.crypto;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
public class AppSecretKey
{
  private static final Path SECRET_PATH = Path.of("data/secret.bin");

  private static final int KEY_LEN = AES256.KEY_LEN_BYTES; // 32

  private AppSecretKey(byte[] secretKey)
  {
    this.secretKey = secretKey;
  }

  public static AppSecretKey getInstance()
  {
    return Holder.INSTANCE;
  }

  private static final class Holder
  {
    private static final AppSecretKey INSTANCE = loadOrCreate();

  }

  private static AppSecretKey loadOrCreate()
  {
    try
    {
      Files.createDirectories(SECRET_PATH.getParent());

      if(Files.exists(SECRET_PATH))
      {
        byte[] secretKey = Files.readAllBytes(SECRET_PATH);
        log.debug("Loading secret file");
        return new AppSecretKey(secretKey);
      }

      byte[] secretKey = new byte[KEY_LEN];
      new SecureRandom().nextBytes(secretKey);
      log.info("Writing secret file");
      Files.write(SECRET_PATH, secretKey, StandardOpenOption.CREATE_NEW);

      File secretFile = SECRET_PATH.toFile();

      // file permissions - r-- --- ---
      secretFile.setExecutable(false, false);
      secretFile.setWritable(false, false);
      secretFile.setReadable(false, false);
      secretFile.setReadable(true, true);

      return new AppSecretKey(secretKey);
    }
    catch(IOException e)
    {
      log.error("ERROR: secret file ", e);
      System.exit(-1);
    }
    
    return null;
  }

  public byte[] getSecretKey() // mutable copy 
  {
    return Arrays.copyOf(secretKey, secretKey.length);
  }

  private byte[] secretKey;

}
