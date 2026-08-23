# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden hier dokumentiert.

Das Format folgt [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
die Versionierung [Semantic Versioning](https://semver.org/lang/de/).

Gepflegt wird die Datei über `bin/changelog.sh` (siehe `bin/changelog.sh --help`).
Der Maven-Build prüft in der `validate`-Phase, dass die Version aus `pom.xml`
hier einen Eintrag hat — ein Release ohne Changelog-Eintrag bricht ab.

## [Unreleased]

## [2.5.0] - 2026-08-23

Sicherheits- und Qualitätsrelease. Grundlage war der Aufbau einer
Unit-Test-Suite (0 → 292 Tests), die 22 Befunde aufgedeckt hat; 18 davon wurden
behoben, 4 nach Prüfung als gewolltes Verhalten bestätigt und durch Tests
festgeschrieben. Details und Begründungen: `DEFEKTE.md`.

**Kompatibilität:** Datenbankschema, WebSocket-Protokoll und die Signaturen der
über 20 im Feld befindlichen Signatur-Pads sind unverändert. Archivierte
Signaturen bleiben unbegrenzt verifizierbar.

### Security

- **Backchannel-Logout verifiziert das Logout-Token.** `/oidc-backchannel-logout`
  ist ohne Authentifizierung erreichbar und CSRF-exempt; das Token wurde bisher
  nur dekodiert, nicht verifiziert. Ein selbst gebautes Token konnte damit die
  Sitzung eines bekannten `sid` beenden. Neu prüfen `LogoutTokenVerifier` und
  `LogoutTokenConfig` Signatur (JWKS des IdP), `iss`, `exp`, `nbf`, `aud`, den
  `events`-Claim und die Abwesenheit von `nonce`. Jeder Fehlschlag ergibt `400`,
  ohne eine Sitzung anzufassen.
- **Replay-Schutz für die Storage-API.** `X-Timestamp` wurde nie geprüft, ein
  abgefangener Request war unbegrenzt wiederverwendbar. Neu: Toleranzfenster
  (`app.storage.api.timestamp-tolerance`, Default `5m`) und Einmal-Nutzung jeder
  Signatur (`app.storage.api.replay-protection`, Default `true`). Akzeptiert
  weiterhin Zeitstempel in Sekunden **und** Millisekunden.
- **LDAP-Filter-Injection geschlossen.** Kartennummer
  (`GET /api/v1/userinfo?card=…`) und Admin-Personensuche
  (`/api/v1/admin/secret/search/person`) gelangten ungefiltert in den
  Suchfilter; ein `)` konnte die Assertion verlassen. Neu bereinigt
  `LdapService.sanitizeFilterValue` zentral per Whitelist — Buchstaben und
  Ziffern jeder Schrift, Space, Bindestrich, Apostroph, Punkt.
- **Toter JWT-Verifikationspfad entfernt.** `JwtService.validateJwtSignature`
  hatte keine Aufrufer, wurde nie mit JWKS oder Client-Secret befüllt und
  ignorierte den `kid` (immer der erste RS256-Key). Statt ihn zu reparieren
  wurde er entfernt; Verifikation erfolgt über `JwtDecoder`.
- **Audit-Trail für Vault-Admin-Keys.** `createdBy` war bisher der
  Schlüsseleigentümer selbst; jetzt wird der handelnde Administrator vermerkt,
  zusätzlich mit Logzeile `VAULT_ADMINKEY_ENROLLED`.
- Logout-Token und Storage-Token werden nicht mehr im Klartext geloggt.

### Fixed

- **Heartbeat brach nach der ersten Pad-Trennung dauerhaft ab.**
  `SignaturePadWebSocketHandler` entfernte Einträge während `HashMap.forEach`
  und warf eine `ConcurrentModificationException` — der 15-Sekunden-Job war
  danach bis zum Neustart tot. Die Map ist jetzt eine `ConcurrentHashMap`, das
  Aufräumen läuft über `removeIf`. Betraf auch
  `AdminController.getSignaturePadSessions`.
- **Halb-entsperrter Vault.** Ein Schlüssel falscher Länge ließ `masterKey`
  gesetzt zurück, während `AES256` fehlschlug: `getUnlockedKey()` meldete
  „offen", jede Krypto-Operation scheiterte. `AES256` wird jetzt vor jeder
  Feldzuweisung konstruiert.
- **Infrastrukturausfälle wurden als `404` maskiert.** `AuthService.authCheck`
  fing `Throwable` und antwortete stets mit `404`; Datenbankausfall und
  unbekanntes Pad waren nicht unterscheidbar. Jetzt `500` für Störungen, `404`
  bleibt den echten Nicht-gefunden-Fällen vorbehalten — die Pad-Oberfläche
  reagiert gezielt auf `404`.
- **Verwaister Sitzungseintrag.** Wurde derselbe OIDC-`sid` mit einer neuen
  Sitzung registriert, blieb der Eintrag der alten Sitzung unerreichbar im
  Cache stehen. `SessionStoreService.put` räumt ihn jetzt auf.
- `StorageController`: fehlendes `user`-Objekt ergibt `400` statt `500`.
- `AuthService.verifyJwt`: fehlendes oder nicht-RSA-`publicJwk` ergibt `400`
  statt `NullPointerException` bzw. `ClassCastException`.
- `StorageObject.EndUserData.description()` erzeugte den Anzeigenamen
  `"null null"`, wenn beide Namensteile fehlten.
- `StorageObject.equals` verglich die Nutzdaten per Referenz statt per Inhalt;
  `toString` gibt jetzt die Größe statt eines Identity-Hashes aus.
- `JwtService.decodeJwtPayload` liefert `Map<String, Object>` statt
  `Map<String, String>` (numerische Claims warfen an der Aufrufstelle eine
  `ClassCastException`) und dekodiert explizit als UTF-8.
- Tippfehler im Alias `est-id-status` → `ext-id-status` (`SdbSecretType`).
- `SessionStoreService.shutdown()`: Javadoc widersprach der Implementierung.

### Changed

- `rebel.xml` wird nicht mehr in das Artefakt gepackt. Die JRebel-Konfiguration
  enthielt den absoluten Pfad des Entwickler-Arbeitsverzeichnisses, der damit in
  jedes Release und Docker-Image wanderte. Für den Hot-Reload bleibt die Datei
  in `target/classes`.
- Spring Boot Parent `3.5.14` → `3.5.16`.

### Added

- **Unit-Test-Suite mit 292 Tests** (`spring-boot-starter-test`), ohne
  Spring-Context, Datenbank oder Netzzugriff; Laufzeit unter drei Sekunden,
  offline lauffähig. Enthält Kompatibilitätswächter für das WebSocket-Format
  der Pads und für die unveränderte Verifikation bestehender Pad-Signaturen.
  LDAP-Pfade werden gegen ein In-Memory-Verzeichnis geprüft.
- `DEFEKTE.md` — alle 22 Befunde mit Ursache, Auswirkung, Umsetzung,
  Kompatibilitätsbewertung und zugehörigen Tests.
- `CHANGELOG.md` und `bin/changelog.sh`; der Maven-Build prüft in `validate`,
  dass die pom-Version hier dokumentiert ist.
- Neue Konfigurationsschlüssel, alle mit Default und damit
  abwärtskompatibel: `app.storage.api.timestamp-tolerance` (`5m`),
  `app.storage.api.replay-protection` (`true`).

### Bekannte, bewusst so belassene Verhaltensweisen

Durch Tests festgeschrieben, siehe `DEFEKTE.md`:

- Signatur-Pad-JWTs enthalten absichtlich kein `exp` — Signaturen laufen nie ab,
  weil dieselbe Verifikation archivierte Signaturen nachprüft.
- `SdbSecretData.setSecret`: `size` ist die Zeichenanzahl, die Prüfsumme deckt
  die Bytes. Eine Umstellung würde die Prüfsummen aller Bestandsdatensätze
  invalidieren.
- Eine leere Suchanfrage listet alle Personen (beide Suchendpunkte verlangen
  eine authentifizierte Sitzung).
- Eine Ein-Wort-Personensuche vergleicht den Nachnamen; die Argumentreihenfolge
  bestimmt das durchsuchte Feld.

## [2.4.1] - 2026-06-28

### Added

- DSGVO-/NIS2-Löschkonzept: `SdbLastSeen`, `DbDeletionScheduler`,
  `DbService.deleteUserData` mit Gnadenfrist
  (`ldap.configuration.user.delete-grace-period`).
- Manuelle Art.-17-Löschung über `POST /api/v1/admin/secret/person/{uid}/delete`.
- Vault-Admin-Schlüssel mit WebAuthn-Entsiegelung (`SdbVaultAdminKey`).
- Personen-Audit und Export (`/api/v1/admin/secret/audit`, `/export/person`).
- Compliance-Dokumentation: `ACCOUNT_COMPLIANCE.md`,
  `ACCOUNT_PROZESS_BESCHREIBUNG.md`, `ACCOUNT_DSFA.md`,
  `ACCOUNT_SOURCECODE_STATISTIK.md`.

## [2.2.3] - 2026-06-19

### Added

- Storage-API `POST /api/v1/storage/objects` für externe
  Identifikationsergebnisse, gesichert mit Bearer-Token und
  HMAC-SHA256-Signatur.
- `EXT_IDENTIFICATION_STATUS` und `EXT_IDENTIFICATION_ARCHIVE` als
  Secret-Typen.

---

Frühere Versionen sind nicht rückwirkend dokumentiert; für Details siehe die
git-Historie.

[Unreleased]: https://github.com/thorsten-l/l9g-accountinfo/compare/v2.5.0...HEAD
[2.5.0]: https://github.com/thorsten-l/l9g-accountinfo/compare/v2.4.1...v2.5.0
[2.4.1]: https://github.com/thorsten-l/l9g-accountinfo/compare/v2.2.3...v2.4.1
[2.2.3]: https://github.com/thorsten-l/l9g-accountinfo/releases/tag/v2.2.3
