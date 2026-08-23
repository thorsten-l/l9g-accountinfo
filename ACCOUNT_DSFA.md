# Datenschutz-Folgenabschätzung (DSFA) — l9g-accountinfo

> **Zweck dieses Dokuments**
> Diese Datenschutz-Folgenabschätzung (DSFA) nach **Art. 35 DSGVO** bewertet die mit
> der Anwendung **l9g-accountinfo** verbundenen Risiken für die Rechte und Freiheiten
> natürlicher Personen und dokumentiert die zu ihrer Beherrschung getroffenen bzw.
> geplanten Maßnahmen. Sie dient als Entscheidungs- und Vorlagegrundlage für den
> **Datenschutzbeauftragten (DSB)**, den **Verantwortlichen** und den
> **Compliance-Officer**.
>
> **Stand:** 2026-08-23 · **Version:** 2.5.0 · **Branch:** `one-key-to-bind-them`
> **Änderung 2026-06-28:** R-01, R-03 und R-11 neu bewertet (Zugriffskreis auf 4
> benannte, auditierte RZ-Leitungspersonen präzisiert; Admin-Zugang per phishing-
> resistenter FIDO2-MFA); Backup/BCM (ZFS, Air-Gap-Schatten-RZ, Restore getestet,
> 30 Tage Retention) als Bestandsmaßnahme ergänzt; M-11 geschlossen.
> **Methodik:** Strukturierte DSFA nach Art. 35 Abs. 7 DSGVO auf Basis der
> automatisierten Quellcode-/Konfigurationsanalyse.
> **Grundlagendokumente:**
> - **ACCOUNT_PROZESS_BESCHREIBUNG.md** — Verarbeitungstätigkeiten, Datenflüsse,
>   Dateninventar (Art. 30).
> - **ACCOUNT_COMPLIANCE.md** — technische DSGVO-/NIS2-Bewertung, Befunde,
>   Maßnahmenplan.
>
> **Haftungsausschluss:** Dies ist eine **technisch fundierte Risikoanalyse**, **keine
> Rechtsberatung**. Die abschließende rechtliche Würdigung (insbesondere
> Erlaubnistatbestand nach Art. 9, Erforderlichkeit/Verhältnismäßigkeit,
> Restrisiko-Akzeptanz und ggf. Konsultationspflicht nach Art. 36) obliegt dem DSB
> bzw. dem Verantwortlichen.

---

## 0. Management Summary

l9g-accountinfo verarbeitet **besondere Kategorien personenbezogener Daten nach
Art. 9 DSGVO** (Lichtbilder, Ausweis-/MRZ-Daten, handschriftliche Unterschriften,
Video/Audio aus Online-Identifizierung) im Kontext von **Identitäts- und
Ausweisprozessen**. Diese Verarbeitung erfüllt mehrere Kriterien, die eine DSFA nach
Art. 35 Abs. 3 lit. b DSGVO **verpflichtend** machen (umfangreiche Verarbeitung
besonderer Datenkategorien, biometrische Identifizierung).

**Gesamtbewertung:** Die Anwendung besitzt eine **architektonisch solide
Schutzbasis** — Feld- und Dateiverschlüsselung (AES-256), Vault mit Masterkey-TTL
und WebAuthn-Entsiegelung, OAuth2/OIDC mit PKCE, HMAC- und replay-gesicherte
Upload-API, verifiziertes OIDC-Backchannel-Logout,
zentrales Logging (GELF/Graylog), Netzsegmentierung sowie ein **implementiertes
Löschkonzept (P90D)** inkl. manueller Sofort-Erasure, Auskunfts-Export und
Lese-Audit-Trail. Nach Umsetzung dieser Maßnahmen verbleiben **mittlere bis geringe
Restrisiken**, die mit den im Maßnahmenplan (Kap. 8) genannten Punkten weiter
gesenkt werden.

**Wesentliche verbleibende Risiken (Schwerpunkt):**
1. **Personenbezogene Daten in Logs** (INFO/WARN) → Graylog + lokales Logfile
   (Retention 400 Tage) → Redaktion/Governance erforderlich (R-01).
2. **Art.-9-Erlaubnistatbestand** noch formal zu belegen (R-09).
3. **LDAP ohne Zertifikatsprüfung** (`TrustAllTrustManager`) → MITM-Restrisiko (R-05).
4. **Offene Test-Endpunkte** `/system/test/**` (R-06).

**Empfehlung:** Die Verarbeitung ist bei Umsetzung der Maßnahmen P0/P1 (Kap. 8)
**datenschutzkonform betreibbar**. Eine vorherige **Konsultation der Aufsichtsbehörde
nach Art. 36** ist nach aktueller Einschätzung **nicht erforderlich**, sofern die
Restrisiken auf das ausgewiesene Niveau gesenkt werden; die endgültige Entscheidung
trifft der DSB.

---

## 1. Notwendigkeit der DSFA (Schwellwertanalyse, Art. 35 Abs. 1 & 3)

Eine DSFA ist durchzuführen, wenn eine Verarbeitung voraussichtlich ein **hohes
Risiko** für die Rechte und Freiheiten natürlicher Personen zur Folge hat. Geprüft
gegen Art. 35 Abs. 3 sowie die Kriterienliste des EDSA (WP248):

| Kriterium | Trifft zu? | Begründung |
|---|---|---|
| Verarbeitung besonderer Kategorien (Art. 9) | **Ja** | Biometrie (Lichtbild, Unterschrift, Portrait, Video/Audio), Ausweis-/MRZ-Daten |
| Umfangreiche Verarbeitung | **Ja** | Identifizierung aller Endkunden/Antragsteller im Regelbetrieb |
| Bewertung/Scoring | Nein | Keine automatisierte Bewertung/Profilbildung |
| Automatisierte Entscheidung mit Rechtswirkung | Teilweise | Identitätsnachweis steuert Karten-/Kontoausgabe; menschlich begleitet |
| Systematische Überwachung | Nein | Keine Verhaltensbeobachtung |
| Daten schutzbedürftiger Personen | Möglich | Endkunden in Abhängigkeitsverhältnis (Antragsteller) |
| Innovative Nutzung neuer Technologien | **Ja** | Online-eID-Identifizierung, biometrische Erfassung, WebAuthn/PRF |
| Datenabgleich/Zusammenführung | **Ja** | Zusammenführung eID-Ergebnis + LDAP-Verzeichnis + Signaturerfassung |
| Verhinderung der Rechtsausübung | Reduziert | Frühere `immutable`-Löschsperre entfernt (Art. 17 nun erfüllbar) |

**Ergebnis:** Es sind **≥ 2 Kriterien** (tatsächlich mehrere) erfüllt, darunter das
für sich allein bereits auslösende Kriterium **„umfangreiche Verarbeitung besonderer
Kategorien" (Art. 35 Abs. 3 lit. b)**. → **Eine DSFA ist verpflichtend.**

---

## 2. Systematische Beschreibung der Verarbeitung (Art. 35 Abs. 7 lit. a)

### 2.1 Gegenstand und Zwecke

l9g-accountinfo ist eine Spring-Boot-Webanwendung (Java 21, Spring Boot 3.5.x,
PostgreSQL, Thymeleaf, WebSocket, OAuth2/OIDC) zur Unterstützung von
**Identitäts- und Ausweisprozessen**: Ausgabe/Aktivierung von Benutzerkonten und
Chipkarten, Erfassung handschriftlicher Unterschriften über Signature-Pads sowie
Übernahme und Aufbewahrung von Ergebnissen eines externen Online-Identifizierungs-
(eID/WebID-)Dienstes („MIRA").

| # | Zweck | Rechtsgrundlage (durch DSB zu bestätigen) |
|---|-------|--------------------------------------------|
| 1 | Ausgabe/Aktivierung von Benutzerkonten und Chipkarten | Art. 6 Abs. 1 lit. b / e |
| 2 | Erfassung handschriftlicher Unterschriften als Nachweis | Art. 6 Abs. 1 lit. b/c; ggf. Art. 9 |
| 3 | Übernahme/Aufbewahrung Online-Identifizierung (Ausweis, Lichtbild, Video/Audio) | Art. 6 Abs. 1 lit. c; **Art. 9 Abs. 2** |
| 4 | Auditierung/Einsichtnahme durch berechtigte Administratoren | Art. 6 Abs. 1 lit. c/f |
| 5 | Verzeichnisabgleich (LDAP) und Löschkonzept | Art. 6 Abs. 1 lit. c/f |

### 2.2 Betroffene Personengruppen

- **Endkunden / Antragsteller** — identifizierte Personen (Signature-Pad, eID-Dienst,
  LDAP).
- **Mitarbeitende / Sachbearbeiter (Publisher)** — Vorgangsausführung, in Logs/LDAP
  protokolliert.
- **Administratoren** (ADMIN, AUDITADMIN, VAULTADMIN, TABADMIN) — Audit-Metadaten.

### 2.3 Datenkategorien (Kurzinventar)

Detailliertes Inventar siehe **ACCOUNT_PROZESS_BESCHREIBUNG.md Kap. 3**.

| Kategorie | Beispiele | Art. 9? | Speicherort / Schutz |
|---|---|---|---|
| Stammdaten | Name, E-Mail, Geburtsdatum, Adresse, Kunden-/Barcode-Nr. | nein | DB / LDAP; `secret` feldverschlüsselt |
| Ausweisdaten | Dokumenttyp/-nummer, Behörde, **MRZ**, Geburtsname/-ort, Staatsangeh. | **ja** | Datei (AES-256-GCM) / `secret`-Feld |
| Biometrie | Lichtbild, Portrait, **Unterschrift (PNG/SVG)**, Pass-Scans | **ja** | Datei (AES-256-GCM), JWT feldverschlüsselt |
| Multimedia | **Audio (mp3), Video (webm)** aus eID-Archiv | **ja** | ZIP-Datei (AES-256-GCM) |
| eID-Statusobjekt | vollständiges `IdentificationStatus`-JSON | **ja** | `SdbSecretData.secret` (feldverschlüsselt) |
| Protokoll-/Audit-Daten | Benutzername, userId, Aktion, Admin-ID, IP | teils | Logs → Graylog + lokales Logfile |

### 2.4 Datenflüsse (Kurzform)

1. **Unterschriftenerfassung:** Pad → WebSocket/REST → `FileStorageService` /
   `DbService.saveSignedJWT` → AES-256-Datei bzw. feldverschlüsseltes JWT
   (`immutable=true`). Audit: `PAD_START`/`SIGNATURE_OK`/`SIGNATURE_CANCEL`.
2. **Online-Identifizierung:** Externer eID-Dienst → `POST /api/v1/storage/objects`
   (Bearer-Token + HMAC-SHA256 + Frischefenster + Replay-Schutz)
   → `StorageController` →
   feldverschlüsseltes Status-JSON **oder** AES-256-verschlüsselte ZIP. Audit:
   `STORAGE_UPLOAD`.
3. **Einsichtnahme/Audit:** Admin → `/admin/useraudit` (Vault muss entsiegelt sein)
   → On-the-fly-Entschlüsselung über `VaultService`. Jeder Lesezugriff erzeugt
   `AUDIT_READ` (→ Graylog).
4. **Verzeichnisabgleich & Löschung:** `DbDeletionScheduler` (Cron) →
   `updateLastSeenFromLdap()` + `deleteExpiredUsers()` (P90D) →
   `DbService.deleteUserData()` (DB-Datensätze + Dateien + `SdbLastSeen`). Audit:
   `USER_DELETED`.

### 2.5 Empfänger / Schnittstellen

OAuth2/OIDC-Provider (Keycloak), LDAP-Verzeichnis, externer eID-Dienst (MIRA),
Signature-Pads, PostgreSQL, lokales Dateisystem, Graylog-Cluster (GELF), lokales
Logfile. **Keine Drittlandübermittlung durch die Anwendung selbst** (lt. Code);
Hosting-/Betriebsstandort organisatorisch zu dokumentieren.

### 2.6 Betriebsumgebung

Traefik + l9g-accountinfo + PostgreSQL + error-responder (nginx) in einem
`docker-compose` auf einem Docker-Host. **Traefik = einziger TLS-Endpunkt**
(ACME-Automatik, HTTP→HTTPS-Redirect, Methoden-Whitelist GET/HEAD/POST,
Extension-Blocklist). App intern HTTP/8080 im **ungerouteten 10/8-Netz**, per
IP-Filter nur 80/443 erreichbar. App zusätzlich auf `net108` (`141.41.8.112`) —
per IP-Filter **ausschließlich ausgehend**. Schlüssel/Config im Volume `./data`.
Administrativer Host-Zugang erfolgt **ausschließlich per SSH** und ist auf **vier
namentlich benannte RZ-Leitungspersonen** beschränkt: **Leiter des Rechenzentrums**,
**stellvertretender Leiter des Rechenzentrums**, **Leiter der RZ-IT-Gruppe
Infrastruktur** und **Leiter der RZ-IT-Gruppe Softwareentwicklung** (FIDO2-Schlüssel
`id_ed25519_sk`, freigegeben per **YubiKey 5C BIO**/Fingerprint). **Derselbe
4-Personen-Kreis** verfügt über den **lokalen Logzugriff** und den Zugriff auf das
**Graylog**.

---

## 3. Erforderlichkeit und Verhältnismäßigkeit (Art. 35 Abs. 7 lit. b)

| Prüfpunkt | Bewertung |
|---|---|
| **Zweckbindung (Art. 5 Abs. 1 lit. b)** | Klar definierte Zwecke (Identifizierung, Karten-/Kontoausgabe, Nachweis). Keine zweckfremde Weiterverarbeitung im Code erkennbar. |
| **Datenminimierung (Art. 5 Abs. 1 lit. c)** | Erfassung von Ausweis-/Biometriedaten ist für den Identifizierungs­zweck grundsätzlich erforderlich. **Einschränkung:** PII in INFO/WARN-Logs ist nicht minimal (→ R-01). DTOs teils datensparsam (`DtoLastSeenUser`). |
| **Erforderlichkeit der Datenkategorien** | Umfang des eID-Statusobjekts (MRZ, Geburtsname/-ort, Video/Audio) ist durch den externen Dienst vorgegeben; **fachlich zu prüfen**, ob alle Felder/Medien dauerhaft gespeichert werden müssen oder reduziert/früher gelöscht werden können. |
| **Speicherbegrenzung (Art. 5 Abs. 1 lit. e)** | **Umgesetzt:** automatische Löschung nach Karenzzeit P90D ab letztem LDAP-Kontakt inkl. Dateien; manuelle Sofort-Erasure. Datenartspezifische gesetzliche Fristen durch DSB zu bestätigen. |
| **Verhältnismäßigkeit** | Eingriffsintensität (Art.-9-Daten) ist hoch, jedoch durch starke TOM (Verschlüsselung, Vault, RBAC, Audit, Löschkonzept) und engen Verwendungskontext abgefedert. Mildere Mittel (z. B. Nicht-Speicherung der Biometrie) sind durch den Nachweiszweck eingeschränkt — **fachliche Abwägung durch DSB**. |
| **Rechtsgrundlage / Art.-9-Erlaubnis** | Erlaubnistatbestand nach Art. 9 Abs. 2 ist **formal zu belegen** (→ R-09). |
| **Transparenz (Art. 12–14)** | Informationspflichten gegenüber Betroffenen organisatorisch sicherzustellen (Datenschutzhinweise bei Identifizierung/Unterschrift). |

**Zwischenfazit:** Erforderlichkeit für die Kernverarbeitung ist plausibel;
Verhältnismäßigkeit überwiegend gegeben. Offene Punkte: Log-Minimierung,
Art.-9-Erlaubnis, fachliche Bestätigung von Umfang und Fristen.

---

## 4. Risikobewertung (Art. 35 Abs. 7 lit. c)

### 4.1 Bewertungsmaßstab

Bewertet werden Risiken für die **Rechte und Freiheiten natürlicher Personen** anhand
**Eintrittswahrscheinlichkeit (W)** und **Schadensschwere (S)**, je auf der Skala
**1 = gering, 2 = mittel, 3 = hoch**. Risikowert = W × S.

| Risikowert | Stufe |
|---|---|
| 1–2 | gering |
| 3–4 | mittel |
| 6–9 | hoch |

Schutzziele: **V** = Vertraulichkeit, **I** = Integrität, **Vf** = Verfügbarkeit,
**B** = Betroffenenrechte/Intervenierbarkeit, **T** = Transparenz, **DM** =
Datenminimierung.

### 4.2 Risikoregister

> **W/S = Restwerte nach Berücksichtigung der bereits umgesetzten Maßnahmen** (Kap. 6).

| ID | Risiko / Gefährdung | Schutzziel | W | S | Wert | Stufe |
|---|---|---|---|---|---|---|
| **R-01** | **PII in INFO/WARN-Logs** (Name, Mail, Kundennr., userId) fließen **laufend** nach Graylog + lokales Logfile (400 Tage Retention, Doppelablage). Zugriff zwar auf denselben **4-Personen-RZ-Kreis** (auditiert) beschränkt → Vertraulichkeitsfläche reduziert; **Datenminimierungs-Verstoß** (Art. 5 Abs. 1 lit. c) bleibt jedoch bestehen | DM, V, T | 3 | 2 | **6** | hoch |
| **R-02** | **Kompromittierung des `./data`-Volumes** (enthält `secret.bin` **und** `config.yaml`) → Entschlüsselung aller At-rest-Daten | V | 1 | 3 | **3** | mittel |
| **R-03** | **Unbefugter Admin-Zugriff** auf entschlüsselte Art.-9-Daten. Kompromittiertes Konto durch **phishing-resistente, biometrische FIDO2-MFA (YubiKey 5C BIO)** praktisch ausgeschlossen; Rest = Insider-Missbrauch durch **4 namentlich benannte RZ-Leitungspersonen** (RBAC: nur AUDITADMIN, 180-s-Vault-TTL, lückenloser Lese-Audit-Trail) | V, B | 1 | 3 | **3** | mittel |
| **R-04** | **Datenleck Art.-9-Daten in transit** zum eID-Dienst / Storage-API | V, I | 1 | 3 | **3** | mittel |
| **R-05** | **LDAP-MITM** mangels Zertifikatsprüfung (`TrustAllTrustManager`) → Bind-Passwort/Personendaten exponiert | V, I | 1 | 3 | **3** | mittel |
| **R-06** | **Offene Test-Endpunkte** `/system/test/**` (unauth., GET) → Angriffsfläche/Info-Preisgabe | V, Vf | 2 | 1 | **2** | gering |
| **R-07** | **Fehlende/verzögerte Löschung** → Aufbewahrung über Erforderlichkeit hinaus | DM, B | 1 | 2 | **2** | gering |
| **R-08** | **Unmöglichkeit der Rechtsausübung** (Auskunft/Löschung/Berichtigung) | B | 1 | 2 | **2** | gering |
| **R-09** | **Fehlender Art.-9-Erlaubnistatbestand / fehlende Transparenz** → Verarbeitung ohne tragfähige Rechtsgrundlage | T, Rechtmäßigkeit | 2 | 3 | **6** | hoch |
| **R-10** | **Schema-Drift / Datenverlust** durch `ddl-auto: update` in Produktion | I, Vf | 1 | 2 | **2** | gering |
| **R-11** | **Verfügbarkeitsverlust / Datenverlust.** **Umgesetzt:** **ZFS**-basierte Datenhaltung (End-to-End-Prüfsummen, Snapshots) mit **`zfs send/receive`-Replikation** auf einen **Air-Gap-Server im Schatten-RZ** (>1 km, eigenes Dark-Fiber-Netz) inkl. Snapshot-Historie + valider Offsite-Kopie (Retention 30 Tage) → Daten- und Ransomware-Resilienz. **Restore-Verfahren (RTO/RPO) ist dokumentiert und getestet** | Vf, B | 1 | 2 | **2** | gering |
| **R-12** | **Auftragsverarbeitung ohne AV-Vertrag** (eID-Dienst, LDAP-/Hosting-Betrieb) | Rechtmäßigkeit | 2 | 2 | **4** | mittel |
| **R-13** | **Fehlende Schlüsselrotation / kein externes KMS/HSM**; `secret.bin` statisch | V | 1 | 3 | **3** | mittel |
| **R-14** | **Header-Härtung** (HSTS/nosniff/frame-ancestors) offen → Clickjacking/Downgrade | I, V | 1 | 2 | **2** | gering |

### 4.3 Risiko-Matrix (Restrisiko)

```
 S=3 |  R-02,R-04,    R-09
     |  R-05,R-13,R-03
 S=2 |  R-07,R-08,    R-12            R-01
     |  R-10,R-14,R-11
 S=1 |               R-06
     +-----------------------------------------
          W=1            W=2            W=3
```

---

## 5. Bewertung der höchsten Risiken (Detail)

- **R-01 (hoch) — PII in Logs:** Klarnamen/E-Mail/Kundennummer in INFO/WARN, dauerhaft
  in Graylog und im lokalen Logfile (400 Tage, Doppelablage). Der **Lese-Zugriff ist
  auf denselben auditierten 4-Personen-RZ-Kreis beschränkt** (kein breiter
  Betriebs-/Monitoring-Personenkreis) — die **Vertraulichkeitsfläche ist damit
  geringer als zunächst angenommen**. Maßgeblich für die Einstufung bleibt jedoch der
  **Verstoß gegen die Datenminimierung** (Art. 5 Abs. 1 lit. c): PII gehört nicht in
  INFO/WARN, unabhängig vom Leserkreis; W bleibt **3** (laufendes, sicheres Eintreten),
  S bleibt **2** (persistente PII über 400 Tage in zwei Speichern). Die Behebung ist
  zudem **kostengünstig** (Pseudonymisierung/Redaktion) → bleibt **P0**. → Maßnahmen M-01.
- **R-03 (mittel) — Insider-/Kontorisiko bei Admin-Zugriff:** Der Vektor
  *kompromittiertes Konto* ist durch **phishing-resistente, biometrische
  FIDO2-MFA** (administrativer Host-Zugang ausschließlich per SSH mit
  `id_ed25519_sk` + **YubiKey 5C BIO**/Fingerprint; identische 4-Personen-Gruppe
  auch für lokalen Log- und Graylog-Zugriff) praktisch ausgeschlossen → niedrige
  Eintrittswahrscheinlichkeit (**W=1**). Verbleibend ist allein der **Insider-Missbrauch
  durch Berechtigte**: Einsicht in Art.-9-Klardaten ist **der Rolle AUDITADMIN
  vorbehalten — Standard-Administratoren (ADMIN) haben keinen Zugriff auf die
  Personen-Audit-Daten**; zusätzlich RBAC, **Vault-Entsiegelung per YubiKey 5C BIO**
  (WebAuthn/PRF, biometrisch) mit **automatischer Versiegelung nach 180 s** sowie der
  vollständige Lese-Audit-Trail (`AUDIT_READ`) als Detektion/Abschreckung. Da die
  Schadensschwere bei Art.-9-Klardaten hoch bleibt (**S=3**), ergibt sich
  **Restrisiko mittel (W×S = 3)**; Missbrauch durch Berechtigte bleibt prinzipbedingt
  möglich. → Maßnahmen M-03.
- **R-09 (hoch) — Art.-9-Erlaubnis/Transparenz:** Ohne dokumentierten
  Erlaubnistatbestand (Art. 9 Abs. 2) und Betroffeneninformation ist die
  Rechtmäßigkeit der gesamten biometrischen Verarbeitung gefährdet. → Maßnahmen M-09.

---

## 6. Bereits umgesetzte Maßnahmen (Bestand, Art. 35 Abs. 7 lit. d)

> Diese Maßnahmen sind im Code/Deployment **bestätigt** und in den W/S-Werten oben
> bereits berücksichtigt.

| Bereich | Umgesetzte Maßnahme |
|---|---|
| Verschlüsselung at-rest (Dateien) | **AES-256-GCM** (`CryptoHandler`), Schlüssel `data/secret.bin` (Rechte `0400`) |
| Verschlüsselung at-rest (Felder) | Feldverschlüsselung `secret` via crypto-jpa (`@Convert`) |
| Schlüssel-/Vault-Konzept | In-Memory-Masterkey mit **TTL (prod 180 s)**, Entsiegelung über **WebAuthn/PRF**-Admin-Keys (PRF = Pseudo-Random Function), freigegeben per **YubiKey 5C BIO** (biometrisch); Zugriff auf Klardaten nur bei entsiegeltem Vault, **automatische Versiegelung nach 180 s** |
| Authentifizierung | **OAuth2/OIDC mit PKCE** |
| Autorisierung | RBAC (ADMIN/AUDITADMIN/VAULTADMIN/TABADMIN/PUBLISHER), `@PreAuthorize` + Vault-Bedingung für Audit; **Standard-Admins (ADMIN) ohne Einsicht in Personen-Audit-Daten** (nur AUDITADMIN bei entsiegeltem Vault) |
| API-Schutz (Storage) | **Bearer-Token (konstante-Zeit-Vergleich) + HMAC-SHA256 inkl. Zeitstempel**, SHA-256-Integritätsprüfsummen |
| Speicherbegrenzung / Löschung | **Automatische Löschung nach P90D** (DB + Dateien + `SdbLastSeen`), idempotent; Audit `USER_DELETED` |
| Betroffenenrechte | **Manuelle Sofort-Erasure** pro Person; **Auskunfts-Export (ZIP)** der entschlüsselten Metadaten (ohne Publisher/Binärdaten) |
| Rechenschaft/Audit | **Lese-Audit-Trail** (`AUDIT_READ` → Graylog) für alle lesenden Admin-Zugriffe; Export/Löschung ebenfalls geloggt |
| Integritätsschutz | `immutable`-Flag (sperrt **Änderung**, Löschung bleibt möglich → Art. 17 erfüllbar) |
| Transportverschlüsselung (Web) | **TLS-Edge über Traefik** (ACME), HTTP→HTTPS-Redirect, interne Strecke im isolierten 10/8-Netz |
| Transportverschlüsselung (LDAP) | **LDAPS** (Port 636, `ssl: true`), Bind-DN/-Passwort `{AES256}` |
| Sekret-Management | **Alle Secrets `{AES256}`-verschlüsselt** in der Prod-Config |
| Netz-/Edge-Härtung | Host nur 80/443; `net108` outgoing-only; Methoden-Whitelist; Extension-Blocklist; Management-Port nicht publiziert; Swagger in Prod aus |
| Zugriffskontrolle (Host/Betrieb) | Docker-Host **nur per SSH**, beschränkt auf **vier benannte RZ-Personen**; SSH via **FIDO2-Hardware-Schlüssel** (`id_ed25519_sk` + YubiKey 5C BIO/Fingerprint) → Schutz des `./data`-Volumes auf Betriebsebene |
| Protokollierung/Monitoring | Zentrales Logging **GELF → Graylog**; Log-Rotation (`max-history: 400`) |
| Backup / Datenresilienz | Datenhaltung auf **ZFS** (End-to-End-Prüfsummen gegen Bit-Rot, Snapshots); **`zfs send/receive`-Replikation** auf einen **Air-Gap-Server im Schatten-RZ** (>1 km Entfernung, eigenes Netz über **96 Dark-Fiber-Glasfasern**) → Snapshot-**Historie** + valide **Offsite-Kopie** (Retention 30 Tage; Disaster- und Ransomware-Resilienz); **Restore-Verfahren dokumentiert und getestet** (RTO/RPO definiert) |

---

## 7. Geplante / empfohlene Maßnahmen zur Risikobeherrschung (Art. 35 Abs. 7 lit. d)

| ID | Maßnahme | Adressiert | Priorität | Restwirkung |
|---|---|---|---|---|
| **M-01** | PII aus INFO/WARN-Logs entfernen (pseudonyme Korrelations-IDs); **Retention-/Zugriffs-Policy** für Graylog-Streams **und** lokales Logfile; Doppelablage prüfen | R-01 | **P0** | senkt R-01 von hoch → gering |
| **M-06** | Test-Endpunkte `/system/test/**` in Prod deaktivieren (Profil-Gate) oder an Traefik sperren | R-06 | **P0** | schließt R-06 |
| **M-09** | Art.-9-Erlaubnistatbestand (Art. 9 Abs. 2) **dokumentieren**; Betroffeneninformation (Art. 12–14) sicherstellen | R-09 | **P1** | senkt R-09 von hoch → gering |
| **M-05** | `TrustAllTrustManager` durch echten Truststore (CA-Kette) **mit Hostname-Verifikation** ersetzen | R-05 | **P1** | schließt R-05 |
| **M-03** | Admin-Zugriff regelmäßig auf Erforderlichkeit prüfen; ggf. Mehr-Personen-Entsiegelung (Threshold); Audit-Auswertung etablieren | R-03 | **P1** | senkt Insider-Restrisiko (Detektion/Governance), R-03 bereits **mittel** |
| **M-14** | Security-Header an Traefik: **HSTS, nosniff, frame-ancestors/X-Frame-Options** | R-14 | **P1** | schließt R-14 |
| **M-10** | `ddl-auto: validate` + verwaltete Migrationen (Flyway/Liquibase) | R-10 | **P1** | schließt R-10 |
| **M-02** | `./data`-Volume-Zugriff strikt beschränken (Host-Zugang bereits **SSH-only**, **4 RZ-Personen**, **FIDO2/YubiKey 5C BIO**); `secret.bin` und `config.yaml` getrennt absichern | R-02, R-13 | **P1** | senkt R-02 |
| **M-12** | **AV-Verträge** (Art. 28) mit eID-Dienst, LDAP-/Hosting-Betrieb schließen/dokumentieren | R-12 | **P1** | schließt R-12 |
| **M-04** | TLS-Härtung der Storage-/eID-Strecke verifizieren (Zertifikate, Pinning falls möglich) | R-04 | **P2** | senkt R-04 |
| **M-13** | Externes Key-Management/HSM, **`secret.bin`-Rotation** | R-13 | **P2** | senkt R-13 |
| **M-15** | Datenminimierung im eID-Statusobjekt prüfen (Reduktion/kürzere Frist für Video/Audio/MRZ) | R-01, Verhältnism. | **P2** | senkt Eingriffstiefe |
| **M-16** | Rate-Limiting/WAF für `/api/v1/**` | R-06, R-04 | **P2** | reduziert Angriffsfläche |

---

## 8. Maßnahmenplan (priorisiert, Zusammenfassung)

**P0 — vorrangig:** M-01 (Log-PII + Retention/Zugriff), M-06 (Test-Endpunkte).
**P1 — kurzfristig:** M-09 (Art.-9-Erlaubnis + Transparenz), M-05 (LDAP-Cert),
M-03 (Admin-Zugriffsgovernance), M-14 (Header), M-10 (`ddl-auto`/Migrationen),
M-02 (`./data`-Schutz), M-12 (AV-Verträge).
**P2 — mittelfristig:** M-04, M-13 (KMS/Rotation), M-15 (Datenminimierung eID),
M-16 (Rate-Limiting/WAF).

---

## 9. Restrisiko und Ergebnis (Art. 35 Abs. 7)

| Zeitpunkt | hohe Risiken | mittlere Risiken | geringe Risiken |
|---|---|---|---|
| **Vor Maßnahmen (Ausgangslage)** | R-01, R-09 | R-02, R-03, R-04, R-05, R-12, R-13 | R-06, R-07, R-08, R-10, R-11, R-14 |
| **Nach Umsetzung P0/P1** | — | R-03 (Insider, prinzipbedingt), R-04, R-13 | übrige |

**Bewertung des Restrisikos:** Nach Umsetzung von P0/P1 verbleiben **keine hohen
Risiken**. Das verbleibende Insider-Risiko (R-03) ist prinzipbedingt (berechtigter
Zugriff auf Klardaten) und wird durch RBAC, Vault-Entsiegelung und lückenlosen
Lese-Audit-Trail auf ein vertretbares Maß reduziert.

**Konsultationspflicht (Art. 36):** Da nach Maßnahmenumsetzung **kein hohes
Restrisiko** verbleibt, ist eine vorherige Konsultation der Aufsichtsbehörde nach
Art. 36 voraussichtlich **nicht erforderlich**. **Entscheidung obliegt dem DSB.**

**Gesamtergebnis (Vorschlag):** Die Verarbeitung kann bei Umsetzung des
Maßnahmenplans **datenschutzkonform fortgeführt werden**. Die DSFA ist bei
wesentlichen Änderungen (neue Datenarten, neue Empfänger, geänderte Fristen) sowie
**spätestens alle 24 Monate** zu überprüfen.

---

## 10. Konsultation des Datenschutzbeauftragten (Art. 35 Abs. 2)

| Punkt | Eintrag |
|---|---|
| Rat des DSB eingeholt | ☐ ja ☐ nein — Datum: __________ |
| Stellungnahme des DSB | _______________________________________________ |
| Abweichungen vom DSB-Rat (mit Begründung) | _______________________________________________ |
| Standpunkte betroffener Personen eingeholt (Art. 35 Abs. 9) | ☐ ja ☐ nein ☐ nicht angemessen — Begründung: __________ |

---

## 11. Freigabe / Verantwortlichkeiten

| Rolle (Art. 4 Nr. 7 / Art. 37 ff.) | Name / Stelle | Datum | Unterschrift |
|---|---|---|---|
| Verantwortlicher | _________________ | ________ | ____________ |
| Datenschutzbeauftragter | _________________ | ________ | ____________ |
| Compliance-Officer | _________________ | ________ | ____________ |
| Technischer Betrieb / Admin | _________________ | ________ | ____________ |
| Fachverantwortlicher (Identifizierungs­prozess) | _________________ | ________ | ____________ |

| DSFA-Status | ☐ Entwurf ☐ in Abstimmung ☐ freigegeben |
|---|---|
| Nächste Überprüfung spätestens | __________ (Vorschlag: 2028-06-26 oder bei wesentlicher Änderung) |

---

> **Verweis:** Technische Detailbefunde und Maßnahmen-Fortschritt siehe
> **ACCOUNT_COMPLIANCE.md**; Verarbeitungs-/Dateninventar siehe
> **ACCOUNT_PROZESS_BESCHREIBUNG.md**.
</content>
</invoke>
