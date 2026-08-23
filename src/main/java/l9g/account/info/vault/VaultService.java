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

  /**
   * Stores a new vault admin key.
   * <p>
   * {@code createdBy} is recorded separately from the key's own
   * {@code adminId}: previously {@code adminId} was passed for both, so the
   * audit trail named every key its own creator and it was impossible to
   * reconstruct which administrator had enrolled it.
   *
   * @param createdBy The acting administrator, recorded as the creator of the
   * entry.
   * @param key The admin key to store.
   */
  public synchronized void addVaultAdminKey(String createdBy, VaultAdminKey key)
  {
    log.debug("addVaultAdminKey createdBy={}", createdBy);
    dbService.saveVaultAdminKey(createdBy, new l9g.account.info.db.model.SdbVaultAdminKey(
      createdBy, key.adminId(), key.fullName(), key.description(),
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

  /**
   * Unseals the vault with the given master key.
   * <p>
   * The cipher is constructed <em>before</em> any field is assigned. {@code AES256}
   * rejects a key that is not exactly 32 bytes long, and assigning
   * {@code masterKey} first left the service half-unlocked on that error:
   * {@link #getUnlockedKey()} reported a key — which callers read as "the vault
   * is open" — while every crypto operation still failed with
   * {@link VaultSealedException}. Either the vault is fully unsealed now, or
   * nothing changed at all.
   *
   * @param masterKey The AES-256 master key, exactly 32 bytes.
   *
   * @throws VaultSealedException If {@code masterKey} is {@code null}.
   * @throws IllegalArgumentException If the key does not have the required
   * length; the vault then stays in its previous state.
   */
  public synchronized void setUnlockedKey(SecretKey masterKey)
  {
    if(masterKey == null)
    {
      throw new VaultSealedException("MasterKey must not be null.");
    }

    AES256 cipher = new AES256(masterKey.getEncoded());

    this.masterKey = masterKey;
    this.aes256 = cipher;
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
