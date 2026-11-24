# Security-Audit – l9g-accountinfo 3.0.0

*Automatisch erzeugt am 2026-11-24 (`mvn -Prelease verify`). Teil des Release-Nachweises (NIS2 Art. 21, Art. 32 DSGVO). Die ausführliche Bewertung mit Befunden und Maßnahmenplan steht in `ACCOUNT_COMPLIANCE.md`.*

## 1. Scope

Webanwendung **l9g-accountinfo** zur Unterstützung von Identitäts- und Ausweisprozessen: Account- und Chipkarten-Ausgabe, Unterschriftenerfassung über Signature-Pads, Übernahme von Online-Identifizierungsergebnissen eines externen eID-Dienstes. Java 25, Spring Boot (Version siehe Abschnitt 6), PostgreSQL, OIDC-Login (Keycloak), LDAP.

## 2. Risikoregister

Restwerte nach den umgesetzten Maßnahmen; W = Wahrscheinlichkeit, S = Schwere (je 1–3), Wert = W × S. Quelle: `docs/compliance/risk-register.yaml`.

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


## 3. Implementierte Sicherheitsmaßnahmen

| Bereich | Maßnahme | Nachweis im Code |
|---|---|---|
| Authentifizierung | OIDC Authorization Code mit PKCE; Rollen aus dem `resource_access`-Claim des Access Tokens | `ClientSecurityConfig`, `AppAuthoritiesConverter` |
| Autorisierung | URL-Regeln mit Vault-Bedingung plus `@PreAuthorize`; Personen-Audit nur für ADMIN und AUDITADMIN bei entsiegeltem Vault (maßgeblich ist die Zugriffsmatrix in Abschnitt 4) | `ClientSecurityConfig`, Abschnitt 4 |
| Sitzungsbeendigung | OIDC-Backchannel-Logout; Logout-Token gegen das JWKS des IdP verifiziert (Signatur, `iss`, `exp`, `nbf`, `aud`, `events`) | `LogoutTokenVerifier`, `BackchannelLogoutController` |
| Schlüsselverwaltung | Vault mit In-Memory-Masterkey und TTL; Entsiegelung per YubiKey (WebAuthn/PRF, AES-GCM) | `VaultService`, `VaultApiController` |
| Verschlüsselung ruhender Daten | Feldverschlüsselung (Abschnitt 5) und AES-256-GCM-verschlüsselte Dateien; Secrets in `data/config.yaml` als `{AES256}` | `EncryptedAttributeConverter`, `FileStorageService` |
| Storage-API | Bearer-Token (konstante Zeit), HMAC-SHA256 über Zeitstempel und Body, Frischefenster, Einmal-Nutzung der Signatur | `StorageController` |
| Signature-Pads | Authentisierung per JWT mit pad-eigenem Schlüsselpaar | `AuthService` |
| LDAP | Alle Filterwerte per Whitelist bereinigt | `LdapService` (`LdapServiceInjectionTest`) |
| HTTP-Header | Content-Security-Policy, `X-Content-Type-Options: nosniff`, Cache-Control `no-store` | `ClientSecurityConfig` (`ClientSecurityConfigGoldenTest`) |
| CSRF | aktiv; Ausnahmen nur für Backchannel-Logout, Pad-API, Storage-API und Logout | `ClientSecurityConfig` |
| Rechenschaft | Lese-Audit-Trail `AUDIT_READ`, `USER_EXPORT`, `USER_ERASE_MANUAL`, `USER_DELETED` | `ApiAdminController`, `DbService` |
| Regressionsschutz | Golden-Tests für Zugriffsmatrix, gespeichertes JSON und DB-Schema; laufen vor diesem Bericht | `src/test/resources/golden/` |
| Supply Chain | SBOM (CycloneDX) und OWASP dependency-check je Release | Abschnitt 6 |

## 4. Zugriffsmatrix (Berechtigungskonzept)

Abgeleitet aus `src/test/resources/golden/security-matrix.txt`. Der Release-Build führt `ClientSecurityConfigGoldenTest` vor diesem Bericht aus; die Tabelle entspricht damit nachweislich der ausgelieferten `SecurityFilterChain`. Feinere Prüfungen per `@PreAuthorize` auf Methodenebene kommen hinzu.

Legende: **✓** erlaubt · **Login** Weiterleitung zur Anmeldung · **–** verweigert · **✓ Vault offen** nur bei entsiegeltem Vault · **✓ Vault offen oder keine Keys** auch, solange noch kein Admin-Schlüssel eingerichtet ist.

| Pfad (GET) | anonym | ohne Rolle | PUBLISHER | TABADMIN | VAULTADMIN | AUDITADMIN | ADMIN |
|---|---|---|---|---|---|---|---|
| `/` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/manifest.json` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/manifest.webmanifest` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/system/test/403` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/system/test/error403` | Login | – | – | – | – | – | – |
| `/system/test/500` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/error` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/error/x` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/buildinfo` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/webjars/jquery/jquery.min.js` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/icons/x.png` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/css/app.css` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/js/app.js` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/images/x.png` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/flags/de.svg` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/actuator/health` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/android-chrome-192x192.png` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/apple-touch-icon.png` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/favicon.ico` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/favicon-16x16.png` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/androidX/sub` | Login | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/logout` | – | – | – | – | – | – | – |
| `/logoutnow` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/ws/signature-pad` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/admin/validate-new-pad` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/signature-pad` | Login | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/admin` | Login | – | – | ✓ | ✓ | ✓ | ✓ |
| `/admin/` | Login | – | – | ✓ | ✓ | ✓ | ✓ |
| `/ADMIN` | Login | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/admin/vault/managekeys` | Login | – | – | – | ✓ Vault offen | – | ✓ Vault offen |
| `/admin/vault/managekeys/` | Login | – | – | – | ✓ | ✓ | ✓ |
| `/admin/vault/enrollment` | Login | – | – | – | ✓ Vault offen oder keine Keys | – | ✓ Vault offen oder keine Keys |
| `/admin/vault/unseal` | Login | – | – | – | ✓ | ✓ | ✓ |
| `/admin/useraudit` | Login | – | – | – | – | ✓ | ✓ |
| `/admin/register-new-pad` | Login | – | – | ✓ | – | – | ✓ |
| `/admin/manage-pads` | Login | – | – | ✓ | – | – | ✓ |
| `/admin/show-pad-info` | Login | – | – | ✓ | – | – | ✓ |
| `/admin/show-pad` | Login | – | – | ✓ | – | – | ✓ |
| `/admin/hide-pad` | Login | – | – | ✓ | – | – | ✓ |
| `/admin/delete-pad` | Login | – | – | ✓ | – | – | ✓ |
| `/admin/wait-for-response` | Login | – | – | ✓ | – | – | ✓ |
| `/admin/other` | Login | – | – | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/admin/vault/challenge-data` | Login | – | – | – | ✓ | ✓ | ✓ |
| `/api/v1/admin/vault/unlocktimeleft` | Login | – | – | – | ✓ | ✓ | ✓ |
| `/api/v1/admin/vault/adminkey` | Login | – | – | – | ✓ Vault offen oder keine Keys | – | ✓ Vault offen oder keine Keys |
| `/api/v1/admin/secret` | Login | – | – | – | – | – | ✓ Vault offen, Keys vorhanden |
| `/api/v1/admin/secret/` | Login | – | – | – | – | – | ✓ Vault offen, Keys vorhanden |
| `/api/v1/admin/secret/audit` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |
| `/api/v1/admin/secret/audit/x` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |
| `/api/v1/admin/secret/search/person` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |
| `/api/v1/admin/secret/userinfo.json` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |
| `/api/v1/admin/secret/signature.png` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |
| `/api/v1/admin/secret/signature.svg` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |
| `/api/v1/admin/secret/id.jpeg` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |
| `/api/v1/admin/secret/archive.json` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |
| `/api/v1/admin/secret/archive/x` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |
| `/api/v1/admin/secret/export/x` | Login | – | – | – | – | ✓ Vault offen | ✓ Vault offen |
| `/api/v1/admin/secret/other` | Login | – | – | – | – | – | ✓ Vault offen, Keys vorhanden |
| `/v3/api-docs` | Login | – | – | – | – | – | ✓ |
| `/v3/api-docs/swagger-config` | Login | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/admin` | Login | – | – | – | – | – | ✓ |
| `/api/v1/admin/x` | Login | – | – | – | – | – | ✓ |
| `/app` | Login | – | ✓ | – | – | – | – |
| `/app/x` | Login | – | ✓ | – | – | – | – |
| `/api/v1/search/x` | Login | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/summary` | Login | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/userinfo` | Login | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/signature-pad/connect-qrcode` | Login | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/oauth2/authorization/app` | ↪ | ↪ | ↪ | ↪ | ↪ | ↪ | ↪ |
| `/login/oauth2/code/app` | ↪ | ↪ | ↪ | ↪ | ↪ | ↪ | ↪ |
| `/some/other` | Login | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/api/v1/storage/objects` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/oidc-backchannel-logout` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |


## 5. Verschlüsselte Spalten (Reflection über alle JPA-Entities)

- **SdbSecretData** (`secretdata`): `secret`


Zusätzlich werden Ausweis-Scans und Identifizierungsarchive als AES-256-GCM-verschlüsselte Dateien unter `app.storage.location` abgelegt (Schlüssel `data/secret.bin`).

## 6. Abhängigkeiten

SBOM (CycloneDX 1.6) mit **148 Komponenten**: `sbom.json`

| Komponente | Version |
|---|---|
| Spring Boot | 4.1.3 |
| Spring Framework | 7.0.11 |
| Spring Security | 7.1.3 |
| Hibernate ORM | 7.4.15.Final |
| Tomcat | 11.0.27 |
| Jackson 3 | 3.1.8 |
| Nimbus JOSE + JWT | 10.9.1 |
| L9G Crypto | 2.0.1 |
| PostgreSQL JDBC | 42.7.14 |
| UnboundID LDAP SDK | 7.0.6 |
| springdoc-openapi | 3.2.0 |
| Swagger UI | 5.36.0 |


### Schwachstellenbefund (OWASP dependency-check)

Keine bekannten Schwachstellen gefunden.


Detailbericht: `dependency-check-report.html`.

## 7. Betriebshinweise

- `data/secret.bin` sichern und den Zugriff auf `data/` beschränken; ohne den Schlüssel sind verschlüsselte Spalten und Dateien unlesbar.
- TLS terminiert vor der Anwendung (Traefik); `server.forward-headers-strategy=framework` ist gesetzt.
- Produktion läuft mit `spring.jpa.hibernate.ddl-auto: update`; Mapping-Änderungen verändern die Datenbank. `SchemaGoldenTest` schreibt das Mapping fest.
