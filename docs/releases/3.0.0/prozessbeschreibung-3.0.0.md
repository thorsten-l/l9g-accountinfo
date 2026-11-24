# Prozessbeschreibung / Verzeichnis von Verarbeitungstätigkeiten – l9g-accountinfo 3.0.0

*Automatisch erzeugt am 2026-11-24 (`mvn -Prelease verify`) als Release-Nachweis nach Art. 30 DSGVO. Die ausführliche Verfahrensbeschreibung mit Betriebsumgebung, Datenflüssen und Betroffenenrechten steht in `ACCOUNT_PROZESS_BESCHREIBUNG.md`. Technische Beschreibung, keine Rechtsberatung; die datenschutzrechtliche Bewertung obliegt dem DSB.*

## 1. Zweck der Anwendung

l9g-accountinfo unterstützt Identitäts- und Ausweisprozesse: Ausgabe und Aktivierung von Benutzerkonten und Chipkarten, Erfassung handschriftlicher Unterschriften über Signature-Pads und Übernahme von Online-Identifizierungsergebnissen eines externen eID-Dienstes. Verarbeitet werden auch **besondere Kategorien personenbezogener Daten (Art. 9 DSGVO)**: Lichtbild, Unterschrift, Ausweisdaten, Video und Audio.

## 2. Gespeicherte Datentypen

Je Wert des Enums `SdbSecretType`; Beschreibungen aus `docs/compliance/processing-activities.yaml`.

| Datentyp (`SdbSecretType`) | Inhalt | Ablage | Art. 9 |
|---|---|---|---|
| `SIGNATURE_PAD_JSON` | Konfiguration eines Signature-Pads (öffentlicher Schlüssel, Client-Umgebung) | secretdata.secret (feldverschlüsselt) | nein |
| `ID_SIGNATURE_JWT` | Signiertes JWT mit Unterschrift (PNG/SVG), Name, Mail | secretdata.secret (feldverschlüsselt) | **ja** |
| `ID_FRONT_IMAGE` | Ausweis-Scan Vorderseite | Datei (AES-256-GCM) | **ja** |
| `ID_BACK_IMAGE` | Ausweis-Scan Rückseite | Datei (AES-256-GCM) | **ja** |
| `EXT_IDENTIFICATION_STATUS` | Identifizierungsstatus des eID-Dienstes (Ausweis-, Adress- und Kontaktdaten) | secretdata.secret (feldverschlüsselt) | **ja** |
| `EXT_IDENTIFICATION_ARCHIVE` | ZIP mit Pass-Scans, Portrait, Audio, Video und PDF der Online-Identifizierung | Datei (AES-256-GCM) | **ja** |


## 3. Datenmodell

Ermittelt per Reflection über alle JPA-Entities (inklusive geerbter Spalten aus `SdbUuidObject`).

| Entity | Tabelle | Spalten | verschlüsselt | personenbezogen |
|---|---|---|---|---|
| SdbLastSeen | `lastseen` | 6 | 0 | 5 |
| SdbProperty | `properties` | 10 | 0 | 2 |
| SdbSecretData | `secretdata` | 15 | 1 | 5 |
| SdbVaultAdminKey | `vaultadminkeys` | 14 | 0 | 4 |


### 3.1 Personenbezogene Spalten

Gekennzeichnet mit `@PersonalData` im Code.

| Tabelle | Spalte | Kategorie | Art. 9 | Zweck | Schutz |
|---|---|---|---|---|---|
| `lastseen` | `username` | Identifikation | nein | Verzeichnisabgleich für das Löschkonzept | Klartext, Zugriffskontrolle |
| `lastseen` | `firstname` | Identifikation | nein | Anzeige im Personen-Audit | Klartext, Zugriffskontrolle |
| `lastseen` | `lastname` | Identifikation | nein | Anzeige im Personen-Audit | Klartext, Zugriffskontrolle |
| `lastseen` | `mail` | Kontakt | nein | Anzeige im Personen-Audit | Klartext, Zugriffskontrolle |
| `lastseen` | `last_seen_timestamp` | Verzeichnisabgleich | nein | Beginn der Löschkarenz (letzter Kontakt im Verzeichnis) | Klartext, Zugriffskontrolle |
| `properties` | `created_by` | Beschäftigtendaten | nein | Nachweis, wer den Datensatz angelegt hat (Publisher: Name, Benutzername, Mail) | Klartext, Zugriffskontrolle |
| `properties` | `modified_by` | Beschäftigtendaten | nein | Nachweis, wer den Datensatz zuletzt geändert hat (Publisher: Name, Benutzername, Mail) | Klartext, Zugriffskontrolle |
| `secretdata` | `created_by` | Beschäftigtendaten | nein | Nachweis, wer den Datensatz angelegt hat (Publisher: Name, Benutzername, Mail) | Klartext, Zugriffskontrolle |
| `secretdata` | `modified_by` | Beschäftigtendaten | nein | Nachweis, wer den Datensatz zuletzt geändert hat (Publisher: Name, Benutzername, Mail) | Klartext, Zugriffskontrolle |
| `secretdata` | `name` | Identifikation | nein | Zuordnung der Nachweise zur Person (Benutzername; bei Pad-Datensätzen der Pad-Name) | Klartext, Zugriffskontrolle |
| `secretdata` | `description` | Kontakt | nein | Anzeige im Personen-Audit (Name, Mail als JSON) | Klartext, Zugriffskontrolle |
| `secretdata` | `secret` | Identifikationsnachweis | **ja** | Signatur-JWT mit Unterschrift bzw. Identifizierungsstatus des eID-Dienstes | AES-256 feldverschlüsselt |
| `vaultadminkeys` | `created_by` | Beschäftigtendaten | nein | Nachweis, wer den Datensatz angelegt hat (Publisher: Name, Benutzername, Mail) | Klartext, Zugriffskontrolle |
| `vaultadminkeys` | `modified_by` | Beschäftigtendaten | nein | Nachweis, wer den Datensatz zuletzt geändert hat (Publisher: Name, Benutzername, Mail) | Klartext, Zugriffskontrolle |
| `vaultadminkeys` | `admin_id` | Beschäftigtendaten | nein | Zuordnung des Vault-Schlüssels zur Administratorin bzw. zum Administrator | Klartext, Zugriffskontrolle |
| `vaultadminkeys` | `full_name` | Beschäftigtendaten | nein | Anzeige in der Schlüsselverwaltung | Klartext, Zugriffskontrolle |


## 4. Verarbeitungstätigkeiten

### 4.1 Ausgabe und Aktivierung von Benutzerkonten und Chipkarten

- **Zweck:** Ausgabe/Aktivierung von Konten und Chipkarten durch Sachbearbeitende (Publisher)
- **Rechtsgrundlage (durch DSB zu bestätigen):** Art. 6 Abs. 1 lit. b / e DSGVO
- **Betroffene:** Antragstellende; Sachbearbeitende
- **Datenkategorien:** Name; Benutzername; Mail; Kundennummer/Barcode; Chipkarten-Status
- **Empfänger:** LDAP-Verzeichnis (Aktivitätsprotokoll soniaUserLog)
- **Drittlandtransfer:** keiner
- **Löschfrist:** LDAP-Bestand nach den Regeln des Verzeichnisdienstes
- **TOM:** OIDC-Login mit PKCE; Rolle PUBLISHER; LDAP-Filterwerte per Whitelist bereinigt

### 4.2 Erfassung handschriftlicher Unterschriften (Signature-Pad)

- **Zweck:** Nachweis der Kenntnisnahme/Zustimmung durch die antragstellende Person
- **Rechtsgrundlage (durch DSB zu bestätigen):** Art. 6 Abs. 1 lit. b/c DSGVO; ggf. Art. 9 Abs. 2 (biometrisch)
- **Betroffene:** Antragstellende
- **Datenkategorien:** Unterschrift (PNG/SVG im signierten JWT); Name; Mail; Ausweis-Scan; Lichtbild
- **Empfänger:** PostgreSQL (feldverschlüsselt); Dateisystem (AES-256-GCM)
- **Drittlandtransfer:** keiner
- **Löschfrist:** P90D nach letztem Kontakt im Verzeichnis (automatische Löschung)
- **TOM:** Pad-Authentisierung per eigenem Schlüsselpaar (JWT); immutable-Flag; Feld- und Dateiverschlüsselung

### 4.3 Übernahme von Online-Identifizierungsergebnissen (eID-Dienst MIRA)

- **Zweck:** Aufbewahrung des Identifizierungsergebnisses als Nachweis
- **Rechtsgrundlage (durch DSB zu bestätigen):** Art. 6 Abs. 1 lit. c DSGVO; Art. 9 Abs. 2 DSGVO (Erlaubnistatbestand zu dokumentieren, R-09)
- **Betroffene:** Antragstellende
- **Datenkategorien:** Ausweisdaten inkl. MRZ und Dokumentnummer; Geburtsdatum; Adresse; Lichtbild; Video/Audio; PDF
- **Empfänger:** PostgreSQL (feldverschlüsselt); Dateisystem (AES-256-GCM)
- **Drittlandtransfer:** keiner durch die Anwendung (Hosting/eID-Dienst organisatorisch zu dokumentieren)
- **Löschfrist:** P90D nach letztem Kontakt im Verzeichnis (automatische Löschung)
- **TOM:** Bearer-Token + HMAC-SHA256 + Frischefenster + Replay-Schutz; Feld- und Dateiverschlüsselung

### 4.4 Einsichtnahme und Audit durch Administratoren

- **Zweck:** Prüfung der Nachweise, Auskunft und Löschung auf Anfrage
- **Rechtsgrundlage (durch DSB zu bestätigen):** Art. 6 Abs. 1 lit. c/f DSGVO
- **Betroffene:** Antragstellende; Administratoren
- **Datenkategorien:** alle oben genannten Nachweise (entschlüsselt angezeigt)
- **Empfänger:** Rollen ADMIN und AUDITADMIN bei entsiegeltem Vault; Graylog (Audit-Trail)
- **Drittlandtransfer:** keiner
- **Löschfrist:** Audit-Einträge nach Log-Retention
- **TOM:** Vault-Entsiegelung per YubiKey (WebAuthn/PRF) mit TTL; Lese-Audit-Trail AUDIT_READ; Zugriff nur ADMIN und AUDITADMIN

### 4.5 Verzeichnisabgleich und Löschkonzept

- **Zweck:** Speicherbegrenzung – Löschung aller Nachweise nach Ablauf der Karenzzeit
- **Rechtsgrundlage (durch DSB zu bestätigen):** Art. 6 Abs. 1 lit. c/f DSGVO; Art. 5 Abs. 1 lit. e DSGVO
- **Betroffene:** Antragstellende
- **Datenkategorien:** Benutzername; Vorname; Nachname; Mail; Zeitpunkt des letzten Kontakts
- **Empfänger:** LDAP-Verzeichnis (lesend)
- **Drittlandtransfer:** keiner
- **Löschfrist:** Eintrag wird mit den Nachweisen der Person gelöscht
- **TOM:** DbDeletionScheduler (täglich); idempotente Löschung Datei vor Datenbankzeile; Audit USER_DELETED



## 5. Speicherbegrenzung

Der `DbDeletionScheduler` gleicht täglich mit dem LDAP-Verzeichnis ab und löscht für jede Person, die länger als die Karenzzeit (Standard P90D) nicht mehr im Verzeichnis steht, alle Nachweise in der Datenbank, die zugehörigen verschlüsselten Dateien und den Abgleichseintrag. Daneben gibt es die manuelle Sofort-Löschung je Person im Personen-Audit.

## 6. Verantwortlichkeiten (durch die Organisation auszufüllen)

| Rolle | Name / Stelle |
|---|---|
| Verantwortlicher (Art. 4 Nr. 7) | _________________ |
| Datenschutzbeauftragter | _________________ |
| Technischer Betrieb | _________________ |
| Auftragsverarbeiter (eID-Dienst, Hosting) | _________________ |
