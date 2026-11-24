# Security-Audit – l9g-accountinfo {{version}}

*Automatisch erzeugt am {{date}} (`mvn -Prelease verify`). Teil des Release-Nachweises (NIS2 Art. 21, Art. 32 DSGVO). Die ausführliche Bewertung mit Befunden und Maßnahmenplan steht in `ACCOUNT_COMPLIANCE.md`.*

## 1. Scope

Webanwendung **l9g-accountinfo** zur Unterstützung von Identitäts- und Ausweisprozessen: Account- und Chipkarten-Ausgabe, Unterschriftenerfassung über Signature-Pads, Übernahme von Online-Identifizierungsergebnissen eines externen eID-Dienstes. Java {{javaVersion}}, Spring Boot (Version siehe Abschnitt 6), PostgreSQL, OIDC-Login (Keycloak), LDAP.

## 2. Risikoregister

Restwerte nach den umgesetzten Maßnahmen; W = Wahrscheinlichkeit, S = Schwere (je 1–3), Wert = W × S. Quelle: `docs/compliance/risk-register.yaml`.

{{riskSummary}}

{{riskTable}}

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

{{accessMatrix}}

## 5. Verschlüsselte Spalten (Reflection über alle JPA-Entities)

{{encryptedFields}}

Zusätzlich werden Ausweis-Scans und Identifizierungsarchive als AES-256-GCM-verschlüsselte Dateien unter `app.storage.location` abgelegt (Schlüssel `data/secret.bin`).

## 6. Abhängigkeiten

{{sbomSummary}}

### Schwachstellenbefund (OWASP dependency-check)

{{dependencyFindings}}

Detailbericht: `dependency-check-report.html`.

## 7. Betriebshinweise

- `data/secret.bin` sichern und den Zugriff auf `data/` beschränken; ohne den Schlüssel sind verschlüsselte Spalten und Dateien unlesbar.
- TLS terminiert vor der Anwendung (Traefik); `server.forward-headers-strategy=framework` ist gesetzt.
- Produktion läuft mit `spring.jpa.hibernate.ddl-auto: update`; Mapping-Änderungen verändern die Datenbank. `SchemaGoldenTest` schreibt das Mapping fest.
