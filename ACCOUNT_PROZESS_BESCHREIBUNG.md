# Prozessbeschreibung / Verfahrensbeschreibung — l9g-accountinfo

> **Zweck dieses Dokuments**
> Diese Prozessbeschreibung dokumentiert die Verarbeitungstätigkeiten der Anwendung
> **l9g-accountinfo** als Grundlage für das Verzeichnis von Verarbeitungstätigkeiten
> (Art. 30 DSGVO) und zur Vorlage beim Datenschutzbeauftragten (DSB) und
> Compliance-Officer. Sie beschreibt **was** verarbeitet wird, **wozu**, **wo** die
> Daten liegen und **wie** sie technisch geschützt werden.
>
> **Stand:** 2026-08-23 · **Version:** 2.5.0 · **Branch:** `one-key-to-bind-them`
> **Änderung 2026-06-28:** Zugriffskreis präzisiert (4 benannte RZ-Leitungspersonen,
> phishing-resistente FIDO2-MFA, gleicher Kreis für Log/Graylog); Backup/Datensicherung
> (ZFS, Air-Gap-Schatten-RZ, Restore getestet, 30 Tage Retention) ergänzt inkl.
> Löschung im Backup-Bestand.
> **Grundlage:** automatisierte Quellcode-Analyse. Dies ist eine technische
> Beschreibung, **keine Rechtsberatung**. Die datenschutzrechtliche Bewertung
> obliegt dem DSB.

---

## 1. Überblick / Zweck der Verarbeitung

l9g-accountinfo ist eine Spring-Boot-Webanwendung (Java 21, Spring Boot 3.5.x,
PostgreSQL, Thymeleaf, WebSocket, OAuth2/OIDC) zur Unterstützung von
**Identitäts- und Ausweisprozessen** (Account-/Chipkarten-Ausgabe,
Unterschriftenerfassung über Signature-Pads sowie Übernahme von
Online-Identifizierungs­ergebnissen eines externen eID-/WebID-Dienstes „MIRA").

**Hauptzwecke der Verarbeitung:**

| # | Zweck | Rechtsgrundlage (durch DSB zu bestätigen) |
|---|-------|--------------------------------------------|
| 1 | Ausgabe/Aktivierung von Benutzerkonten und Chipkarten | Art. 6 Abs. 1 lit. b / e |
| 2 | Erfassung handschriftlicher Unterschriften (Signature-Pad) als Nachweis | Art. 6 Abs. 1 lit. b/c; ggf. Art. 9 (biometrisch) |
| 3 | Übernahme und Aufbewahrung von Online-Identifizierungs­ergebnissen (Ausweisdaten, Lichtbild, Video/Audio) | Art. 6 Abs. 1 lit. c (Nachweispflichten); Art. 9 Abs. 2 |
| 4 | Auditierung/Einsichtnahme durch berechtigte Administratoren | Art. 6 Abs. 1 lit. c/f |
| 5 | Verzeichnisabgleich (LDAP) und Löschkonzept | Art. 6 Abs. 1 lit. c/f |

**Betriebsumgebung:** **Traefik, l9g-accountinfo, PostgreSQL** und ein
**error-responder** (nginx) laufen gemeinsam in einem `docker-compose` auf einem
Docker-Host. **In Produktion ist der App-eigene TLS-Endpunkt abgeschaltet**; die
Anwendung lauscht intern auf **HTTP, Port 8080** (Actuator intern 9000). **Traefik**
ist der einzige **TLS-Endpunkt** (ACME-Automatik), mit erzwungenem
HTTP→HTTPS-Redirect, Methoden-Whitelist (GET/HEAD/POST) und Blocklist gefährlicher
Dateiendungen. Der Docker-Host liegt im **ungerouteten privaten Netz (10/8)** und
ist per **IP-Filter nur über 80/443** (= Traefik) erreichbar. Konfiguration und
Schlüssel (`secret.bin`, `server.p12`) liegen im gemounteten Volume **`./data`**.
Die **Container-Logs werden per GELF an einen Graylog-Cluster** ausgeleitet
(zentrale Protokollierung). Der App-Container ist zusätzlich am Netz **`net108`
(`141.41.8.112`)** angebunden — **per IP-Filter ausschließlich ausgehend**
(eingehend blockiert), nur für die Outbound-Anbindung an LDAP/IdP/eID.
**Administrativer Zugang zum Docker-Host** erfolgt **ausschließlich per SSH** und ist
auf **vier namentlich benannte RZ-Leitungspersonen** beschränkt (Leiter des
Rechenzentrums, stellv. Leiter des Rechenzentrums, Leiter der RZ-IT-Gruppe
Infrastruktur, Leiter der RZ-IT-Gruppe Softwareentwicklung); die SSH-Authentisierung
nutzt einen **FIDO2-gebundenen Schlüssel** (`id_ed25519_sk`), der über einen
**YubiKey 5C BIO** (Fingerabdruck) freigegeben wird (phishing-resistente MFA).
**Derselbe 4-Personen-Kreis** verfügt über den **lokalen Logzugriff** und den Zugriff
auf das **Graylog**. **Datensicherung:** Die Datenhaltung liegt auf **ZFS**; per
**`zfs send/receive`** wird eine Snapshot-Historie inkl. valider Offsite-Kopie auf
einen **Air-Gap-Server im Schatten-RZ** (>1 km, eigenes 96-faser-Dark-Fiber-Netz)
repliziert (**Snapshot-Retention 30 Tage**); das **Restore-Verfahren ist dokumentiert
und getestet**.
*(Sicherheitsdetails siehe ACCOUNT_COMPLIANCE.md.)*

> **Hinweis:** Verarbeitet werden **besondere Kategorien personenbezogener Daten**
> nach **Art. 9 DSGVO** (biometrische Daten: Lichtbild, Unterschrift, Video/Audio
> zur Identifizierung). Hierfür ist eine gesonderte Zulässigkeitsprüfung und ggf.
> eine **Datenschutz-Folgenabschätzung (Art. 35 DSGVO)** erforderlich.

---

## 2. Betroffene Personengruppen

- **Endkunden / Antragsteller** (Personen, die identifiziert werden bzw. eine
  Karte/ein Konto erhalten) — Quelle: Signature-Pad, externer eID-Dienst, LDAP.
- **Mitarbeitende / Sachbearbeiter** (Publisher), die Vorgänge ausführen —
  protokolliert in Logs und LDAP-Aktivitätsprotokollen.
- **Administratoren** (ADMIN, AUDITADMIN, VAULTADMIN, TABADMIN) — Audit-Metadaten.

---

## 3. Datenkategorien (Dateninventar)

### 3.1 Datenbank-Entitäten (PostgreSQL)

| Entität / Tabelle | Felder mit Personenbezug | Verschlüsselung |
|---|---|---|
| `SdbSecretData` (`secretdata`) | `name` (Benutzername), `description` (JSON: Name, Mail), `key`, Audit-Felder; **`secret`** (verschlüsselte Nutzlast: Signatur-JWT, Identifizierungs­status-JSON) | `secret` **feldverschlüsselt** via `@Convert(EncryptedAttributeConverter)` (crypto-jpa) — `SdbSecretData.java:122` |
| `SdbLastSeen` (`lastseen`) | `username`, `firstname`, `lastname`, `mail`, `timestamp` | **unverschlüsselt** (Klartext in DB) — `SdbLastSeen.java` |
| `SdbVaultAdminKey` (`vaultadminkeys`) | `adminId`, `fullName`, `description`, `credentialId`, `encryptedMasterKey` | Masterkey clientseitig (WebAuthn/PRF) verschlüsselt |
| `SdbProperty` (`properties`) | i. d. R. kein Personenbezug | — |

Basisklasse `SdbUuidObject` liefert allen Entitäten: `id` (UUID), `createdBy`,
`modifiedBy`, `createTimestamp`, `modifyTimestamp`, `immutable`, `hidden`,
`pendingDeletionCount`.

### 3.2 Dateisystem-Speicher (verschlüsselte Dateien)

Pfad: `app.storage.location` (Default `./data/secrets`). Ablage in
hierarchischer Verzeichnisstruktur nach UUID
(`FileStorageService.getHierarchicalPath`). **Dateiverschlüsselung: AES-256-GCM**
(`CryptoHandler` / `de.l9g.crypto.core`), Schlüssel aus `data/secret.bin`.

| Datentyp (`SdbSecretType`) | Inhalt | Art. 9? |
|---|---|---|
| `ID_FRONT_IMAGE`, `ID_BACK_IMAGE` | Ausweis-Scan Vorder-/Rückseite | **ja (biometrisch/Ausweis)** |
| `ID_SIGNATURE_JWT` | Signiertes JWT mit Unterschrift (PNG/SVG), Name, Mail | **ja (biometrisch)** |
| `SIGNATURE_PAD_JSON` | Signature-Pad-Konfiguration inkl. Nutzerkontext | tlw. |
| `EXT_IDENTIFICATION_STATUS` | eID-Statusobjekt (JSON, feldverschlüsselt) — siehe 3.3 | **ja** |
| `EXT_IDENTIFICATION_ARCHIVE` | **ZIP** mit Pass-Scans, Portrait, Audio (mp3), Video (webm), PDF | **ja (umfassend, biometrisch)** |

### 3.3 Inhalt des eID-Statusobjekts (`IdentificationStatus` DTO)

Umfangreiche Ausweis- und Identitätsdaten, u. a.:

- **Person:** Anrede, Geschlecht, Vorname, Nachname, **Geburtsdatum**, Adresse
  (Straße, PLZ, Ort, Region, Land), Kontakt (E-Mail, Mobil, Telefon).
- **Ausweisdokument:** Dokumenttyp, Behörde, Ausstell-/Ablaufdatum,
  Staatsangehörigkeit, **Dokumentnummer**, **MRZ (maschinenlesbare Zone, alle
  Zeilen)**, Geburtsname, Geburtsort.
- **Biometrie:** `passImages`, `portraitImages`; im Archiv zusätzlich Audio/Video.

### 3.4 LDAP-Bezug (Verzeichnisdienst)

Aus LDAP gelesen/geschrieben (`LdapService`): Vor-/Nachname, UID, Mail,
Geburtsdatum, Barcode/Kundennummer, Adressen, Lichtbild (`jpegPhoto`); geschrieben
werden Chipkarten-Status, Ausgeber, Zeitstempel und ein **Aktivitätsprotokoll**
(`soniaUserLog`: Zeitstempel | Remote-IP | Publisher | Aktion | Gerät).

---

## 4. Datenflüsse (Prozesse)

### 4.1 Unterschriftenerfassung (Signature-Pad)
```
Endkunde unterschreibt am Pad
  → Browser/Pad lädt Daten hoch (WebSocket / REST)
  → FileStorageService.saveSecretFileData() / DbService.saveSignedJWT()
  → SdbSecretData (immutable=true) + AES-256-Datei / feldverschlüsseltes JWT
  → Audit-Log: PAD_START / SIGNATURE_OK / SIGNATURE_CANCEL (INFO)
```

### 4.2 Übernahme Online-Identifizierung (externer eID-Dienst → Storage-API)
```
Externer Dienst (MIRA) → POST /api/v1/storage/objects
  Authentisierung: statisches Bearer-Token (konstante-Zeit-Vergleich)
                 + HMAC-SHA256 über "<Zeitstempel>." + Body
                 + Frischefenster auf den Zeitstempel (Default 5 min)
                 + Einmal-Nutzung jeder Signatur (Replay-Schutz)
  → StorageController.receiveObject()
  ├─ EXT_IDENTIFICATION_STATUS → FileStorageService.saveSecretData()
  │     → feldverschlüsseltes JSON in SdbSecretData.secret (Vault-Masterkey)
  └─ EXT_IDENTIFICATION_ARCHIVE → FileStorageService.saveSecretRawData()
        → AES-256-verschlüsselte ZIP-Datei im Dateisystem
  → Audit-Log: STORAGE_UPLOAD (INFO, enthält Benutzername)
```

### 4.3 Einsichtnahme/Audit durch Administratoren
```
Admin (ADMIN/AUDITADMIN) öffnet /admin/useraudit
  → Vault muss entsiegelt sein (Masterkey-TTL)
  → ApiAdminController: audit/person/{uid}, id.jpeg, signature.png/svg,
    userinfo.json, archive.json, archive/file (PDF inline)
  → Entschlüsselung on-the-fly über VaultService
```
> **Zugriffsbeschränkung:** **Standard-Administratoren (Rolle ADMIN) können die
> Personen-Audit-Daten nicht einsehen.** Die Einsicht ist der Rolle **AUDITADMIN**
> vorbehalten und setzt eine **Entsiegelung des Vaults** voraus, die per
> **YubiKey 5C BIO** (WebAuthn/PRF, biometrische Freigabe) erfolgt und auf **180 s**
> begrenzt ist; danach **versiegelt der Vault automatisch** und der Klartext-Zugriff
> endet.
>
> **Audit-Trail:** Jeder lesende Admin-Zugriff erzeugt einen `AUDIT_READ`-Eintrag
> auf INFO-Ebene (Admin, Aktion, Ziel) und wird per GELF zentral in **Graylog**
> protokolliert. Im Personen-Audit hinterlegt sind außerdem der **Metadaten-Export**
> und die **vollständige Löschung** der Daten; diese Vorgänge werden ebenso
> protokolliert (`USER_EXPORT`, `USER_ERASE_MANUAL`).

### 4.4 Verzeichnisabgleich & Löschkonzept
```
Scheduler (scheduler.db-deletion.cron)
  1) updateLastSeenFromLdap()
       LdapService.listLastSeenUsers() → SdbLastSeen upsert (timestamp = jetzt)
  2) deleteExpiredUsers()
       DbService.findExpiredLastSeen(P90D) → abgelaufene Nutzer
       je Nutzer: DbService.deleteUserData(user, fileStorageService)
         → löscht alle SdbSecretData-Datensätze + zugehörige Dateien
         → löscht SdbLastSeen-Eintrag (nur bei vollständigem Erfolg)
         → Audit-Log USER_DELETED
```

---

## 5. Empfänger / Schnittstellen

| Empfänger / System | Daten | Richtung |
|---|---|---|
| OAuth2/OIDC-Provider (`idp.sonia.de` / Keycloak) | Authentifizierung, Rollen-Claims | bidirektional |
| LDAP-Verzeichnis | Benutzer-/Karten-/Adressdaten, Aktivitätslog | bidirektional |
| Externer eID-/WebID-Dienst (MIRA) | Identifizierungs­ergebnisse, Ausweis-/Biometriedaten | eingehend (Storage-API) |
| Signature-Pads (Geräte) | Unterschrift, Lichtbild | eingehend (WebSocket/REST) |
| PostgreSQL-Datenbank | Persistenz | intern |
| Lokales Dateisystem (`data/secrets`) | verschlüsselte Dateien | intern |
| Graylog-Cluster (via Docker-GELF) | Betriebs-/Audit-Logs (enthält Personenbezug, INFO/WARN) | ausgehend |
| Log-Datei `data/accountinfo.log` | Betriebslogs (Personenbezug), ggf. redundant zu Graylog | intern |
| Air-Gap-Backup-Server (Schatten-RZ) | ZFS-Snapshots (verschlüsselte Datenbestände) via `zfs send/receive` | ausgehend (eigenes Dark-Fiber-Netz) |

> Es findet (lt. Code) **keine** Drittlandübermittlung durch die Anwendung selbst
> statt; Betriebsstandort/Hosting ist organisatorisch zu dokumentieren.

---

## 6. Technische Schutzmaßnahmen (TOM, Kurzfassung)

| Maßnahme | Umsetzung |
|---|---|
| Verschlüsselung at-rest (Felder) | AES via crypto-jpa, Schlüssel = Vault-Masterkey |
| Verschlüsselung at-rest (Dateien) | AES-256-GCM (`CryptoHandler`), Schlüssel `data/secret.bin` (0400) |
| Vault / Schlüsselverwaltung | In-Memory-Masterkey mit TTL (Default 120 s, **prod 180 s**), Entsiegelung über **WebAuthn/PRF**-Admin-Keys (`SdbVaultAdminKey`), freigegeben per **YubiKey 5C BIO** (biometrisch); **automatische Versiegelung nach 180 s** |
| Authentifizierung | OAuth2/OIDC mit PKCE |
| Autorisierung | Rollen ADMIN/AUDITADMIN/VAULTADMIN/TABADMIN/PUBLISHER, `@PreAuthorize`, Vault-Entsiegelung für Audit; **Standard-Admins (ADMIN) ohne Einsicht in Personen-Audit-Daten** (nur AUDITADMIN bei entsiegeltem Vault) |
| Zugriffskontrolle (Host/Betrieb) | Docker-Host **nur per SSH**, beschränkt auf **vier namentlich benannte RZ-Leitungspersonen** (Leiter RZ, stellv. Leiter RZ, Leiter RZ-IT Infrastruktur, Leiter RZ-IT Softwareentwicklung); SSH-Authentisierung über **phishing-resistente FIDO2-Hardware-Schlüssel** (`id_ed25519_sk` + YubiKey 5C BIO/Fingerprint); derselbe Kreis hält Log-/Graylog-Zugriff |
| Backup / Datenresilienz | Datenhaltung auf **ZFS** (End-to-End-Prüfsummen, Snapshots); **`zfs send/receive`-Replikation** auf **Air-Gap-Server im Schatten-RZ** (>1 km, eigenes 96-faser-Dark-Fiber-Netz) mit Snapshot-Historie + valider Offsite-Kopie (**Retention 30 Tage**); **Restore dokumentiert und getestet** (RTO/RPO) |
| API-Schutz (Storage) | Bearer-Token (konstante-Zeit-Vergleich) + HMAC-SHA256 über Zeitstempel und Body + **Frischefenster** (`app.storage.api.timestamp-tolerance`, Default 5 min) + **Einmal-Nutzung der Signatur** (`app.storage.api.replay-protection`) |
| Integrität | SHA-256-Prüfsummen je Objekt; HMAC bei Upload; **292 automatisierte Tests** auf den sicherheits- und löschrelevanten Pfaden (siehe `ACCOUNT_SOURCECODE_STATISTIK.md`, `DEFEKTE.md`) |
| Sitzungsbeendigung | OIDC-Backchannel-Logout; das Logout-Token wird **gegen das JWKS des IdP verifiziert** (Signatur, `iss`, `exp`, `nbf`, `aud`, `events`-Claim, kein `nonce`), bevor eine Sitzung beendet wird |
| LDAP-Anfragen | Alle aus Anfragen stammenden Filterwerte werden **zentral per Whitelist bereinigt** (`LdapService.sanitizeFilterValue`) — Buchstaben/Ziffern jeder Schrift, Space, Bindestrich, Apostroph, Punkt |
| Transportverschlüsselung | **TLS-Edge über Traefik** (ACME DNS-01, automatische Zertifikatserneuerung); App intern HTTP/8080 im **ungerouteten 10/8-Netz** |
| Unveränderbarkeit | `immutable`-Flag auf Identifizierungs­artefakten — verhindert **Änderung** (Integritätsschutz), **Löschung ist möglich** (Art. 17 erfüllbar) |

---

## 7. Aufbewahrung & Löschung

- **Löschkonzept (implementiert):** `SdbLastSeen` + Karenzzeit
  `delete-grace-period: P90D` (`ldap.configuration.user`). Der
  `DbDeletionScheduler` aktualisiert täglich die Zeitstempel aktiver LDAP-Nutzer
  und **löscht für jeden abgelaufenen Nutzer** (`now − lastSeen > P90D`) **alle
  `SdbSecretData`-Datensätze, die zugehörigen verschlüsselten Dateien und den
  `SdbLastSeen`-Eintrag** (`DbService.deleteUserData`). Robust/idempotent: Datei
  vor DB-Zeile, `SdbLastSeen` erst nach vollständiger Löschung; Audit-Log
  `USER_DELETED`.
- **Identifizierungs­archive / Biometrie:** `immutable=true` schützt gegen
  **Änderung**, **nicht** gegen Löschung → werden vom Löschjob mit erfasst.
- **Aufbewahrungsfrist:** technische Standardfrist = **P90D** ab letztem
  LDAP-Kontakt. Ob je Datenart abweichende gesetzliche Fristen gelten, ist
  fachlich/rechtlich (DSB) zu bestätigen.
- **Datensicherung / Verfügbarkeit:** Die verschlüsselten Datenbestände werden per
  **ZFS-Snapshots + `zfs send/receive`** auf einen **Air-Gap-Server im Schatten-RZ**
  repliziert (Offsite-Kopie, **Snapshot-Retention 30 Tage**, Restore getestet) →
  Schutz vor Datenverlust und Ransomware. **Löschung im Backup-Bestand:** Gelöschte
  Datensätze (Karenzzeit-Löschung bzw. Art.-17-Sofort-Erasure) bleiben in Snapshots
  längstens **30 Tage** erhalten und rotieren dann automatisch heraus. Da die
  **Snapshot-Retention (30 Tage) deutlich kürzer ist als die Löschkarenz (P90D)**,
  greift die Löschung fristgerecht auch im Backup; ein gesondertes Eingreifen in
  Snapshots ist nicht erforderlich.

---

## 8. Rechte der betroffenen Personen (Ist-Stand)

| Recht | Status im System |
|---|---|
| Auskunft (Art. 15) | Admin-Einsicht + **Metadaten-Export als ZIP** (entschlüsselt, ohne Publisher/Binärdaten) über `/admin/useraudit` |
| Datenübertragbarkeit (Art. 20) | Metadaten-Export (JSON in ZIP) vorhanden; ohne Binärdaten |
| Löschung (Art. 17) | **automatisch nach Karenzzeit (P90D)** und **manuelle Sofort-Erasure** pro Person (Admin-Button), jeweils inkl. Dateien |
| Berichtigung (Art. 16) | für Identifizierungs­artefakte durch `immutable` gesperrt (sachgerecht: löschen + neu erfassen) |
| Einschränkung (Art. 18) | `hidden`-Flag nur als UI-Sichtbarkeit, nicht als Verarbeitungseinschränkung |

> Detaillierte Bewertung und Maßnahmen siehe **ACCOUNT_COMPLIANCE.md**.

---

## 9. Verantwortlichkeiten (auszufüllen durch Organisation)

| Rolle | Name / Stelle |
|---|---|
| Verantwortlicher (Art. 4 Nr. 7) | _________________ |
| Datenschutzbeauftragter | _________________ |
| Compliance-Officer | _________________ |
| Technischer Betrieb / Admin | _________________ |
| Auftragsverarbeiter (eID-Dienst, Hosting) | _________________ |
