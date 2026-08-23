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

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import l9g.account.info.db.DbService;
import l9g.account.info.db.model.SdbVaultAdminKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VaultService}: seal/unseal transitions, the AES-256
 * round trips and the delegation to {@link DbService}.
 *
 * @author Thorsten Ludewig (t.ludewig@gmail.com)
 */
class VaultServiceTest
{
  private static final long TTL = 120_000L;

  private DbService dbService;

  @BeforeEach
  void setUp()
  {
    dbService = mock(DbService.class);
  }

  private static SecretKey key(int length)
  {
    byte[] raw = new byte[length];
    for(int i = 0; i < length; i ++ )
    {
      raw[i] = (byte)(i + 1);
    }
    return new SecretKeySpec(raw, "AES");
  }

  // --------------------------------------------------------------- unsealing

  @Test
  @DisplayName("a fresh vault is sealed")
  void freshVaultIsSealed()
  {
    VaultService vault = new VaultService(TTL, dbService);

    assertThat(vault.getUnlockedKey()).isNull();
    assertThat(vault.getUnlockTimeLeft()).isZero();
    assertThatThrownBy(() -> vault.encrypt("secret"))
      .isInstanceOf(VaultSealedException.class);
  }

  @Test
  @DisplayName("setUnlockedKey(null) is refused")
  void nullKeyIsRefused()
  {
    VaultService vault = new VaultService(TTL, dbService);

    assertThatThrownBy(() -> vault.setUnlockedKey(null))
      .isInstanceOf(VaultSealedException.class)
      .hasMessageContaining("must not be null");
  }

  @Test
  @DisplayName("a 32 byte key unseals the vault and starts the TTL countdown")
  void thirtyTwoByteKeyUnseals()
  {
    VaultService vault = new VaultService(TTL, dbService);

    vault.setUnlockedKey(key(32));

    assertThat(vault.getUnlockedKey()).isNotNull();
    assertThat(vault.getUnlockTimeLeft())
      .isBetween(TTL / 1000 - 2, TTL / 1000);
  }

  /**
   * The regression guard for the half-unlocked state: {@code AES256} is now
   * constructed before any field is assigned, so a key of the wrong length
   * leaves the vault exactly as it was. Previously {@code masterKey} had already
   * been assigned when the constructor threw, and with expiry disabled
   * ({@code masterkeyTTL <= 0}) that state was permanent —
   * {@link VaultService#getUnlockedKey()} reported a key, which
   * {@code ClientSecurityConfig} and {@code ApiAdminController#deletePerson}
   * read as "the vault is open", while every crypto operation still failed.
   */
  @Test
  @DisplayName("a wrong-length key leaves the vault sealed, also with expiry disabled")
  void wrongKeyLengthLeavesVaultSealed()
  {
    for(long ttl : new long[]
    {
      TTL, 0L
    })
    {
      VaultService vault = new VaultService(ttl, dbService);

      assertThatThrownBy(() -> vault.setUnlockedKey(key(16)))
        .as("ttl=%d", ttl)
        .isInstanceOf(IllegalArgumentException.class);

      assertThat(vault.getUnlockedKey()).as("ttl=%d", ttl).isNull();
      assertThatThrownBy(() -> vault.encrypt("secret"))
        .as("ttl=%d", ttl)
        .isInstanceOf(VaultSealedException.class);
    }
  }

  /**
   * A failed unseal attempt must not disturb an already unsealed vault either.
   */
  @Test
  @DisplayName("a failed unseal attempt does not seal an already open vault")
  void failedUnsealDoesNotDisturbOpenVault()
  {
    VaultService vault = new VaultService(TTL, dbService);
    vault.setUnlockedKey(key(32));
    String encrypted = vault.encrypt("Vertrauliche Daten");

    assertThatThrownBy(() -> vault.setUnlockedKey(key(16)))
      .isInstanceOf(IllegalArgumentException.class);

    assertThat(vault.getUnlockedKey()).isNotNull();
    assertThat(vault.decrypt(encrypted)).isEqualTo("Vertrauliche Daten");
  }

  @Test
  @DisplayName("the key expires after the configured TTL and re-seals the vault")
  void keyExpiresAfterTtl()
    throws Exception
  {
    VaultService vault = new VaultService(1L, dbService);
    vault.setUnlockedKey(key(32));

    Thread.sleep(30L);

    assertThat(vault.getUnlockedKey()).isNull();
    assertThatThrownBy(() -> vault.encrypt("secret"))
      .isInstanceOf(VaultSealedException.class)
      .hasMessage("Vault is seald!");
  }

  @Test
  @DisplayName("a TTL of zero disables expiry entirely")
  void zeroTtlNeverExpires()
    throws Exception
  {
    VaultService vault = new VaultService(0L, dbService);
    vault.setUnlockedKey(key(32));

    Thread.sleep(30L);

    assertThat(vault.getUnlockedKey()).isNotNull();
    assertThat(vault.encrypt("secret")).isNotNull();
    assertThat(vault.getUnlockTimeLeft()).isZero();
  }

  // ------------------------------------------------------------------ crypto

  @Test
  @DisplayName("String encryption prefixes {AES256} and round-trips")
  void stringRoundTrip()
  {
    VaultService vault = new VaultService(TTL, dbService);
    vault.setUnlockedKey(key(32));

    String encrypted = vault.encrypt("Vertrauliche Daten");

    assertThat(encrypted).startsWith("{AES256}");
    assertThat(vault.decrypt(encrypted)).isEqualTo("Vertrauliche Daten");
  }

  @Test
  @DisplayName("byte[] encryption round-trips and carries NO {AES256} prefix")
  void byteRoundTripHasNoPrefix()
  {
    VaultService vault = new VaultService(TTL, dbService);
    vault.setUnlockedKey(key(32));
    byte[] plain = "Vertrauliche Daten".getBytes(StandardCharsets.UTF_8);

    byte[] encrypted = vault.encrypt(plain);

    assertThat(new String(encrypted, StandardCharsets.ISO_8859_1))
      .doesNotStartWith("{AES256}");
    assertThat(vault.decrypt(encrypted)).isEqualTo(plain);
  }

  @Test
  @DisplayName("AES-GCM uses a random IV, so the same plaintext yields different ciphertexts")
  void encryptionIsNonDeterministic()
  {
    VaultService vault = new VaultService(TTL, dbService);
    vault.setUnlockedKey(key(32));

    assertThat(vault.encrypt("same input"))
      .isNotEqualTo(vault.encrypt("same input"));
  }

  @Test
  @DisplayName("decrypt(String) passes unprefixed input through unchanged")
  void decryptPassesUnprefixedInputThrough()
  {
    VaultService vault = new VaultService(TTL, dbService);
    vault.setUnlockedKey(key(32));

    assertThat(vault.decrypt("plain text")).isEqualTo("plain text");
    assertThat(vault.decrypt((String)null)).isNull();
  }

  @Test
  @DisplayName("a tampered ciphertext fails the GCM authentication tag")
  void tamperedCiphertextIsRejected()
  {
    VaultService vault = new VaultService(TTL, dbService);
    vault.setUnlockedKey(key(32));
    byte[] encrypted = vault.encrypt(
      "Vertrauliche Daten".getBytes(StandardCharsets.UTF_8));
    encrypted[encrypted.length - 1] ^= 0x01;

    assertThatThrownBy(() -> vault.decrypt(encrypted))
      .isInstanceOf(RuntimeException.class);
  }

  // -------------------------------------------------------------- delegation

  /**
   * The acting administrator is now recorded as the creator, separately from the
   * key's own {@code adminId}. Previously {@code adminId} was passed for both,
   * so the audit trail named every key its own creator and it was impossible to
   * reconstruct who had enrolled it — relevant for the NIS2 audit trail.
   */
  @Test
  @DisplayName("the acting administrator is recorded as the creator of an admin key")
  void addVaultAdminKeyRecordsTheActingAdmin()
  {
    VaultService vault = new VaultService(TTL, dbService);
    VaultAdminKey adminKey = new VaultAdminKey("admin-1", "Admin One",
      "Yubikey", "cred-id-b64", "salt-b64", "enc-master-key-b64");

    vault.addVaultAdminKey("vaultadmin-boss", adminKey);

    ArgumentCaptor<String> publisher = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<SdbVaultAdminKey> entity =
      ArgumentCaptor.forClass(SdbVaultAdminKey.class);
    verify(dbService).saveVaultAdminKey(publisher.capture(), entity.capture());

    assertThat(publisher.getValue()).isEqualTo("vaultadmin-boss");
    assertThat(entity.getValue().getCreatedBy()).isEqualTo("vaultadmin-boss");
    assertThat(entity.getValue().getAdminId())
      .as("the key still belongs to its own admin")
      .isEqualTo("admin-1");
    assertThat(entity.getValue().getFullName()).isEqualTo("Admin One");
    assertThat(entity.getValue().getCredentialId()).isEqualTo("cred-id-b64");
    assertThat(entity.getValue().getPrfSalt()).isEqualTo("salt-b64");
    assertThat(entity.getValue().getEncryptedMasterKey())
      .isEqualTo("enc-master-key-b64");
  }

  @Test
  @DisplayName("admin key lookups map entities to VaultAdminKey records")
  void adminKeyLookupsMapEntities()
  {
    VaultService vault = new VaultService(TTL, dbService);
    SdbVaultAdminKey entity = new SdbVaultAdminKey("creator", "admin-1",
      "Admin One", "Yubikey", "cred-id", "salt", "enc-key");
    when(dbService.findVaultAdminKeysByAdminId("admin-1"))
      .thenReturn(List.of(entity));
    when(dbService.findAllVaultAdminKeys()).thenReturn(List.of(entity));

    assertThat(vault.findVaultAdminKeysByAdminId("admin-1"))
      .singleElement()
      .isEqualTo(new VaultAdminKey("admin-1", "Admin One", "Yubikey",
        "cred-id", "salt", "enc-key"));
    assertThat(vault.findAllVaultAdminKeys()).hasSize(1);
  }

  @Test
  @DisplayName("adminKeysIsEmpty and removeVaultAdminKeyByCredentialId delegate to DbService")
  void remainingMethodsDelegate()
  {
    VaultService vault = new VaultService(TTL, dbService);
    when(dbService.vaultAdminKeysIsEmpty()).thenReturn(true);

    assertThat(vault.adminKeysIsEmpty()).isTrue();

    vault.removeVaultAdminKeyByCredentialId("cred-id");
    verify(dbService).deleteVaultAdminKeyByCredentialId("cred-id");
  }

}
