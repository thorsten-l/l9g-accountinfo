# Quellcode-Statistik — l9g-accountinfo

> **Zweck dieses Dokuments**
> Überblick über Umfang und Struktur der Codebasis **l9g-accountinfo** (Lines of
> Code, Sprachverteilung, Paket-/Schichtgliederung) als ergänzende technische
> Kennzahl zu **ACCOUNT_COMPLIANCE.md** und **ACCOUNT_PROZESS_BESCHREIBUNG.md**.
>
> **Stand:** 2026-08-23 · **Branch:** `one-key-to-bind-them` · **Version:** 2.5.0
> **Methodik:** `cloc` 2.10 über `src/`. Ausgenommen: Build-Artefakte
> (`target/`), über Maven/WebJars eingebundene Fremdbibliotheken (Bootstrap,
> jQuery, Font-Awesome, signature_pad …) sowie Binärassets (PNG/JPG/ICO).
>
> **Änderung gegenüber 2.4.1:** Die Zahlen für `src/main` sind mit der
> Vorversion vergleichbar; neu hinzu kommt `src/test`, das in 2.4.1 leer war.
> Wo unterschieden wird, ist das ausgewiesen.

---

## 1. Kennzahlen auf einen Blick

| Kennzahl | Wert | 2.4.1 |
|---|---|---|
| Quelldateien `src/` (textuell) | **174** | 158 |
| Gesamt-Zeilen `src/` | **24.862** | 19.537 |
| davon `src/main` | 153 Dateien · 19.645 Zeilen | 158 · 19.537 |
| davon `src/test` | 21 Dateien · 5.217 Zeilen | 0 |
| Java-Dateien (gesamt) | **93** | 70 |
| Java-Zeilen (gesamt) | **17.042** | 11.146 |
| davon Code / Kommentar / Leerzeilen | **9.960 / 5.308 / 1.774** | 6.209 / 3.958 / 979 |
| Java-Pakete `src/main` (mit Klassen) | **11** | 11 |
| Thymeleaf-Templates (HTML) | **21** (3.654 Zeilen) | 21 (3.650) |
| JavaScript-Dateien | **14** (1.961 Zeilen) | 14 (1.957) |
| Maven-Abhängigkeiten | **25** | 24 |
| **Automatisierte Tests** | **292** (21 Testklassen) | **0** |
| Git-Commits | **59** (1 Autor) | 59 |
| Entwicklungszeitraum | 2026-01-30 – 2026-08-23 | 2026-01-30 – 2026-06-24 |

**Technologiebasis:** Java 21 · Spring Boot 3.5.16 · PostgreSQL · Thymeleaf ·
WebSocket · OAuth2/OIDC · L9G-Crypto-Bibliothek (`crypto-core/-spring/-jpa`) ·
JUnit 5 / Mockito / AssertJ (Testumfang).

> Der Produktivcode ist gegenüber 2.4.1 nur um rund 680 Java-Zeilen gewachsen
> (zwei neue Klassen für die Logout-Token-Verifikation, erweiterte Prüfungen in
> `StorageController` und `LdapService`, entfernter toter Verifikationspfad in
> `JwtService`). Der Sprung in der Gesamtzahl geht fast vollständig auf die neue
> Testbasis zurück.

---

## 2. Sprachverteilung (nach Zeilen)

| Sprache / Typ | Dateien | Zeilen | Anteil |
|---|---:|---:|---:|
| Java (`src/main`) | 72 | 11.825 | 47,6 % |
| Java (`src/test`) | 21 | 5.217 | 21,0 % |
| HTML (Thymeleaf) | 21 | 3.654 | 14,7 % |
| JavaScript | 14 | 1.961 | 7,9 % |
| SVG (Icons/Assets) | 18 | 774 | 3,1 % |
| Properties (i18n/Config) | 3 | 503 | 2,0 % |
| JSON | 15 | 444 | 1,8 % |
| CSS | 4 | 302 | 1,2 % |
| YAML | 1 | 97 | 0,4 % |
| TXT | 3 | 50 | 0,2 % |
| SQL | 1 | 21 | 0,1 % |
| XML | 1 | 14 | 0,1 % |
| **Summe** | **174** | **24.862** | **100 %** |

> Die **SVG-Dateien** sind überwiegend Sprach-/Flaggen-Icons und statische Assets,
> nicht handgeschriebener Anwendungscode. Ohne Assets (SVG) und ohne Templates
> umfasst der reine Anwendungscode (Java + JS + CSS) rund **19.305 Zeilen**,
> davon **5.217** Testcode.
>
> Die SVG-Zählung weicht von 2.4.1 ab (18 statt 25 Dateien): `cloc` 2.10 ordnet
> einige Icon-Dateien nicht mehr als SVG ein. Das betrifft nur Assets, keinen
> Anwendungscode.

---

## 3. Java-Codebasis im Detail

### 3.1 Code-/Kommentar-Verhältnis

| Kategorie | `src/main` | Anteil | `src/test` | Anteil |
|---|---:|---:|---:|---:|
| Code | 6.432 | 54,4 % | 3.528 | 67,6 % |
| Kommentar | 4.348 | 36,8 % | 960 | 18,4 % |
| Leerzeilen | 1.045 | 8,8 % | 729 | 14,0 % |
| **Summe** | **11.825** | **100 %** | **5.217** | **100 %** |

> Der hohe Kommentaranteil in `src/main` (≈ 37 %) erklärt sich vor allem durch
> **Lizenz-Header** in jeder Datei sowie Javadoc/Inline-Dokumentation — ein
> Indikator für gepflegte, dokumentierte Quellen.
>
> Im Testcode ist der Anteil geringer, aber inhaltlich dicht: die Kommentare
> begründen dort, **warum** ein bestimmtes Verhalten festgeschrieben ist —
> insbesondere bei den vier bewusst so belassenen Verhaltensweisen (siehe
> `DEFEKTE.md`) und bei den Kompatibilitätswächtern für das WebSocket-Format und
> die Pad-Signaturen.

### 3.2 Zeilen je Paket (`l9g.account.info.*`)

| Paket | Dateien | Zeilen | 2.4.1 | Rolle |
|---|---:|---:|---:|---|
| `controller.api` | 10 | 2.906 | 2.705 | REST-API-Controller |
| `service` | 10 | 2.063 | 1.918 | Geschäftslogik (LDAP, FileStorage, SignaturePad, Logout-Token …) |
| `controller` | 8 | 1.533 | 1.471 | MVC-/Thymeleaf-Controller |
| `db` | 6 | 1.095 | 1.095 | `DbService`, Repositories, Lösch-Scheduler |
| `config` | 8 | 1.039 | 884 | Spring-`@Configuration` (Security, OAuth2, i18n, Logout-Token …) |
| `db.model` | 7 | 894 | 894 | JPA-Entitäten |
| `dto` | 11 | 839 | 753 | Datentransferobjekte |
| `ws` | 4 | 559 | 541 | WebSocket (Signature-Pad) |
| `vault` | 4 | 387 | 355 | Vault-/Masterkey-Verwaltung |
| `(root)` | 2 | 333 | 333 | Entry-Point, `GlobalExceptionHandler` |
| `vault.api` | 2 | 200 | 197 | Vault-Admin-Key-Schnittstelle |
| **Summe** | **72** | **11.848** | **11.146** | |

> Die Paketzahlen sind rein zeilenbasiert (`wc -l`) erhoben und liegen daher um
> 23 Zeilen über der `cloc`-Summe aus Abschnitt 1 (11.825) — eine
> Methodendifferenz, kein Widerspruch.
>
> Die beiden neuen Dateien sind `service/LogoutTokenVerifier.java` und
> `config/LogoutTokenConfig.java` (Verifikation des OIDC-Backchannel-Logout-
> Tokens). `service` wächst trotz des um 180 Zeilen **geschrumpften**
> `JwtService` — dort wurde der tote Verifikationspfad entfernt.

### 3.2a Testcode je Paket

| Paket | Testklassen |
|---|---:|
| `service` | 6 |
| `controller.api` | 3 |
| `config` | 2 |
| `db` | 2 |
| `db.model` | 2 |
| `dto` | 2 |
| `controller` | 1 |
| `vault` | 1 |
| `ws` | 1 |
| `(root)` | 1 |
| **Summe** | **21** |

> Die Testpakete spiegeln die Produktivpakete. Getestet wird ohne
> Spring-Context, ohne Datenbank und ohne Netzzugriff; LDAP-Pfade laufen gegen
> ein In-Memory-Verzeichnis des UnboundID-SDK. Gesamtlaufzeit unter drei
> Sekunden, offline ausführbar.

### 3.3 Strukturierung nach Klassentyp

| Typ | Anzahl |
|---|---:|
| MVC-Controller (`@Controller`) | 8 |
| REST-API-Controller (`@RestController`) | 11 |
| Services (`@Service`) | 9 |
| Konfigurationsklassen (`@Configuration`) | 9 |
| JPA-Entitäten (`@Entity` + `@MappedSuperclass`) | 4 + 1 |
| DTOs (Paket `dto`, davon 9 Records) | 11 |
| `@ControllerAdvice` | 2 |
| Sonstige `@Component` | 2 |
| **Testklassen** | **21** |

### 3.4 Größte Java-Dateien (Top 10)

| Zeilen | Datei | 2.4.1 |
|---:|---|---:|
| 998 | `controller/api/ApiAdminController.java` | 998 |
| 691 | `service/LdapService.java` | 633 |
| 669 | `controller/AdminController.java` | 669 |
| 666 | `db/DbService.java` | 666 |
| 633 | `controller/api/ApiSignaturePadController.java` | 633 |
| 478 | `controller/api/StorageController.java` | 312 |
| 390 | `config/ClientSecurityConfig.java` | 390 |
| 308 | `service/FileStorageService.java` | 308 |
| 254 | `ws/SignaturePadWebSocketHandler.java` | 236 |
| 235 | `db/model/SdbUuidObject.java` | 235 |

> `JwtService` ist aus den Top 10 herausgefallen: 289 → 109 Zeilen, nachdem der
> nie aufgerufene und fehlerhafte Signaturprüfpfad entfernt wurde. `LdapService`
> und `StorageController` sind durch die zusätzlichen Eingabeprüfungen gewachsen.

---

## 4. Frontend / Ressourcen

| Bereich | Umfang |
|---|---|
| Thymeleaf-Templates | 21 HTML-Dateien (3.654 Zeilen) |
| JavaScript (eigene Skripte) | 14 Dateien (1.961 Zeilen) |
| CSS | 4 Dateien (302 Zeilen) |
| i18n-Sprachbündel | `messages.properties`, `messages_de.properties`, `messages_en.properties` |
| Statische Assets | Flaggen-/Icon-SVGs, Bilder (PNG/JPG/ICO) |

---

## 5. Abhängigkeiten (Maven, Auswahl)

**Spring-Boot-Starter:** `web`, `oauth2-client`, `websocket`, `thymeleaf`,
`data-jpa`, `actuator`, `aspects`, `thymeleaf-extras-springsecurity6`.

**Fachlich/Infrastruktur:** `postgresql` (JDBC), `unboundid-ldapsdk` (LDAP),
`springdoc-openapi-starter-webmvc-ui` (OpenAPI), `caffeine` (Cache),
`javase` (ZXing/Barcode), `lombok`.

**L9G-Crypto (eigene Bibliothek):** `crypto-core`, `crypto-spring`, `crypto-jpa`
— transparente Feld-/Dateiverschlüsselung.

**Frontend (WebJars):** `bootstrap`, `bootstrap-icons`, `font-awesome`, `jquery`,
`popper.js`, `signature_pad`, `webjars-locator-core`.

**Test (Scope `test`):** `spring-boot-starter-test` — bringt JUnit 5, Mockito,
AssertJ, Hamcrest und `spring-test`. Der In-Memory-LDAP-Server stammt aus dem
bereits vorhandenen `unboundid-ldapsdk`, es ist also keine weitere
Test-Abhängigkeit nötig.

---

## 6. Hinweise zur Interpretation

- Die Zahlen erfassen **eigenen Quellcode** im Repository; über Maven/WebJars
  eingebundene Fremdbibliotheken sind **nicht** enthalten (würden die effektive
  Codemenge des Gesamtsystems deutlich erhöhen).
- **Automatisierte Tests seit 2.5.0:** 292 Unit-Tests in 21 Testklassen
  (`src/test`), ohne Spring-Context, Datenbank oder Netzzugriff. Schwerpunkt sind
  die sicherheits- und DSGVO-relevanten Pfade: Storage-API (Bearer-Token, HMAC,
  Replay), Backchannel-Logout, LDAP-Filter-Bereinigung, Vault-Ent-/Versiegelung,
  Löschlogik (`DbService.deleteUserData`, `DbDeletionScheduler`) und die
  Integritäts-Metadaten der Secret-Datensätze. Zusätzlich Kompatibilitätswächter
  für das WebSocket-Format der Signatur-Pads und für die unveränderte
  Verifikation bestehender Pad-Signaturen. Ergänzend wird weiterhin manuell
  getestet (Ende-zu-Ende gegen IdP, LDAP und Pads).
- Die Suite hat beim Aufbau **22 Befunde** aufgedeckt; 18 wurden behoben, 4 nach
  fachlicher Prüfung als gewolltes Verhalten bestätigt und durch Tests
  festgeschrieben. Vollständige Liste mit Ursache, Auswirkung und
  Kompatibilitätsbewertung: `DEFEKTE.md`.
- Die LOC-Werte sind **zeilenbasiert** (inkl. Kommentaren/Leerzeilen, soweit nicht
  separat ausgewiesen) und als Größenordnung, nicht als exakte Metrik, zu verstehen.
