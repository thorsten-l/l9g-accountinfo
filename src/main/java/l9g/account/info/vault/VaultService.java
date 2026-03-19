/*
 * Copyright 2026 Thorsten Ludewig (t.ludewig@gmail.com).
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
package l9g.account.info.vault;

import de.l9g.crypto.core.AES256;
import de.l9g.crypto.core.CryptoHandler;
import static de.l9g.crypto.core.CryptoHandler.AES256_PREFIX;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import javax.crypto.SecretKey;
import l9g.account.info.db.DbService;
import org.springframework.beans.factory.annotation.Value;

/**
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
@Slf4j
@Service
public class VaultService
{
  private final long masterkeyTTL;

  private final DbService dbService;

  private SecretKey masterKey;

  private long masterKeyTimestamp;

  private AES256 aes256;

  public VaultService(@Value("${app.vault.masterkey-ttl}") long masterkeyTTL,
    DbService dbService)
  {
    this.masterkeyTTL = masterkeyTTL;
    this.dbService = dbService;

    log.debug("masterKeyTTL={}", masterkeyTTL);
  }

  public synchronized void addVaultAdminKey(VaultAdminKey key)
  {
    log.debug("addVaultAdminKey");
    dbService.saveVaultAdminKey(key.adminId(), new l9g.account.info.db.model.SdbVaultAdminKey(
      key.adminId(), key.adminId(), key.fullName(), key.description(),
      key.credentialId(), key.prfSalt(), key.encryptedMasterKey()));
  }

  public synchronized List<VaultAdminKey> findVaultAdminKeysByAdminId(String adminId)
  {
    log.debug("findVaultAdminKeysByAdminId");
    return dbService.findVaultAdminKeysByAdminId(adminId).stream()
      .map(VaultAdminKey::new)
      .toList();
  }

  public synchronized List<VaultAdminKey> findAllVaultAdminKeys()
  {
    log.debug("findAllVaultAdminKeys");
    return dbService.findAllVaultAdminKeys().stream()
      .map(VaultAdminKey::new)
      .toList();
  }

  public synchronized boolean adminKeysIsEmpty()
  {
    log.debug("adminKeysIsEmpty");
    return dbService.vaultAdminKeysIsEmpty();
  }

  public long getUnlockTimeLeft()
  {
    long timeLeft = (masterkeyTTL + masterKeyTimestamp
      - System.currentTimeMillis()) / 1000;
    return (timeLeft > 0) ? timeLeft : 0;
  }

  public synchronized SecretKey getUnlockedKey()
  {
    if(masterkeyTTL > 0
      && (System.currentTimeMillis() - masterKeyTimestamp) > masterkeyTTL)
    {
      masterKey = null;
      aes256 = null;
    }
    return masterKey;
  }

  public synchronized void setUnlockedKey(SecretKey masterKey)
  {
    if(masterKey == null)
    {
      throw new VaultSealedException("MasterKey must not be null.");
    }
    this.masterKey = masterKey;
    this.aes256 = new AES256(masterKey.getEncoded());
    this.masterKeyTimestamp = System.currentTimeMillis();
  }

  public synchronized void removeVaultAdminKeyByCredentialId(String credentialId)
  {
    log.debug("removeVaultAdminKeyByCredentialId");
    dbService.deleteVaultAdminKeyByCredentialId(credentialId);
  }

  private void checkVaultIsUnsealed()
  {
    if(aes256 == null)
    {
      throw new VaultSealedException("Vault is seald!");
    }
  }

  public synchronized String encrypt(String plainText)
  {
    checkVaultIsUnsealed();
    return CryptoHandler.AES256_PREFIX + aes256.encrypt(plainText);
  }

  public synchronized String decrypt(String encryptedText)
  {
    checkVaultIsUnsealed();

    String text;

    if(encryptedText != null && encryptedText.startsWith(AES256_PREFIX))
    {
      text = aes256.decrypt(encryptedText.substring(AES256_PREFIX.length()));
    }
    else
    {
      text = encryptedText;
    }

    return text;
  }

  public synchronized byte[] encrypt(byte[] plainData)
  {
    checkVaultIsUnsealed();
    return aes256.encrypt(plainData);
  }

  public synchronized byte[] decrypt(byte[] cryptData)
  {
    log.debug("decrypt");
    checkVaultIsUnsealed();
    return aes256.decrypt(cryptData);
  }
}
