# Compliance-Bericht (DSGVO & NIS2) — l9g-accountinfo

> **Zweck:** Bewertung der Anwendung **l9g-accountinfo** hinsichtlich DSGVO und
> NIS2 zur Vorlage beim **Datenschutzbeauftragten (DSB)** und **Compliance-Officer**,
> mit besonderem Fokus auf Befunde, die **im Produktionsbetrieb** (nicht nur in
> der Entwicklung) datenschutz- bzw. sicherheitsrelevant sind.
>
> **Stand:** 2026-06-23 · **Branch:** `one-key-to-bind-them`
> **Methodik:** automatisierte statische Quellcode-Analyse (Dateninventar,
> Logging, Sicherheits­konfiguration, Aufbewahrung/Löschung).
> **Haftungsausschluss:** Dies ist eine **technische** Analyse, **keine
> Rechtsberatung**. Die rechtliche Würdigung obliegt dem DSB. Zeilenangaben
> beziehen sich auf den o. g. Stand und können nach Änderungen abweichen.

Ergänzendes Dokument: **ACCOUNT_PROZESS_BESCHREIBUNG.md** (Verarbeitungstätigkeiten,
Datenflüsse, Dateninventar).

---

## 0. Management Summary

Die Anwendung verfügt über eine **architektonisch solide Sicherheitsbasis**
(Feld- und Dateiverschlüsselung AES-256, Vault mit Masterkey-TTL und
WebAuthn-Entsiegelung, OAuth2/OIDC mit PKCE, HMAC-gesicherte Upload-API). Es
werden jedoch **besondere Kategorien personenbezogener Daten (Art. 9 DSGVO)**
verarbeitet (Lichtbilder, Ausweisdaten inkl. MRZ, Unterschrift, Video/Audio),
was erhöhte Anforderungen auslöst.

**Wesentliche, produktionsrelevante Befunde** (Schwerpunkt jetzt **DSGVO-Struktur**,
da Infrastruktur/Config überwiegend gehärtet sind):

1. **Personenbezogene Daten in Logs** (INFO/WARN) → fließen in **Graylog** und ins
   lokale Logfile (Retention 400 Tage) → Redaktion/Governance nötig (2.3).
2. **Restpunkte Technik:** LDAP nutzt **LDAPS**, aber ohne Zertifikatsprüfung
   (`TrustAllTrustManager`, 3.1); `ddl-auto: update` (3.6); HSTS/Test-Endpunkte an
   Traefik (3.2/3.4).

> **Erledigt:** **Auskunfts-Export** (ZIP der entschlüsselten SdbSecretData-
> Metadaten ohne Publisher/Binärdaten), **manuelle Sofort-Erasure** pro Person
> (`/admin/useraudit`) sowie der **Lese-Audit-Trail** (`AUDIT_READ` auf INFO →
> Graylog) für alle lesenden Admin-Zugriffe auf personenbezogene Daten.

> **Positiv (bestätigt mit Prod-Config):** **automatische Löschung nach Karenzzeit
> (P90D) implementiert** inkl. Datei-Löschung (2.1); `immutable` schützt nur gegen
> Änderung, nicht gegen Löschung (2.2); **alle Secrets `{AES256}`-verschlüsselt**;
> `l9g: INFO`/`root: ERROR`; Swagger aus; **Vault-TTL 180 s**; **LDAPS** (Port 636);
> DB intern mit Passwort-Auth. **Infrastruktur:** TLS-Edge (ACME) + HTTP→HTTPS-
> Redirect + Methoden-Whitelist + Extension-Blocklist an Traefik; Host nur 80/443;
> `net108` outgoing-only; zentrales Logging via GELF/Graylog; **Log-Rotation**
> (`max-history: 400`).

Einstufung: **Infrastruktur/Transport/Secret-Management überwiegend NIS2-/Art.-32-
konform; Speicherbegrenzung/Löschung (Art. 5/17) umgesetzt.** Verbleibend v. a.
**Auskunft/Export, Lese-Audit, Log-PII** sowie kleinere technische Restpunkte
(LDAP-Zertifikatsprüfung, `ddl-auto`, Traefik-Header). Diese sind
**deployment-unabhängig**.

---

## 1. Lesart „Produktion vs. Entwicklung"

Die Anwendung kennt mehrere Spring-Profile. Maßgeblich:

| Quelle | Rolle | `l9g`-Loglevel | Secrets | Swagger | `app.development` |
|---|---|---|---|---|---|
| `src/main/resources/application.yaml` | eingebauter Default | INFO | — | aus | `false` |
| Repo `data/config.yaml` (Beispiel) | **Dev-/Beispieldatei** | INFO/TRACE | tlw. **Klartext** | tlw. an | `true` |
| **Gemountete Prod-`config.yaml`** (vom Betrieb) | **Produktion** | **`l9g: INFO`, root `ERROR`** | **alle `{AES256}`** | **aus** | `true` *(wirkungslos, s. u.)* |

> **Konfigurations-Hygiene (geklärt):** Die **produktive** `config.yaml` (vom
> Betrieb bereitgestellt, gemountet aus `./data`) ist gehärtet — **alle Secrets
> `{AES256}`-verschlüsselt**, `l9g: INFO`/`root: ERROR`, Swagger/API-Docs aus,
> Vault-TTL 180 s, LDAPS. Die im **Repo** liegende `data/config.yaml` mit
> Klartext-Secret ist nur eine **Dev-/Beispieldatei** und nicht produktiv im
> Einsatz. **Rest-Hinweis:** `app.development: true` ist auch produktiv gesetzt,
> wird aber **nirgends im Code/Template gelesen** (verifiziert) → **funktional
> wirkungslos**; aus Hygienegründen auf `false` setzen.

**Deployment-Architektur (laut Produktions-`docker-compose`):** **Traefik,
l9g-accountinfo, PostgreSQL** und ein **error-responder** (nginx) laufen in einem
Compose. Maßgeblich:
- **App:** läuft intern **HTTP, Port 8080** (`expose: 8080`, **kein** Host-Mapping),
  Actuator intern auf 9000; eigener TLS-Endpunkt produktiv abgeschaltet. Config,
  Schlüssel und Logfile kommen aus dem gemounteten **`./data` → `/data`**
  (`secret.bin`, `server.p12`, `secrets/`, `accountinfo.log`, `config.yaml`).
- **Traefik:** einziger TLS-Endpunkt (Entrypoints `:80`/`:443`), **HTTP→HTTPS-
  Redirect (permanent)**, Zertifikate per **ACME (certbot/DNS-01)**. **Edge-Härtung
  positiv:** Methoden-Whitelist (**nur GET/HEAD/POST**, sonst 405 via
  error-responder) und **Blocklist gefährlicher Dateiendungen** (`.php/.env/.sql/
  .zip/…`). **Aber:** keine **HSTS**-/`nosniff`-Header-Middleware; **`/system/test/**`
  wird nicht gesondert blockiert** (per GET erreichbar — siehe 3.4).
- **PostgreSQL (`signaturedb`, postgres:18):** **Host-Port `5432:5432` gemappt**
  (nicht rein intern!) und **ohne `POSTGRES_PASSWORD`-Env** im Compose
  (Authentisierung zu verifizieren). Daten-Volume `./signaturedb`.
- **Netz / Erreichbarkeit:** Docker-Host im **10/8**, per IP-Filter nur **80/443**
  von außen (= Traefik). Der App-Container ist zusätzlich am Netz **`net108`
  (`141.41.8.112`)** angebunden — laut Betrieb **per IP-Filter ausschließlich
  ausgehend** (eingehend vollständig blockiert), dient nur der Outbound-Anbindung
  (LDAP/IdP/eID). Traefik zusätzlich auf `net704` (`10.230.153.43`).
- **Logging:** Docker-Logs werden vom Host **automatisch per GELF an einen
  Graylog-Cluster** ausgeleitet (zentral, dauerhaft).
- **Docker-Socket:** Traefik mountet `/var/run/docker.sock` (read-only); mehrere
  Traefik-Instanzen teilen sich den Socket (Label-Constraint `traefik.profile`).

**Bewertung:** Gute Netzsegmentierung (Host nur 80/443; `net108` outgoing-only),
durchdachte **Edge-Härtung** an Traefik (Redirect, Methoden-Whitelist,
Extension-Blocklist) und **zentrales Logging (GELF/Graylog)**. Die interne Strecke
**Traefik → App** (HTTP) ist Container-zu-Container und unkritisch. **Verbleibende,
deployment-seitige Punkte:** (a) **PostgreSQL-Port `5432` auf Host veröffentlicht**
+ fehlendes Passwort-Env (3.6); (b) **HSTS/`nosniff`** und **Test-Endpunkte** an
Traefik (3.2/3.4); (c) **PII-Inhalte** der Logs in Graylog (Governance — 2.3); (d)
Hardening der gemounteten **`./data/config.yaml`** (Secrets, `development:false`).

Im Folgenden wird je Befund gekennzeichnet:
**[PROD]** = wirkt im Produktivbetrieb · **[DEV]** = nur in Entwicklungsprofilen ·
**[STRUKTUR]** = unabhängig vom Profil (Code-/Architekturthema).

---

## 2. Produktionsrelevante DSGVO-Befunde

### 2.1 [UMGESETZT] Automatische Löschung nach Karenzzeit — Speicherbegrenzung (Art. 5 Abs. 1 lit. e)
- **Stand (Code):** Das Löschkonzept ist **implementiert**. Der
  `DbDeletionScheduler` (Cron `scheduler.db-deletion.cron`) führt täglich aus:
  (1) `updateLastSeenFromLdap()` — `SdbLastSeen`-Zeitstempel der aktiven Nutzer auf
  „jetzt"; (2) `deleteExpiredUsers()` — für jeden Nutzer mit
  `now − lastSeen > delete-grace-period` (**P90D**) ruft
  `DbService.deleteUserData(...)` auf und **löscht alle `SdbSecretData`-Datensätze,
  die zugehörigen verschlüsselten Dateien (`FileStorageService.delete`) und den
  `SdbLastSeen`-Eintrag**.
- **Robustheit:** Datei-Löschung vor DB-Löschung; der `SdbLastSeen`-Eintrag wird
  nur entfernt, wenn **alle** Datensätze erfolgreich gelöscht wurden — sonst Retry
  im nächsten Lauf (idempotent). Audit-Log `USER_DELETED` je Nutzer.
- **DSGVO:** Art. 5 Abs. 1 lit. e erfüllt (definierte Aufbewahrung = Karenzzeit ab
  letztem LDAP-Kontakt); unterstützt Art. 17.
- **Verbleibend (organisatorisch):** Die Karenz **P90D** ist die technische
  Standardfrist; ob je Datenart abweichende gesetzliche Fristen gelten, ist
  fachlich/rechtlich zu bestätigen (DSB).

### 2.2 [REDUZIERT] Unveränderbarkeit — Löschung jetzt möglich, nur Änderung gesperrt
- **Aktueller Stand (Code):** `SdbUuidObject.preRemove()` wirft **nicht mehr** bei
  `immutable=true` (loggt nur); nur `preUpdate()` blockiert weiterhin Änderungen.
  → **`immutable`-Datensätze können nun gelöscht werden, sind aber gegen
  Veränderung geschützt.** Das ist das **datenschutzkonforme Muster**:
  Integritätsschutz (Art. 5 Abs. 1 lit. f — Identifizierungs-/Unterschriften­nachweise
  sind manipulationssicher) **bei gleichzeitiger Erfüllbarkeit von Art. 17**.
- **DSGVO Art. 17 (Löschung):** auf Datenschicht **nicht mehr blockiert** →
  strukturelles Hindernis entfällt.
- **DSGVO Art. 16 (Berichtigung):** für Identifizierungs­artefakte weiterhin durch
  `preUpdate` gesperrt — bei evidenzartigen Momentaufnahmen (Ausweis-Scan, MRZ,
  Unterschrift) i. d. R. **sachgerecht** (statt Korrektur: löschen + neu erfassen).
- **Operative Löschung:** Die nun löschbaren Datensätze werden durch
  `DbService.deleteUserData(...)` pro Person tatsächlich entfernt — inkl. der
  verschlüsselten Dateien (siehe 2.1). Eine **manuelle, sofortige** Erasure pro
  Person auf Anforderung (Art. 17 außerhalb der Karenzzeit) ist als Admin-Funktion
  noch nicht exponiert — geringfügige Erweiterung (dieselbe Methode aufrufen).

### 2.3 [PROD] Personenbezogene Daten in Logs → Graylog (Art. 5 Abs. 1 lit. c/f, Art. 32)
- **Befund:** Auf **INFO/WARN**-Ebene (im Produktivbetrieb aktiv) werden u. a.
  protokolliert:

  | Log-Ereignis | Personenbezug | Quelle |
  |---|---|---|
  | `STORAGE_UPLOAD` | Benutzername | `StorageController` |
  | `SIGNATURE_OK` / `SIGNATURE_CANCEL` | Benutzername, Subject, userId | `ApiSignaturePadController` |
  | `PAD_START` / `PAD_AUTH` | Benutzername | `SignaturePadController` / `ApiAuthPadController` |
  | `PHOTO_UPLOAD` | Benutzername, userId | `ApiScanController` |
  | `USER_SEARCH` / `USER_FOUND` | Benutzername, **Kundennummer, Barcode, UID** | `ApiUserInfoController` |
  | `LOGOUT` | Benutzername | `ClientSecurityConfig` |
  | `DELETE CANDIDATE` (WARN) | **Vorname, Nachname, E-Mail**, Benutzername | `DbDeletionScheduler` |

- **Wirkung:** Diese Daten fließen über **stdout → Docker-GELF → Graylog-Cluster**
  (zentralisiert; positiv für Monitoring/Integrität) **und** zusätzlich in das
  lokale Volume-Logfile `./data/accountinfo.log`. Personenbezug (teils sensibel)
  wird damit **in den zentralen Logbestand übernommen**.
- **DSGVO:** Datenminimierung (Art. 5 Abs. 1 lit. c); die personenbezogenen Logs
  unterliegen in **Graylog** denselben Pflichten (Zweckbindung, **Aufbewahrungs-/
  Löschfristen**, Zugriffsbeschränkung, ggf. eigener Eintrag im
  Verarbeitungsverzeichnis).
- **Maßnahme:** Klarnamen/E-Mail/Kundennummer aus INFO/WARN-Logs entfernen
  (pseudonyme Korrelations-IDs); in **Graylog** Retention-/Zugriffs-Policy für
  diese Streams definieren; redundantes lokales Logfile in `./data` prüfen
  (Doppelablage von PII vermeiden).

### 2.4 [UMGESETZT] Protokollierung lesender Admin-Zugriffe (Art. 5 Abs. 2, Art. 32)
- **Stand (Code):** Jeder lesende Admin-Zugriff auf personenbezogene Daten erzeugt
  in `ApiAdminController` einen **INFO**-Audit-Eintrag
  `AUDIT_READ: admin={…}, action={…}, target={…}` (Helper `auditRead(...)`).
  Instrumentiert: `id.jpeg` (ID_IMAGE), `signature.png/svg`, `userinfo.json`,
  `archive.json` (ARCHIVE_LIST), `archive/file` (ARCHIVE_FILE), `pad.json`,
  `search/person` (PERSON_SEARCH), `audit/person` (AUDIT_PERSON). Export
  (`USER_EXPORT`) und Löschung (`USER_ERASE_MANUAL`) werden ebenfalls geloggt.
- **Wirkung:** „Wer hat wann wessen Daten gesehen" ist über **GELF → Graylog**
  zentral und manipulationssicher auswertbar.
- **Verbleibend (organisatorisch):** In Graylog Aufbewahrung/Zugriff der
  `AUDIT_READ`-Streams regeln (der Trail enthält Admin- und Betroffenen-IDs).

### 2.5 [STRUKTUR] Kein Auskunfts-/Portabilitäts-Export (Art. 15/20)
- **Befund:** Admins können Daten **einsehen**, es existiert aber **keine**
  strukturierte Export-/Download-Funktion (z. B. JSON/ZIP) zur Erfüllung von
  Auskunfts- und Übertragbarkeits­ersuchen.
- **DSGVO:** Art. 15, Art. 20.
- **Maßnahme:** Export-Funktion pro betroffener Person bereitstellen.

### 2.6 [STRUKTUR] DSFA & Art.-9-Zulässigkeit
- **Befund:** Verarbeitung biometrischer/Ausweisdaten in erheblichem Umfang.
- **DSGVO:** Art. 9 (Erlaubnistatbestand erforderlich), Art. 35
  (Datenschutz-Folgenabschätzung wahrscheinlich verpflichtend).
- **Maßnahme:** DSFA durchführen/dokumentieren; Erlaubnistatbestand belegen.

---

## 3. Produktionsrelevante Sicherheits-/NIS2-Befunde (Art. 32 DSGVO)

### 3.1 [PROD — REDUZIERT] LDAP: LDAPS aktiv, aber keine Zertifikatsprüfung
- **Befund (mit Prod-Config):** LDAP läuft produktiv über **LDAPS**
  (`ldap.host.name: dps1.sonia.de`, **Port 636, `ssl: true`**), Bind-DN/-Passwort
  `{AES256}`-verschlüsselt → **Transport ist verschlüsselt**. **Aber:** der Code
  verwendet bei `ssl: true` weiterhin einen **`TrustAllTrustManager`**
  (`LdapService.createSSLSocketFactory()`) → **keine Zertifikats-/Hostname-
  Prüfung**.
- **Restrisiko:** TLS schützt gegen passives Mitlesen; mangels Zertifikatsprüfung
  bleibt ein **aktiver MITM** (jemand, der die Strecke zu `dps1.sonia.de`
  umlenken und ein beliebiges Zertifikat präsentieren kann) theoretisch möglich —
  Bind-Passwort und abgefragte Personendaten wären dann exponiert. Praktisch durch
  das interne/Campus-Netz deutlich gemildert.
- **NIS2/Art. 32:** Integrität/Authentizität der Übertragung.
- **Maßnahme:** `TrustAllTrustManager` durch echten Truststore (CA-Kette) **mit
  Hostname-Verifikation** ersetzen. Geringer Aufwand, schließt den Befund.

### 3.2 [WEITGEHEND ENTSCHÄRFT] Transportverschlüsselung der Web-Anwendung
- **Bewertung mit Deployment:** TLS wird am Edge durch **Traefik** erbracht
  (einziger TLS-Endpunkt, **automatisierte Zertifikatspflege per ACME
  DNS-01**). Die App selbst läuft produktiv **als HTTP auf 8080**; dieser Verkehr
  bleibt auf das **isolierte, ungeroutete Docker-/Hostnetz (10/8)** beschränkt.
  Dies ist ein für Art. 32 **akzeptables Muster** (Edge-TLS + interne, segmentierte
  Klartextstrecke).
- **Residualrisiko:** sehr gering. Traefik und App liegen **im selben
  `docker-compose` auf einem Host**; die HTTP-Strecke ist reiner
  Container-zu-Container-Verkehr auf dem internen Docker-Netz und verlässt den
  Host nicht. Kein mTLS/internes TLS erforderlich.
- **Verbleibende Härtungspunkte (P1):**
  - **HSTS** an **Traefik** setzen (App liefert HTTP; HSTS gehört an den TLS-Edge).
  - `frameOptions().disable()` → X-Frame-Options ist deaktiviert; auf
    `sameOrigin` bzw. CSP `frame-ancestors` umstellen (Clickjacking).
  - `X-Content-Type-Options: nosniff` ergänzen (App oder Traefik).
- **NIS2/Art. 32:** Edge-Transportverschlüsselung erfüllt; interne Strecke per
  Netzisolation kompensiert; Header-Härtung offen.

### 3.3 [WEITGEHEND ENTSCHÄRFT] Sekret-Management
- **Befund (mit Prod-Config):** In der **produktiven** `config.yaml` sind **alle
  Anmeldeinformationen `{AES256}`-verschlüsselt** — OAuth-Client-Secret,
  DB-Passwort, Storage-API-Token, HMAC-Secret, LDAP-Bind-Passwort. **Kein
  Klartext-Secret in Produktion.** (Das früher genannte Klartext-Secret stammt aus
  der Repo-**Beispiel**datei, nicht aus dem Prod-Mount.)
- **Verbleibend (P2):** Die Entschlüsselung beruht auf **`data/secret.bin`**
  (lokal, Rechte `0400`); es gibt **keine Rotation** und **kein externes
  Secret-Management/HSM**. `secret.bin` und `config.yaml` liegen im selben
  `./data`-Volume → Schutz des Volumes ist kritisch.
- **NIS2/Art. 32:** Vertraulichkeit von Anmeldeinformationen, Schlüsselverwaltung.
- **Maßnahme:** mittelfristig externes Key-Management/Rotation (P2);
  `./data`-Volume-Zugriff strikt beschränken.

### 3.4 [PROD] Offene Test-Endpunkte
- **Befund:** `/system/test/**` ist in `ClientSecurityConfig` `permitAll` und
  nicht über `app.development` abgesichert (`SystemTestController` erzeugt
  bewusst 4xx/5xx). Die Traefik-Regeln erlauben **GET** und **blockieren
  `/system/test/**` nicht** → über `https://account.sonia.de/system/test/**`
  **unauthentisiert erreichbar** (bestätigt durch die Prod-Compose).
- **NIS2/Art. 32:** Angriffsfläche/Informationspreisgabe.
- **Maßnahme:** Test-Endpunkte in Produktion deaktivieren (Profil-Gate) oder
  entfernen; alternativ als Sofortmaßnahme an Traefik per Pfad-Regel blocken.

### 3.5 [WEITGEHEND ENTSCHÄRFT] Actuator-Endpunkte `permitAll`
- **Bewertung mit Deployment:** Geringes Risiko — `/actuator/**` ist zwar
  `permitAll`, der Management-Port (9000) ist jedoch **nicht von außen
  erreichbar**: Host-IP-Filter nur 80/443, kein Port-Mapping, und `net108`
  outgoing-only (3.7). Exposition auf `health`, `show-details: NEVER`.
- **Maßnahme (optional):** Management-Port netzintern halten (so bereits der Fall);
  Actuator zusätzlich authentifizieren, falls Traefik ihn künftig routet.

### 3.6 [PROD — REDUZIERT] Datenbank-Härtung
- **Kontext (mit Prod-Config):** Die App verbindet sich intern auf
  `jdbc:postgresql://signaturedb:5432/...` (Container-zu-Container) mit
  **`{AES256}`-verschlüsseltem Passwort** → Passwort-Authentisierung ist aktiv.
- **Befund 1 — Host-Port (gering):** PostgreSQL ist mit **`ports: "5432:5432"`**
  auf den Host gemappt (nicht nur `expose`). Extern durch IP-Filter (nur 80/443)
  blockiert, aber **unnötige Bindung** auf Host-Interfaces. **Maßnahme:**
  Host-Mapping entfernen (App nutzt ohnehin `signaturedb:5432`) oder auf
  `127.0.0.1` binden.
- **Befund 2 — Init-Env:** Im Compose kein `POSTGRES_PASSWORD` — unkritisch, da das
  Daten-Volume bereits initialisiert ist und die App per Passwort authentisiert;
  zur Sicherheit `pg_hba.conf` auf **kein `trust`** prüfen.
- **Befund 3 — Schema (verbleibt):** `ddl-auto: update` ist in Produktion riskant
  (unkontrollierte Schema-Drift). **Maßnahme:** `ddl-auto: validate` + verwaltete
  Migrationen (Flyway/Liquibase). *(JDBC-TLS unnötig, da Container-zu-Container.)*

### 3.7 [GEKLÄRT] App dual-homed auf öffentlicher IP `141.41.8.112` (`net108`)
- **Sachverhalt:** Der App-Container hängt zusätzlich am Netz `net108` mit fester
  IP `141.41.8.112`. **Laut Betrieb ist diese IP per IP-Filter ausschließlich
  ausgehend (outgoing) konfiguriert; eingehender Verkehr wird vollständig
  blockiert.**
- **Bewertung:** Damit dient `net108` nur der **ausgehenden** Anbindung
  (LDAP/IdP/eID). Ein eingehender Direktzugriff auf `:8080`/`:9000` unter Umgehung
  von Traefik ist **ausgeschlossen** → **kein Befund**.
- **Empfehlung (Defense-in-Depth):** Regel dokumentieren/regelmäßig verifizieren
  (Firewall-Review), damit die outgoing-only-Eigenschaft erhalten bleibt.

---

## 4. Nur entwicklungsrelevante Befunde (kein Produktionsverstoß)

> Diese Punkte betreffen **ausschließlich** Entwicklungsprofile (insb. `iddev`)
> bzw. lokale Konfigurationen und wirken **nicht** im gehärteten Produktivbetrieb —
> dennoch dokumentiert, da die mitgelieferte `config.yaml` Verwechslungsgefahr birgt.

- **[DEV] Sehr ausführliches Logging** (`l9g: TRACE`): vollständige **JWT-Claims**
  (Name, Mail, Kundennummer, Publisher-JSON) in `ApiSignaturePadController`,
  komplette **LDAP-Einträge** und `userInfo`-Objekte, **Principal**-Objekte,
  **LDAP-Bind-DN**, Such-Filter. Im Produktivprofil (INFO) **nicht** sichtbar.
- **[DEV] Swagger-UI / OpenAPI** aktiviert (in Default/Prod deaktiviert).
- **[DEV] Vault-Masterkey-TTL** = 9 000 000 ms (2,5 h) statt 120 s → längere
  Schlüsselexposition; nur `iddev`.
- **[DEV] `debug-oidc: true`, `javascript.log-level: debug`** → erweitertes
  Debug-Verhalten clientseitig.

---

## 5. Stärken (positiv festzuhalten)

- **Verschlüsselung at-rest:** AES-256-GCM für Dateien (`CryptoHandler`),
  Feldverschlüsselung für `secret` (crypto-jpa); `secret.bin` mit Rechten `0400`.
- **Vault-Konzept:** In-Memory-Masterkey mit TTL, Entsiegelung über
  WebAuthn-Admin-Keys; Zugriff auf entschlüsselte Daten nur bei entsiegeltem Vault.
- **Authentifizierung:** OAuth2/OIDC mit **PKCE**; rollenbasierte Autorisierung mit
  zusätzlicher Vault-Entsiegelungs-Bedingung für Audit-Funktionen.
- **Upload-API:** Bearer-Token (konstante-Zeit-Vergleich) **plus** HMAC-SHA256 mit
  mitsigniertem Zeitstempel; SHA-256-Integritätsprüfsummen.
- **Datensparsame DTOs** an einzelnen Stellen (z. B. `DtoLastSeenUser`,
  `description()`-Reduktion in `StorageObject`).
- **CSP** gesetzt; Management-Port getrennt/nicht publiziert; Swagger in Prod
  deaktiviert.
- **Netz-/Edge-Architektur:** Traefik + App + PostgreSQL in **einem
  `docker-compose` auf einem Host**; Web-Verkehr Container-zu-Container.
  Docker-Host im **ungerouteten 10/8-Netz**, per **IP-Filter nur 80/443**
  (= Traefik) erreichbar. TLS-Edge über **Traefik** mit **automatisierter
  Zertifikatserneuerung (ACME)** — keine abgelaufenen Zertifikate.
  *(Einschränkung: App zusätzlich auf `net108`/öffentlicher IP — siehe 3.7.)*
- **Edge-Härtung an Traefik:** erzwungener **HTTP→HTTPS-Redirect (permanent)**,
  **Methoden-Whitelist** (nur GET/HEAD/POST → sonst 405), **Blocklist
  gefährlicher Dateiendungen**, dedizierter `error-responder`.
- **Zentrales Logging:** Docker-Logs werden per **GELF an einen Graylog-Cluster**
  ausgeleitet → zentrale, dauerhafte und auswertbare Protokollierung
  (gut für NIS2-Monitoring/Rechenschaft). *(PII-Inhalte governance-pflichtig — 2.3.)*

---

## 6. Maßnahmen­plan (priorisiert)

> **Erledigt:** (a) automatische Löschung nach Karenzzeit (P90D) inkl. DB-Datensätze,
> Dateien und `SdbLastSeen`; (b) **manuelle Sofort-Erasure** pro Person
> (`POST …/person/{uid}/delete`); (c) **Auskunfts-Export** als ZIP
> (`GET …/export/person/{uid}`, entschlüsselte Metadaten ohne Publisher/Binärdaten);
> (d) **Lese-Audit-Trail** (`AUDIT_READ` auf INFO → Graylog) für alle lesenden
> Admin-Zugriffe. Bedienung über `/admin/useraudit`. (Art. 5/15/17/32). ✔

### P0 — vorrangig (DSGVO)
1. **PII aus INFO/WARN-Logs** entfernen (pseudonyme IDs); **Retention-/Zugriffs-
   Policy** für Graylog **und** das lokale Logfile (aktuell 400 Tage) — schließt
   auch die `AUDIT_READ`-Streams ein. *(Art. 5/32)*
2. **Test-Endpunkte** in Prod deaktivieren (Profil-Gate oder Traefik-Sperre für
   `/system/test/**`). *(NIS2)*

### P1 — kurzfristig
3. **DSFA (Art. 35)** durchführen/dokumentieren; Art.-9-Erlaubnis belegen.
4. **LDAP-Zertifikatsprüfung:** `TrustAllTrustManager` durch echten Truststore +
   Hostname-Verifikation ersetzen (LDAPS läuft bereits). *(Art. 32/NIS2)*
5. **Security-Header** an **Traefik**: **HSTS**, `nosniff`,
   `frameOptions`/`frame-ancestors`. **`ddl-auto: validate`** + Migrations-Tool.
6. **PostgreSQL-Host-Port** `5432:5432` entfernen/auf `127.0.0.1` binden;
   `pg_hba.conf` auf kein `trust` prüfen. *(Defense-in-Depth)*
7. **`app.development: false`** setzen (Hygiene; aktuell wirkungslos, aber
   irreführend).

### P2 — mittelfristige Härtung
8. Externes Secret-/Key-Management (HSM/KMS), `secret.bin`-Rotation,
   Mehr-Personen-Entsiegelung (Threshold).
9. **PII-Redaktion vor GELF/Graylog** + Integritäts-/Aufbewahrungs-Governance der
   Log-Streams.
10. Rate-Limiting/WAF für `/api/v1/**`.

---

## 7. Anforderungs-Mapping (Kurzüberblick)

### DSGVO
| Artikel | Thema | Status |
|---|---|---|
| Art. 5 Abs. 1 lit. c | Datenminimierung | teils (PII in Logs) |
| Art. 5 Abs. 1 lit. e | Speicherbegrenzung | **umgesetzt** (autom. Löschung nach P90D) |
| Art. 5 Abs. 2 / 32 | Rechenschaft/Sicherheit | **Lese-Audit umgesetzt** (`AUDIT_READ`→Graylog); Rest: Log-PII-Redaktion |
| Art. 9 | Besondere Kategorien | **prüfpflichtig** |
| Art. 15 / 20 | Auskunft/Portabilität | Metadaten-**Export (ZIP)** umgesetzt (ohne Publisher/Binärdaten) |
| Art. 17 | Löschung | autom. nach Karenzzeit **und** manuelle Sofort-Erasure **umgesetzt** |
| Art. 16 | Berichtigung | für Identifizierungs­artefakte gesperrt (sachgerecht: löschen + neu) |
| Art. 32 | Sicherheit der Verarbeitung | überwiegend gut; Rest: LDAP-Cert, Header |
| Art. 35 | DSFA | **durchzuführen** |

### NIS2 (Auswahl, Art. 21)
| Anforderung | Status |
|---|---|
| Verschlüsselung at-rest | gut (AES-256 Datei/Feld, Vault) |
| Verschlüsselung in transit | Web: Edge-TLS (ACME) + interne Container-Strecke; **LDAP: LDAPS aktiv, aber keine Zertifikatsprüfung** (TrustAll) |
| Zugriffskontrolle | gut (RBAC+Vault, Methoden-Whitelist); Test-Endpunkte offen |
| Protokollierung/Monitoring | zentral via GELF/Graylog + Rotation + **Lese-Audit-Trail** (gut); offen: PII-Redaktion |
| Schlüssel-/Secret-Management | gut (alle Secrets `{AES256}`); P2: Rotation/HSM, `secret.bin`-Schutz |
| Incident-Response / BCM / Lieferkette | **organisatorisch zu belegen** |

---

## 8. Status der zuvor offenen Punkte
- **Geklärt/erledigt:** Prod-Config bestätigt (**alle Secrets `{AES256}`**,
  `l9g: INFO`, Swagger aus, Vault-TTL 180 s); **LDAPS** aktiv (Port 636);
  DB-Verbindung intern mit verschlüsseltem Passwort; **`net108` outgoing-only**;
  TLS-Edge/ACME, Methoden-Whitelist, Extension-Blocklist; **Log-Rotation**
  (400 Tage) + GELF/Graylog; `app.development` ohne Code-Wirkung.
- **Verbleibende technische Restpunkte (P1):** LDAP-**Zertifikatsprüfung**
  (TrustAll, 3.1); **HSTS/`nosniff`** + `/system/test/**`-Sperre an Traefik;
  `ddl-auto: validate`; PostgreSQL-Host-Port-Mapping; `app.development:false`.
- **Organisatorisch zu klären:**
  - **Log-Retention/Zugriff:** 400 Tage PII im lokalen Logfile **und** in Graylog —
    Aufbewahrungs-/Zugriffs-Policy festlegen; ggf. PII-Redaktion.
  - **DSFA (Art. 35)** und Art.-9-Erlaubnistatbestand.
  - **Aufbewahrungs-/Löschfristen** je Datenart bestätigen (technische Standardfrist
    `delete-grace-period: P90D` ist umgesetzt).
  - **Auftragsverarbeitungs­verträge** (eID-Dienst, LDAP-/Hosting-Betrieb).
