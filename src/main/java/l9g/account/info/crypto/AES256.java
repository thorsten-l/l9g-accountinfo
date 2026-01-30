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

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
public class AES256
{
  private final static Logger LOGGER = LoggerFactory.getLogger(
    AES256.class.getName());

  private final static String KEY_ALGORITHM = "AES";

  private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

  public static final int KEY_LEN_BYTES = 32;  // 256 bit

  private static final int IV_LEN_BYTES = 12;  // GCM recommended

  private static final int TAG_LEN_BITS = 128; // 16 bytes auth tag

  private final SecretKey key;

  private final SecureRandom secureRandom = new SecureRandom();

  public AES256()
    throws NoSuchAlgorithmException
  {
    KeyGenerator keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM);
    keyGenerator.init(256);
    this.key = keyGenerator.generateKey();
  }

  /**
   * encodedSecretBytes must be exactly 32 bytes (AES-256 key).
   */
  public AES256(byte[] encodedSecretBytes)
  {
    if(encodedSecretBytes == null || encodedSecretBytes.length != KEY_LEN_BYTES)
    {
      throw new IllegalArgumentException("Secret must be " + KEY_LEN_BYTES + " bytes (AES-256 key)");
    }
    this.key = new SecretKeySpec(encodedSecretBytes, KEY_ALGORITHM);
  }

  public AES256(String encodedSecret)
  {
    this(Base64.getDecoder().decode(encodedSecret));
  }

  public String encrypt(String plainText)
  {
    try
    {
      byte[] iv = new byte[IV_LEN_BYTES];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));

      byte[] ctWithTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      // output = IV || (ciphertext||tag)
      byte[] out = new byte[IV_LEN_BYTES + ctWithTag.length];
      System.arraycopy(iv, 0, out, 0, IV_LEN_BYTES);
      System.arraycopy(ctWithTag, 0, out, IV_LEN_BYTES, ctWithTag.length);

      return Base64.getEncoder().encodeToString(out);
    }
    catch(Exception ex)
    {
      LOGGER.error("Encryption failed", ex);
      throw new IllegalStateException("Encryption failed", ex);
    }
  }

  public String decrypt(String encryptedText)
  {
    try
    {
      byte[] in = Base64.getDecoder().decode(encryptedText);

      if(in.length < IV_LEN_BYTES + 16)
      {
        throw new IllegalArgumentException("Encrypted payload too short");
      }

      byte[] iv = java.util.Arrays.copyOfRange(in, 0, IV_LEN_BYTES);
      byte[] ctWithTag = java.util.Arrays.copyOfRange(in, IV_LEN_BYTES, in.length);

      Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));

      byte[] pt = cipher.doFinal(ctWithTag);
      return new String(pt, StandardCharsets.UTF_8);
    }
    catch(Exception ex)
    {
      LOGGER.error("Decryption failed", ex);
      throw new IllegalStateException("Decryption failed", ex);
    }
  }

  public byte[] encrypt(byte[] plainData)
  {
    try
    {
      byte[] iv = new byte[IV_LEN_BYTES];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));

      byte[] ctWithTag = cipher.doFinal(plainData);

      byte[] out = new byte[IV_LEN_BYTES + ctWithTag.length];
      System.arraycopy(iv, 0, out, 0, IV_LEN_BYTES);
      System.arraycopy(ctWithTag, 0, out, IV_LEN_BYTES, ctWithTag.length);

      return out;
    }
    catch(Exception ex)
    {
      LOGGER.error("Encryption failed", ex);
      throw new IllegalStateException("Encryption failed", ex);
    }
  }

  public byte[] decrypt(byte[] encryptedData)
  {
    try
    {
      if(encryptedData.length < IV_LEN_BYTES + 16)
      {
        throw new IllegalArgumentException("Encrypted payload too short");
      }

      byte[] iv = java.util.Arrays.copyOfRange(encryptedData, 0, IV_LEN_BYTES);
      byte[] ctWithTag = java.util.Arrays.copyOfRange(encryptedData, IV_LEN_BYTES, encryptedData.length);

      Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));

      return cipher.doFinal(ctWithTag);
    }
    catch(Exception ex)
    {
      LOGGER.error("Decryption failed", ex);
      throw new IllegalStateException("Decryption failed", ex);
    }
  }

  /**
   * Returns only the raw AES-256 key bytes (32 bytes).
   */
  public byte[] getSecret()
  {
    byte[] keyBytes = key.getEncoded();
    if(keyBytes.length != KEY_LEN_BYTES)
    {
      // sollte bei AES-256 nicht passieren, aber besser explizit
      throw new IllegalStateException("Unexpected key length: " + keyBytes.length);
    }
    return keyBytes;
  }

  public String getEncodedSecret()
  {
    return Base64.getEncoder().encodeToString(getSecret());
  }

}
