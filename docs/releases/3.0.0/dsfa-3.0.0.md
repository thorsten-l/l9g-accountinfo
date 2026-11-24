# Datenschutz-Folgenabschätzung (DSFA) – l9g-accountinfo 3.0.0

*Automatisch erzeugt am 2026-11-24 (`mvn -Prelease verify`) als Release-Nachweis nach Art. 35 DSGVO. Die vollständige DSFA mit Schwellwertanalyse, Verhältnismäßigkeitsprüfung und Detailbewertung steht in `ACCOUNT_DSFA.md`. Technisch fundierte Risikoanalyse, keine Rechtsberatung; die abschließende Würdigung obliegt dem DSB.*

## 1. Notwendigkeit

Die Anwendung verarbeitet besondere Kategorien personenbezogener Daten (Art. 9 DSGVO: Lichtbild, Unterschrift, Ausweisdaten, Video und Audio der Online-Identifizierung). Eine DSFA ist nach Art. 35 Abs. 3 lit. b DSGVO erforderlich.

## 2. Datenbestand dieses Release

### 2.1 Datentypen

| Datentyp (`SdbSecretType`) | Inhalt | Ablage | Art. 9 |
|---|---|---|---|
| `SIGNATURE_PAD_JSON` | Konfiguration eines Signature-Pads (öffentlicher Schlüssel, Client-Umgebung) | secretdata.secret (feldverschlüsselt) | nein |
| `ID_SIGNATURE_JWT` | Signiertes JWT mit Unterschrift (PNG/SVG), Name, Mail | secretdata.secret (feldverschlüsselt) | **ja** |
| `ID_FRONT_IMAGE` | Ausweis-Scan Vorderseite | Datei (AES-256-GCM) | **ja** |
| `ID_BACK_IMAGE` | Ausweis-Scan Rückseite | Datei (AES-256-GCM) | **ja** |
| `EXT_IDENTIFICATION_STATUS` | Identifizierungsstatus des eID-Dienstes (Ausweis-, Adress- und Kontaktdaten) | secretdata.secret (feldverschlüsselt) | **ja** |
| `EXT_IDENTIFICATION_ARCHIVE` | ZIP mit Pass-Scans, Portrait, Audio, Video und PDF der Online-Identifizierung | Datei (AES-256-GCM) | **ja** |


### 2.2 Personenbezogene Spalten

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


## 3. Risikobewertung

Bewertungsmaßstab: Wahrscheinlichkeit (W) und Schwere (S) je 1 = gering, 2 = mittel, 3 = hoch; Wert = W × S; Stufe 1–2 gering, 3–4 mittel, 6–9 hoch. Bewertet werden die Restrisiken nach den umgesetzten Maßnahmen. Quelle: `docs/compliance/risk-register.yaml`.

| Stufe | Anzahl | Risiken |
|---|---|---|
| hoch | 2 | R-01, R-09 |
| mittel | 6 | R-02, R-03, R-04, R-05, R-12, R-13 |
| gering | 6 | R-06, R-07, R-08, R-10, R-11, R-14 |


| ID | Risiko | Schutzziel | W | S | Wert | Stufe | Umgesetzt | Geplant |
|---|---|---|---|---|---|---|---|---|
| R-01 | Personenbezogene Daten in INFO/WARN-Logs (Graylog und lokales Logfile, 400 Tage) | DM; V; T | 3 | 2 | 6 | **hoch** | Zugriff auf 4 benannte RZ-Personen beschränkt und auditiert | M-01 PII aus Logs entfernen, Retention-/Zugriffs-Policy (P0) |
| R-02 | Kompromittierung des ./data-Volumes (secret.bin und config.yaml) | V | 1 | 3 | 3 | mittel | Host nur per SSH mit FIDO2-Schlüssel, 4 benannte Personen | M-02 secret.bin und config.yaml getrennt absichern (P1) |
| R-03 | Unbefugter Admin-Zugriff auf entschlüsselte Art.-9-Daten (Insider) | V; B | 1 | 3 | 3 | mittel | Nur ADMIN und AUDITADMIN, Vault-Entsiegelung per YubiKey mit TTL, Lese-Audit-Trail | M-03 Zugriffsgovernance, ggf. Mehr-Personen-Entsiegelung (P1) |
| R-04 | Datenleck von Art.-9-Daten in transit (eID-Dienst, Storage-API) | V; I | 1 | 3 | 3 | mittel | TLS über Traefik, HMAC-SHA256 mit Frischefenster und Replay-Schutz | M-04 TLS-Härtung der eID-Strecke verifizieren (P2) |
| R-05 | LDAP-MITM mangels Zertifikatsprüfung (TrustAllTrustManager) | V; I | 1 | 3 | 3 | mittel | LDAPS, Bind-Daten {AES256}-verschlüsselt | M-05 Truststore mit Hostname-Verifikation (P1) |
| R-06 | Offene Test-Endpunkte /system/test/** (unauthentisiert) | V; Vf | 2 | 1 | 2 | gering | keine Daten hinter den Endpunkten | M-06 in Produktion deaktivieren (P0) |
| R-07 | Fehlende oder verzögerte Löschung | DM; B | 1 | 2 | 2 | gering | Automatische Löschung nach P90D inkl. Dateien, idempotent, Audit USER_DELETED | – |
| R-08 | Unmöglichkeit der Rechtsausübung (Auskunft, Löschung, Berichtigung) | B | 1 | 2 | 2 | gering | Auskunfts-Export (ZIP), manuelle Sofort-Erasure je Person | – |
| R-09 | Fehlender Art.-9-Erlaubnistatbestand oder fehlende Transparenz | T; R | 2 | 3 | 6 | **hoch** | keine technische Maßnahme möglich | M-09 Erlaubnistatbestand dokumentieren, Betroffeneninformation (P1) |
| R-10 | Schema-Drift oder Datenverlust durch ddl-auto=update | I; Vf | 1 | 2 | 2 | gering | SchemaGoldenTest schreibt Mapping und Constraint-Namen fest (ab 3.0.0), Migrationsskripte in sql/ | M-10 ddl-auto=validate mit verwalteten Migrationen (P1) |
| R-11 | Verfügbarkeits- oder Datenverlust | Vf; B | 1 | 2 | 2 | gering | ZFS mit Replikation auf Air-Gap-Server im Schatten-RZ, Restore getestet | – |
| R-12 | Auftragsverarbeitung ohne AV-Vertrag (eID-Dienst, Hosting) | R | 2 | 2 | 4 | mittel | keine technische Maßnahme möglich | M-12 AV-Verträge nach Art. 28 (P1) |
| R-13 | Keine Schlüsselrotation, kein externes KMS/HSM (secret.bin statisch) | V | 1 | 3 | 3 | mittel | secret.bin mit Rechten 0400 | M-13 externes Key-Management, Rotation (P2) |
| R-14 | Fehlende Header-Härtung (HSTS, frame-ancestors) – Clickjacking/Downgrade | I; V | 1 | 2 | 2 | gering | CSP und X-Content-Type-Options nosniff durch die Anwendung | M-14 HSTS und frame-ancestors an Traefik (P1) |


## 4. Ergebnis

Hohe Restrisiken sind in der Übersicht in Abschnitt 3 ausgewiesen; sie sind mit den geplanten Maßnahmen zu senken, bevor die Verarbeitung als datenschutzkonform freigegeben wird. Ob eine Konsultation der Aufsichtsbehörde nach Art. 36 DSGVO erforderlich ist, entscheidet der DSB. Die DSFA ist bei wesentlichen Änderungen (neue Datenarten, neue Empfänger, geänderte Fristen) und spätestens alle 24 Monate zu überprüfen.

## 5. Freigabe

| Rolle | Name / Stelle | Datum | Unterschrift |
|---|---|---|---|
| Verantwortlicher | _________________ | ________ | ____________ |
| Datenschutzbeauftragter | _________________ | ________ | ____________ |
| Technischer Betrieb | _________________ | ________ | ____________ |
