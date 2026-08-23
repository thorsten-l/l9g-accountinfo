# Gefundene Defekte — l9g-accountinfo

Befundliste aus dem Aufbau der Unit-Test-Suite (Stand 2026-08-23, Version 2.5.0,
Branch `one-key-to-bind-them`). Sortiert nach Risiko, absteigend.

Jeder Befund ist entweder durch einen Test **gepinnt** (der Test schreibt das
heutige Verhalten fest und schlägt nach einem Fix bewusst fehl) oder als
**ungetestet** markiert. Nach jedem Fix in `src/main` muss der zugehörige Test
umgedreht werden — die Spalte „Nach dem Fix" sagt, wie.

**Legende Risiko:** `KRITISCH` = extern ausnutzbar oder Datenverlust ·
`HOCH` = Sicherheits-/Betriebsausfall · `MITTEL` = Fehlverhalten mit Workaround ·
`NIEDRIG` = Datenqualität, Doku, Ergonomie · `—` = kein Defekt, dokumentiertes
Soll-Verhalten

---

## Übersicht

| # | Risiko | Ort | Kurzfassung | Status |
|---|---|---|---|---|
| ~~1~~ | ~~KRITISCH~~ | `StorageController` | ~~Kein Replay-/Skew-Fenster für `X-Timestamp`~~ | **✅ BEHOBEN 2026-08-23** |
| ~~2~~ | ~~KRITISCH~~ | `BackchannelLogoutController` | ~~Logout-Token wird nicht verifiziert~~ | **✅ BEHOBEN 2026-08-23** |
| ~~3~~ | ~~HOCH~~ | `JwtService` | ~~`kid` wird ignoriert, immer erster RS256-Key~~ | **✅ BEHOBEN 2026-08-23** |
| 4a | ~~HOCH~~ | `JwtService` | ~~Keine Claim-Prüfung~~ | **✅ BEHOBEN 2026-08-23** |
| 4b | — | `AuthService.java:114` | Keine Claim-Prüfung in `verifyJwt` | **so gewollt (TL)**, gepinnt |
| ~~5~~ | ~~HOCH~~ | `SignaturePadWebSocketHandler` | ~~`ConcurrentModificationException` killt Heartbeat~~ | **✅ BEHOBEN 2026-08-23** |
| ~~6~~ | ~~MITTEL~~ | `VaultService` | ~~Halb-entsperrter Vault bei TTL ≤ 0~~ | **✅ BEHOBEN 2026-08-23** |
| ~~7~~ | ~~MITTEL~~ | `LdapService` | ~~LDAP-Filter-Sanitizer unvollständig~~ | **✅ BEHOBEN 2026-08-23** |
| ~~20~~ | ~~HOCH~~ | `LdapService` | ~~Kartennummer und Admin-Suche gänzlich unsanitisiert~~ | **✅ BEHOBEN 2026-08-23** |
| 21 | — | `LdapService.java:424` | Leere Suchanfrage listet alle Personen | **so gewollt (TL)**, gepinnt |
| 22 | — | `LdapService.java:431` | Ein-Wort-Suche durchsucht nur den Nachnamen | **so gewollt (TL)**, gepinnt |
| ~~8~~ | ~~MITTEL~~ | `AuthService` | ~~Backend-Ausfall wird als 404 maskiert~~ | **✅ BEHOBEN 2026-08-23** |
| ~~9~~ | ~~MITTEL~~ | `SessionStoreService` | ~~Verwaister Cache-Eintrag bei sid-Wiederverwendung~~ | **✅ BEHOBEN 2026-08-23** |
| ~~10~~ | ~~MITTEL~~ | `StorageController` | ~~Fehlendes `user` ergibt 500 statt 400~~ | **✅ BEHOBEN 2026-08-23** |
| 11 | — | `SdbSecretData.java:171` | `size` zählt Zeichen, Checksumme deckt Bytes | **so gewollt (TL)**, gepinnt |
| ~~12~~ | ~~NIEDRIG~~ | `SdbSecretType` | ~~Tippfehler-Alias `est-id-status`~~ | **✅ BEHOBEN durch TL** |
| ~~13~~ | ~~NIEDRIG~~ | `StorageObject` | ~~Literal-Name `"null null"`~~ | **✅ BEHOBEN 2026-08-23** |
| ~~14~~ | ~~NIEDRIG~~ | `JwtService` | ~~Nicht-String-Claims in `Map<String,String>`~~ | **✅ BEHOBEN 2026-08-23** |
| ~~15~~ | ~~NIEDRIG~~ | `StorageObject` | ~~`equals` vergleicht `data` per Referenz~~ | **✅ BEHOBEN 2026-08-23** |
| ~~16~~ | ~~NIEDRIG~~ | `VaultService` | ~~`adminId` wird als eigener `createdBy` geführt~~ | **✅ BEHOBEN 2026-08-23** |
| ~~17~~ | ~~NIEDRIG~~ | `SessionStoreService` | ~~Javadoc widerspricht Implementierung~~ | **✅ BEHOBEN 2026-08-23** |
| ~~18~~ | ~~NIEDRIG~~ | `AuthService` | ~~Fehlendes `publicJwk` → NPE statt 400~~ | **✅ BEHOBEN 2026-08-23** |
| ~~19~~ | ~~NIEDRIG~~ | `pom.xml` | ~~Lokaler Pfad im Release-Jar~~ | **✅ BEHOBEN 2026-08-23** |

---

## 1 — ✅ BEHOBEN (2026-08-23) · Kein Replay-Schutz auf dem Storage-Endpunkt

**Umsetzung** (alles in `StorageController`, keine neue Klasse):
- `checkTimestampFreshness` — `X-Timestamp` wird jetzt geparst und muss
  innerhalb von `app.storage.api.timestamp-tolerance` (Default **5m**) um die
  aktuelle Zeit liegen. Nicht-numerische Werte → 401.
- `checkNotReplayed` — jede erfolgreich verifizierte Signatur wird in einem
  Caffeine-Cache vermerkt (Lebensdauer `2 × tolerance + 1m`, max. 100.000
  Einträge) und beim zweiten Gebrauch mit 401 abgelehnt. Abschaltbar über
  `app.storage.api.replay-protection` (Default `true`).
- Reihenfolge in `verifySignature`: Header-Präsenz → **Frische** → HMAC
  (konstante Zeit) → **Replay**. Die Frischeprüfung vorne spart bei alten
  Requests die MAC-Berechnung; die Replay-Prüfung hinten stellt sicher, dass nur
  tatsächlich verifizierte Signaturen in den Cache gelangen — sonst könnte ein
  Angreifer den Cache mit geratenen Signaturen vergiften.

**Zeitstempel-Format:** Im Repo gibt es keine Sender-Spezifikation; das Javadoc
sagte „Unix epoch seconds", die Betriebsdoku nur „mitsignierter Zeitstempel".
Damit der Fix keine laufende Integration bricht, akzeptiert
`toEpochMillis` **beide** Einheiten — Werte unter 100.000.000.000 gelten als
Sekunden, größere als Millisekunden (eindeutig bis zum Jahr 5138). Wenn das
Sender-Format bekannt ist, kann diese Heuristik gefahrlos verengt werden.

**Tests:** `StorageControllerTest` (23, davon neu) — `replayedSignatureIsRejected`
· `replayWithDifferentEncodingIsRejected` · `ancientTimestampIsRejected`
· `timestampOutsideToleranceIsRejected` · `timestampInsideToleranceIsAccepted`
· `nonNumericTimestampIsRejected` · `epochMillisecondsAreAccepted`
· `replayProtectionCanBeDisabled`

**Restrisiko:** Der Replay-Cache ist **pro Instanz**. Hinter einem Load Balancer
mit mehreren Replicas kann ein Replay auf einer anderen Instanz innerhalb des
Toleranzfensters noch durchgehen — dort bleibt das Fenster die harte Grenze. Der
aktuelle Docker-Compose-Betrieb ist eininstanzig, daher praktisch nicht
betroffen. Ein geteilter Cache (Redis o. ä.) wäre die Lösung, falls skaliert wird.

**Wichtig für den Betrieb:** Sendet das Gegenüber Zeitstempel mit stark
abweichender Uhr, kommen jetzt 401er. Die Toleranz lässt sich über
`app.storage.api.timestamp-tolerance` erhöhen; als Notausgang schaltet
`app.storage.api.replay-protection: false` nur die Einmal-Nutzung ab, das
Zeitfenster bleibt aktiv.

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/controller/api/StorageController.java:252`
(`verifySignature`)

`X-Timestamp` wird für die HMAC-Berechnung verwendet, aber **nie geparst und
nie gegen die aktuelle Zeit geprüft**. Es gibt kein Toleranzfenster und keine
Nonce-Verfolgung. Ein einmal abgefangenes `X-Timestamp`/`X-Signature`-Paar ist
damit unbegrenzt wiederverwendbar; ein Timestamp von 1970 wird genauso
akzeptiert wie ein aktueller, und ein nicht-numerischer Wert ebenfalls.

Verschärfend: `/api/v1/storage/**` ist `permitAll`
(`ClientSecurityConfig.java:137`) **und** CSRF-exempt
(`ClientSecurityConfig.java:248`). Bearer-Token und HMAC sind die einzige
Absicherung — und das Javadoc der Methode suggeriert, der Timestamp binde die
Signatur zeitlich, was er nicht tut.

**Angriff:** Wer einen einzigen Request mitliest (Proxy-Log, Reverse-Proxy-Dump,
kompromittierter Client), kann Identifikations-Datensätze beliebig oft
erneut einspielen.

**Fix-Richtung:** Fenster von z. B. ±300 s gegen `System.currentTimeMillis()`
prüfen, nicht-parsbare Timestamps ablehnen. Für echten Replay-Schutz zusätzlich
gesehene Signaturen für die Fensterdauer cachen (Caffeine ist bereits Dependency).

**Gepinnt war das durch:** `StorageControllerTest.signatureCanBeReplayedIndefinitely`
und `nonNumericTimestampIsAccepted` — beide Tests sind jetzt umgedreht und durch
die oben genannten ersetzt.

</details>

---

## 2 — ✅ BEHOBEN (2026-08-23) · Logout-Token wurde nicht verifiziert

**Umsetzung:**
- Neu: `service/LogoutTokenVerifier.java` — prüft Signatur (über einen
  injizierten `JwtDecoder`), `aud` gegen die eigene Client-ID, den
  `events`-Claim auf `http://schemas.openid.net/event/backchannel-logout`,
  Abwesenheit von `nonce` und Vorhandensein von `sid`. Jeder Fehlschlag → 400,
  keine Session wird angefasst.
- Neu: `config/LogoutTokenConfig.java` — baut den `JwtDecoder` **lazy** aus der
  vorhandenen OAuth2-Client-Registration (`jwk-set-uri` + `issuer-uri` aus der
  OIDC-Discovery). `NimbusJwtDecoder` übernimmt JWKS-Caching, Key-Rotation und
  `kid`-Auswahl; `JwtTimestampValidator` und `JwtIssuerValidator` decken
  `exp`/`nbf`/`iss` ab. Kein Netzzugriff beim Context-Start.
- Geändert: `BackchannelLogoutController` — verifiziert vor dem Invalidieren,
  akzeptiert das spec-konforme `logout_token`-Formularfeld **und** einen rohen
  Body, und loggt das Token nicht mehr im Klartext.

**Tests:** `LogoutTokenVerifierTest` (12) · `BackchannelLogoutControllerTest` (9)
· `LogoutTokenConfigTest` (6)

**Wichtig für den Betrieb:** Der Fix ist **fail-fast** — fehlt in der
OAuth2-Registration eine `jwk-set-uri` oder passt `app.oauth2.registration-id`
nicht, startet die Anwendung nicht mehr. Bei Konfiguration über `issuer-uri`
(aktueller Stand) liefert die OIDC-Discovery beides automatisch.

**Nicht abgedeckt:** `jti`-basierte Replay-Erkennung. Ein abgefangenes
Logout-Token bleibt bis zu seinem `exp` verwendbar, kann aber nur die eine
Session beenden, für die es legitim ausgestellt wurde.

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/controller/BackchannelLogoutController.java:76-78`

```java
Map<String, String> jwt = jwtService.decodeJwtPayload(logoutToken);
sessionStore.invalidateByOAuth2Sid(jwt.get("sid"));
```

`decodeJwtPayload` **dekodiert nur** (Base64 + JSON), es verifiziert keine
Signatur — `validateJwtSignature` wird hier nicht aufgerufen. Der Endpunkt ist
`permitAll` (`ClientSecurityConfig.java:140`) und CSRF-exempt
(`ClientSecurityConfig.java:247`).

**Angriff:** Ein unauthentifizierter Aufrufer kann ein selbst gebautes,
unsigniertes JWT posten und damit die Session eines bekannten `sid` beenden.
Bei bekannten oder erratbaren `sid`-Werten ist das gezieltes Aussperren; per
Brute-Force ein Denial-of-Service gegen alle angemeldeten Nutzer.

**Fix-Richtung:** Vor `invalidateByOAuth2Sid` die Signatur gegen das JWKS des
IdP prüfen (`jwtService.validateJwtSignature`), zusätzlich `iss`, `aud` und
`events`-Claim gemäß OIDC-Backchannel-Logout-Spec validieren. Bei ungültigem
Token 400 zurückgeben.

**Nachweis vor dem Fix:** Ein temporärer Test hat die Lücke belegt — ein
selbst gebautes Token mit Signatur-Müll (`{"alg":"RS256"}` / `{"sid":"..."}` /
beliebige Bytes) beendete die referenzierte Session und wurde mit `200 OK`
quittiert. Verschärfend kam hinzu: `JwtService.validateJwtSignature` wird in
`src/main` **nirgends** aufgerufen und weder `setOauth2JwksCerts` noch
`setClientSecret` je befüllt — der RS256-Pfad war toter Code, ein bloßer
`validateJwtSignature`-Aufruf hätte hier immer `false` geliefert und das Logout
komplett lahmgelegt.

</details>

---

## 3 — ✅ BEHOBEN (2026-08-23) · `kid` wurde bei der JWKS-Auswahl ignoriert

**Umsetzung: der defekte Code wurde entfernt, nicht repariert.**

`validateJwtSignature` und seine Helfer (`validateRs256Signature`,
`validateHs512Signature`, `getPublicKeyFromJwks`) hatten **null Aufrufer**, und
die beiden Felder `oauth2JwksCerts` / `clientSecret` wurden nirgends in
`src/main` befüllt — der ganze Verifikationspfad war toter Code, der bei
tatsächlichem Gebrauch immer `false` geliefert hätte. Eine korrekte Reparatur
hätte JWKS-Abruf, Caching, Key-Rotation und Claim-Prüfung nachbauen müssen, also
genau das, was `NimbusJwtDecoder` schon mitbringt.

Entfernt wurden daher: `validateJwtSignature`, `validateRs256Signature`,
`validateHs512Signature`, `getPublicKeyFromJwks`, die Felder
`oauth2JwksCerts`/`clientSecret` samt Lombok-`@Getter`/`@Setter`. `JwtService`
schrumpft von 289 auf 109 Zeilen und enthält nur noch `splitJwt` und
`decodeJwtPayload`.

Das Klassen-Javadoc sagt jetzt ausdrücklich, dass der Service **nichts
verifiziert** und verweist für echte Verifikation auf `JwtDecoder` /
`LogoutTokenConfig`. Damit kann niemand die Methode versehentlich als
Sicherheitsprüfung verwenden.

**Tests:** `JwtServiceTest` von 16 auf 8 Tests reduziert — die Tests der
entfernten Methoden sind weggefallen, `rs256IgnoresKid` und
`malformedJwksEntriesYieldFalse` sind damit gegenstandslos.

**Hinweis:** Die DTOs `JwksCerts` und `JwtHeader` sind jetzt ungenutzt. Sie
tragen `@Schema`-Annotationen für springdoc und wurden deshalb belassen; sie
können entfallen, wenn sie in keiner API-Beschreibung mehr gebraucht werden.

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/service/JwtService.java:194-212`
(`getPublicKeyFromJwks`)

Die Methode iteriert das JWKS und gibt den **ersten** Key mit `alg == "RS256"`
zurück (`JwtService.java:200`). Der übergebene `kid` wird ausschließlich in der
Fehlermeldung verwendet (`JwtService.java:212`).

**Auswirkung:** Sobald das IdP mehr als einen RS256-Key veröffentlicht — der
Normalfall während einer Key-Rotation — wird ein völlig gültiges Token gegen das
falsche Zertifikat geprüft und abgelehnt. Das ist ein Ausfall zum ungünstigsten
Zeitpunkt und aus dem Log nicht als Ursache erkennbar.

Nebenbefunde in derselben Methode: `key.algorithm()` wirft NPE, wenn ein
JWKS-Eintrag `alg` weglässt (laut RFC 7517 zulässig); `x509CertificateChain()`
wirft NPE ohne `x5c` und `IndexOutOfBoundsException` bei leerem `x5c`. Alle drei
werden vom `catch(Throwable)` des Aufrufers zu `false` verschluckt.

**Fix-Richtung:** Nach `kid` filtern und erst dann auf `alg` prüfen; `alg`- und
`x5c`-Abwesenheit defensiv behandeln statt zu werfen.

**Gepinnt war das durch:** `JwtServiceTest.rs256IgnoresKid` (mit Kontrollfall:
nur eigener Key → `true`, zwei Keys → `false`) und
`malformedJwksEntriesYieldFalse`.

</details>

---

## 4a — ✅ BEHOBEN · 4b — ✅ SO GEWOLLT · Keine Claim-Validierung

### 4a `JwtService` — ✅ behoben (2026-08-23)

Gegenstandslos: der betroffene `validateJwtSignature`-Pfad wurde mit Defekt #3
entfernt. `JwtService` verifiziert jetzt per Design nichts mehr und behauptet das
auch nicht.

### 4b `AuthService.verifyJwt` — ✅ so gewollt (bestätigt TL)

Bei der Umsetzung stellte sich heraus, dass die in der ursprünglichen
Fix-Richtung vorgeschlagene Lösung (Nimbus `DefaultJWTClaimsVerifier`) **die
Audit-Funktion zerstören würde**. `verifyJwt` bedient zwei gegensätzliche Zwecke:

| Aufrufstelle | Zweck | Frischeprüfung |
|---|---|---|
| `ApiSignaturePadController.validate:332` | Pad-Enrolment, frisches Token (`permitAll`, CSRF-exempt) | wäre sinnvoll |
| `ApiSignaturePadController.signature:423` | Signatureingang, frisches Token (authentifiziert) | wäre sinnvoll |
| `ApiAdminController:168,214,287` | **Nachprüfung archivierter Signaturen aus der DB** | **darf nicht sein** |

Ein `exp`- oder Alterscheck in `verifyJwt` würde jede archivierte Signatur ab
einem bestimmten Alter als ungültig melden — genau das, was der Audit-Pfad
beweisen soll.

Erschwerend: `signaturePad.js:129` setzt im Pad-JWT **nur `iat`** — kein `exp`,
kein `iss`, kein `aud`. Diese Claims stehen also gar nicht zur Verfügung.

**Nach Prüfung als gewollt bestätigt:** Die Pads setzen **absichtlich keinen
`exp`-Claim**, weil eine abgegebene Signatur **nie ablaufen** soll. Genau
dieselbe Methode prüft archivierte Signaturen aus der Datenbank nach, teils Jahre
später — jede Ablauf- oder Altersprüfung würde den Audit-Trail unverifizierbar
machen und damit das Gegenteil dessen bewirken, wofür er existiert.

Die ursprüngliche Fix-Richtung (Nimbus `DefaultJWTClaimsVerifier`) war also
nicht nur riskant, sondern fachlich falsch. Ebenso der zwischenzeitlich
erwogene Weg über ein zusätzliches `verifyFreshJwt` mit `iat`-Höchstalter: für
den Archivpfad hätte er nichts gebracht, und für den Eingangspfad hätte er
Tokens abgelehnt, die per Definition zeitlos gültig sind.

Umgesetzt wurde daher **nur die Härtung** (siehe #18): fehlendes oder
nicht-RSA-`publicJwk` ergibt jetzt einen sauberen 400 statt NPE bzw.
ClassCastException. Über **20 Pads sind im Feld**, deren Signaturen sich nicht
ändern lassen — der Regressionsschutz dafür ist
`AuthServiceTest.existingRsaPadSignatureStillVerifies`.

**Gepinnt durch:** `AuthServiceTest.existingRsaPadSignatureStillVerifies`
(Pad-JWT in der real erzeugten Form: nur `iat`, kein `exp`) und
`expiredJwtIsAccepted` (belegt, dass auch ein Token *mit* abgelaufenem `exp`
akzeptiert wird — es wird eben kein Claim ausgewertet).

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/service/JwtService.java:146`
(`validateJwtSignature`) und
`src/main/java/l9g/account/info/controller/api/AuthService.java:113`
(`verifyJwt`)

In beiden Methoden wird ausschließlich die kryptografische Signatur geprüft.
Es gibt **keinerlei** Prüfung von `exp`, `nbf`, `iat`, `iss` oder `aud`. Ein vor
Jahren abgelaufenes Token mit intakter Signatur gilt als gültig.

Bei `AuthService.verifyJwt` wiegt das schwerer, weil der Signatur-Pad-Schlüssel
nur bei einem expliziten `createPrivateJWK()`-Aufruf rotiert: ein abgefangenes
Pad-Token bleibt praktisch unbegrenzt gültig.

**Fix-Richtung:** In `JwtService` nach der Signaturprüfung `exp`/`nbf` gegen die
Systemzeit prüfen (mit kleiner Skew-Toleranz) und `iss`/`aud` gegen die
Konfiguration. In `AuthService.verifyJwt` einen Nimbus
`DefaultJWTClaimsVerifier` einsetzen.

**Gepinnt war/ist das durch:** `JwtServiceTest.expiredTokenIsStillAccepted`
(entfallen mit #3) und `AuthServiceTest.expiredJwtIsAccepted` (weiterhin aktiv,
pinnt 4b).

</details>

---

## 5 — ✅ BEHOBEN (2026-08-23) · `ConcurrentModificationException` beendet den Heartbeat dauerhaft

**Umsetzung** in `SignaturePadWebSocketHandler`:
- `sessionsBySessionId` ist jetzt eine `ConcurrentHashMap` statt einer
  `HashMap`. Die Map wird aus WebSocket-Threads beschrieben, während der
  Heartbeat-Scheduler und `AdminController.getSignaturePadSessions` sie aus
  anderen Threads lesen — sie war nie thread-safe.
- Das Aufräumen geschlossener Sessions läuft über
  `sessionsBySessionId.values().removeIf(...)` statt `forEach` + `remove`.
- `fireEventToAllSessions` serialisiert das JSON einmal statt pro Empfänger.
  Die Semantik ist identisch: die Serialisierung lag dort auch vorher außerhalb
  jedes `catch` und propagiert weiter als `IOException`.
- `fireEventToPad` bekommt einen `padUuid == null`-Guard (vorher NPE) und eine
  `session != null`-Prüfung.

**Kompatibilität — bewusst geprüft:**

| Aspekt | Bewertung |
|---|---|
| Datenbank | nicht berührt, der Handler hält ausschließlich In-Memory-Sessions |
| WebSocket-Nutzlast | **bitgleich** — dasselbe `objectMapper.writeValueAsString` auf demselben `DtoEvent` |
| Öffentliche API | `getSessionsBySessionId()` liefert weiter `Map<String, WebSocketSession>`; `AdminController:148` unverändert |
| `fireEventToPad(…, null)` | vorher NPE → HTTP 500, jetzt No-Op. Kein Aufrufer kann `null` liefern: beide Aufrufstellen lesen ein *required* `@RequestParam("uuid")`, Spring lehnt fehlende Werte schon mit 400 ab |
| Fehlerbehandlung `fireEventToPad` | unverändert — die Serialisierung bleibt absichtlich **innerhalb** des `try`, damit ein Fehler wie bisher geschluckt wird und ein Pad nie die Zustellung an die anderen bricht |

**Bewusst nicht geändert:** die Serialisierung in `fireEventToPad` wurde
zunächst aus der Schleife gezogen und dann zurückgenommen — dort hätte ein
Serialisierungsfehler statt geschluckt zu werden propagiert. Das wäre eine
Verhaltensänderung an einem Pfad im Produktivbetrieb gewesen.

**Tests:** `SignaturePadWebSocketHandlerTest` (17) — neu:
`closedSessionIsReapedAndOthersStillReceive` ·
`broadcastKeepsWorkingAfterDisconnect` · `exposedMapIsSafeToIterate` ·
`nullPadUuidIsIgnored` · **`wireFormatIsStable`** · **`eventIdentifiersAreStable`**

Die beiden letzten sind Kompatibilitätswächter für die über 20 Pads im Feld: sie
pinnen die exakte JSON-Gestalt (`event`/`timestamp`, plus `message` wenn gesetzt)
und alle Event-Kennungen — inklusive des historischen Tippfehlers
`EVENT_UNKNOWN = "unkown"`, der Teil des Wire-Contracts ist und **nicht**
korrigiert werden darf, solange Pads darauf matchen.

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/ws/SignaturePadWebSocketHandler.java:181-188`

```java
sessionsBySessionId.forEach((id, session) ->
{
  if(session == null || ! session.isOpen())
  {
    sessionsBySessionId.remove(id);   // <-- während HashMap.forEach
  }
});
```

`sessionsBySessionId` ist eine `HashMap` (`Zeile 53`); Entfernen während
`forEach` löst den Fail-Fast-Mechanismus aus.

**Auswirkung:** Der Defekt ist latent, solange alle Sessions offen sind. Sobald
ein Signatur-Pad die Verbindung trennt, wirft `fireEventToAllSessions` — und das
ist genau der Aufruf des `HeartbeatScheduler` alle 15 s
(`scheduler.heartbeat.rate`). Ab diesem Moment erreicht **kein Pad mehr einen
Heartbeat**, bis der Prozess neu startet. Da `@Async` die Exception nur
wegloggt, fällt es im Betrieb erst durch tote Pads auf.

Zusätzlich ist die Map nicht thread-sicher, wird aber aus dem Scheduler-Thread
und aus WebSocket-Threads beschrieben.

**Fix-Richtung:** `ConcurrentHashMap` verwenden und
`sessionsBySessionId.values().removeIf(s -> s == null || !s.isOpen())` statt
`forEach` + `remove`.

**Gepinnt war das durch:**
`SignaturePadWebSocketHandlerTest.broadcastThrowsWhenAClosedSessionMustBeReaped`
und `nullPadUuidThrows` — beide sind jetzt umgedreht.

</details>

---

## 6 — ✅ BEHOBEN (2026-08-23) · Halb-entsperrter Vault bei deaktiviertem TTL

**Umsetzung** in `VaultService.setUnlockedKey`: `AES256` wird **vor** jeder
Feldzuweisung konstruiert. Entweder ist der Vault danach vollständig entsperrt,
oder es hat sich gar nichts geändert.

```java
AES256 cipher = new AES256(masterKey.getEncoded());   // wirft bei falscher Länge

this.masterKey = masterKey;
this.aes256 = cipher;
this.masterKeyTimestamp = System.currentTimeMillis();
```

**Kompatibilität:** Die Änderung betrifft ausschließlich den Fehlerpfad
(Schlüssel ≠ 32 Byte). Ein korrekter 32-Byte-Schlüssel verhält sich unverändert.
Einziger Aufrufer ist `VaultApiController:156` (Unseal). Keine DB, kein
Protokoll, keine Konfiguration berührt. In der Produktivkonfiguration
(`app.vault.masterkey-ttl: 120000`) war der dauerhafte Fehlzustand ohnehin nicht
erreichbar — der Fix schließt ihn auch für `masterkeyTTL <= 0`.

**Tests:** `VaultServiceTest.wrongKeyLengthLeavesVaultSealed` (prüft beide
TTL-Varianten) · `failedUnsealDoesNotDisturbOpenVault` (ein fehlgeschlagener
Versuch versiegelt einen bereits offenen Vault nicht)

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/vault/VaultService.java:110-111`

```java
this.masterKey = masterKey;                        // wird zuerst gesetzt
this.aes256 = new AES256(masterKey.getEncoded());  // kann werfen
```

`AES256` verlangt exakt 32 Byte. Bei falscher Länge wirft der Konstruktor —
`masterKey` ist dann aber schon gesetzt, `aes256` bleibt `null`.

Bei aktivem TTL maskiert die Ablaufprüfung in `getUnlockedKey()` das (weil
`masterKeyTimestamp` nie fortgeschrieben wurde, wird der Key beim nächsten Lesen
verworfen). Bei `app.vault.masterkey-ttl <= 0` ist der Zustand **permanent**:
`getUnlockedKey()` liefert einen Key — was `ClientSecurityConfig.java:185,193`
und `ApiAdminController.deletePerson` als „Vault ist offen" lesen — während
jede Krypto-Operation mit `VaultSealedException` fehlschlägt.

**Auswirkung:** Autorisierungsentscheidungen fallen positiv aus, die
nachfolgende Operation scheitert. Nutzer sehen 500er statt einer klaren
„Vault versiegelt"-Meldung.

**Fix-Richtung:** `AES256` zuerst konstruieren, danach beide Felder gemeinsam
setzen. Alternativ im `catch` beide Felder zurücksetzen.

**Gepinnt war das durch:**
`VaultServiceTest.wrongKeyLengthLeavesHalfUnlockedStateWhenExpiryDisabled`.

</details>

---

## 7 + 20 — ✅ BEHOBEN (2026-08-23) · LDAP-Filter-Injection

**Bei der Umsetzung korrigierte Bewertung.** Der ursprüngliche Befund #7 war zu
weit gefasst und übersah gleichzeitig den gravierenderen Fall:

*Zu weit gefasst:* Das Filter-Template ist
`(&(givenName=%s*)(sn=%s*)(objectClass=soniaPerson)(!(soniaIsUnregistered=*)))`
— der Wert landet immer in der **Value-Position**. Dort sind `<`, `>`, `!`, `~`
und `,` nach RFC 4515 literal und können eine Assertion nicht verlassen; `<=`
und `~=` brauchen das Zeichen in der *Attribut*-Position. Die ursprünglich
gemeldete Lücke im Sanitizer von `ApiSearchController` war also **nicht
ausnutzbar**, weil `(`, `)`, `*` und `\` dort schon entfernt wurden.

*Übersehen (neu als #20):* Zwei Stellen erreichen den Filter **ohne jede**
Bereinigung:
1. `LdapService.findUserEntryByCustomerNumber:236` — `cardNumber` wird 4× in
   `(&(|(soniaChipcardBarcode=%s)(soniaCustomerNumber=%s)(soniaCustomerNumber=00%s))…)`
   interpoliert. Erreichbar über `GET /api/v1/userinfo?card=…` aus einer
   Pad-Sitzung.
2. `ApiAdminController.personList:731` — übergibt `query` direkt an
   `listPersons`, ganz ohne Sanitizer (nur `ApiSearchController` bereinigte).

Ein `)` in einem dieser Werte schließt die Assertion und erlaubt eigene
Filter-Logik — also Verzeichnis-Enumeration jenseits der vorgesehenen Suche.

**Umsetzung:** neue Methode `LdapService.sanitizeFilterValue(String)` als
**Whitelist**, angewendet **zentral** in `listPersons` und
`findUserEntryByCustomerNumber`. Damit sind beide Aufrufer abgedeckt,
einschließlich der bisher völlig ungeschützten Admin-Suche.

Erlaubt sind: Buchstaben und Ziffern **jeder Schrift** (`Character.isLetterOrDigit`,
also inklusive Umlauten und Akzenten) sowie Space, Bindestrich, Apostroph und
Punkt. Alles andere entfällt — damit ist das Verlassen der Assertion
strukturell unmöglich, nicht nur "unwahrscheinlich".

**Kompatibilität — mit dem Betrieb abgestimmt:**

| Eingabe | Ergebnis |
|---|---|
| Personensuche: nur Buchstaben und Spaces (laut Betrieb) | **unverändert** |
| Doppelnamen wie `Georg-Martin`, `Müller-Lüdenscheidt` | **unverändert** (Bindestrich erlaubt) |
| Umlaute, `Öztürk`, `Français`, `Große` | **unverändert** |
| `O'Brien`, `Jr. Muster` | **unverändert** |
| Kartennummern: nur Ziffern (laut Betrieb) | **unverändert** |
| Tokenisierung `split("\\s+")` auf zwei Namensteile | **unverändert**, Spaces bleiben erhalten |

Der Blacklist-Sanitizer in `ApiSearchController:64` bleibt unangetastet — er ist
jetzt Defence-in-Depth vor der zentralen Whitelist.

**Nicht betroffen:** `localityConfig.getFilter()` (Zeile 297) wird ohne
Interpolation verwendet; der bei Zeile 291 interpolierte DN stammt aus dem
Verzeichnis selbst, nicht aus einer Anfrage.

**Tests, zweistufig:**

`LdapServiceTest` (59) prüft, dass der Sanitizer *korrekt* ist.
`LdapServiceInjectionTest` (21) prüft, dass er auch *angewendet* wird — gegen ein
In-Memory-Verzeichnis aus dem UnboundID-SDK (bereits Compile-Dependency, also
offline, kein Server, kein Netz). Damit ist die Lücke zwischen „der Helfer ist
richtig" und „der Helfer wird aufgerufen" geschlossen. Ohne die Bereinigung
liefern die Injection-Fälle dort das gesamte Verzeichnis zurück:
`nameSearchInjectionIsNeutralised` (5 Payloads) ·
`cardLookupInjectionIsNeutralised` (5 Payloads) ·
`ordinaryCardLookupWorks` · `customerNumberLookupWorks` ·
`singleTokenSearchesTheSurname` · `twoTokenSearchWorks` ·
`doubleBarrelledNameIsSearchable`

Einheitentests des Helfers, `LdapServiceTest` (59) — `legitimateInputIsUnchanged` (18 Fälle) ·
`cardNumbersAreNeverAltered` · `nameSeparatorsArePreserved` (Doppelnamen) ·
`filterMetacharactersAreRemoved` (19 Zeichen) · `nulByteIsRemoved` ·
`injectionAttemptsAreNeutralised` · `resultingFilterStaysOneAssertion` ·
`sanitizingIsIdempotent` · `whitespaceStructureIsPreserved`

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/controller/api/ApiSearchController.java:64`

```java
String sanitizedQuery = query.replaceAll("[()&|=*\\\\+]", "");
```

Entfernt `( ) & | = * \ +`. **Nicht** entfernt werden `<`, `>`, `!`, `~` und `,`
— also die Operatoren der Extensible-Matching-Regeln (`<=`, `>=`, `~=`) sowie
Negation und DN-Trenner. Der NUL-Byte bleibt ebenfalls stehen.

Ob das ausnutzbar ist, hängt davon ab, wie
`ldap.configuration.user.filter-common-name` den Wert interpoliert. Als alleinige
Verteidigung ist der Sanitizer jedenfalls nicht ausreichend, und derselbe
Filter-Wert wird auch in `ApiAdminController.personList` verwendet.

**Fix-Richtung:** Statt Blacklist die UnboundID-API nutzen —
`com.unboundid.ldap.sdk.Filter.encodeValue(String)` ist über
`unboundid-ldapsdk` bereits verfügbar und escaped korrekt statt zu löschen.
Alternativ Whitelist (Buchstaben, Ziffern, Bindestrich, Punkt, Leerzeichen).

**Gepinnt war das durch:** `ApiSearchControllerTest.unstrippedFilterCharacters`
und `ldapMetacharactersAreStripped`. Beide bleiben unverändert gültig: sie
beschreiben das Verhalten des Controller-Blacklists, das bewusst nicht angefasst
wurde. Die Absicherung liegt jetzt eine Ebene tiefer.

</details>

---

## 8 — ✅ BEHOBEN (2026-08-23) · Backend-Ausfall wurde als 404 maskiert

**Umsetzung** in `AuthService.authCheck`: `catch(Throwable)` → `catch(Exception)`
mit `500 INTERNAL_SERVER_ERROR` und `log.error` inklusive Stacktrace. Ein
`Error` (z. B. `OutOfMemoryError`) propagiert jetzt, statt als HTTP-Status
verschleiert zu werden.

**Kompatibilität — der kritische Punkt geprüft:** Die Pad-JavaScript behandelt
**404 gezielt** (`websocket.js:108` → Alert „userNotFound"). Deshalb wurde
sichergestellt, dass die beiden echten Nicht-gefunden-Fälle **weiterhin 404**
liefern:

| Fall | vorher | jetzt |
|---|---|---|
| Pad-UUID unbekannt | 404 | **404** (unverändert) |
| Pad nicht validiert | 404 | **404** (unverändert) |
| Infrastrukturfehler (DB weg, Datensatz unlesbar) | 404 | **500** |

Nur die letzte Zeile ändert sich. Der versiegelte Vault ist **nicht** betroffen:
`DbService.findSignaturePadbyUUID` liest das per `CryptoHandler`/`secret.bin`
verschlüsselte Feld, `DbService` verwendet `VaultService` überhaupt nicht.
Der Fall tritt also nur bei echten Störungen auf, nicht im Normalbetrieb.

**⚠️ Offene Folge für die Bedienung:** Im Infrastrukturfehlerfall zeigt das Pad
jetzt **keinen** Alert mehr — `websocket.js` reagiert nur auf 404, bei anderen
Status wird ausschließlich in die Browser-Konsole geloggt. Vorher erschien
(irreführend) „Benutzer nicht gefunden". Ein Einzeiler in `websocket.js`
(`else { showAlert(...) }`) würde das beheben; das ist eine bewusste
Entscheidung und wurde hier **nicht** mitgemacht, um die Pad-JS nicht anzufassen.

**Tests:** `AuthServiceTest.backendFailureIsInternalServerError` ·
`ioExceptionIsInternalServerError` · **`genuineNotFoundCasesStayAt404`**
(Kompatibilitätswächter für die Pad-JS) · `errorIsNotSwallowed`

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/controller/api/AuthService.java:73-79`

Der Pad-Lookup ist in `catch(Throwable t)` gehüllt und mündet immer in
`404 NOT FOUND` mit „ERROR: Unable to read signature pad storage.". Datenbank
weg, Vault versiegelt, `OutOfMemoryError` — alles wird zu 404.

**Auswirkung:** Echte Ausfälle sind für Clients und für Log-basiertes Monitoring
nicht von einem unbekannten Pad unterscheidbar. `Throwable` fängt zudem `Error`,
was einen `OutOfMemoryError` verschleiert statt ihn hochzureichen.

**Fix-Richtung:** Nur `Exception` fangen (nicht `Throwable`) und mit
`500 INTERNAL_SERVER_ERROR` antworten; das echte 404 bleibt dem `null`-Fall
vorbehalten.

**Gepinnt war das durch:** `AuthServiceTest.backendFailureIsMaskedAsNotFound`.

</details>

---

## 9 — ✅ BEHOBEN (2026-08-23) · Verwaister Session-Eintrag bei sid-Wiederverwendung

**Umsetzung** in `SessionStoreService.put`: vor dem Schreiben wird über
`evictPreviousSessionFor(sid, session)` der Eintrag der bisher unter dieser sid
registrierten Session aus `byHttpSessionIdCache` entfernt — es sei denn, es ist
dieselbe Session.

**Kompatibilität:** Entfernt ausschließlich einen Eintrag, den vorher niemand
mehr erreichen konnte. Einziger Aufrufer ist
`LoginSuccessHandler.onAuthenticationSuccess`. Keine DB, kein Protokoll.
`getId()` der alten Session ist in `try/catch(Throwable)` gekapselt: manche
Container verweigern das auf einer bereits invalidierten Session, und ein
Fehlschlag dort darf niemals die gerade laufende Anmeldung brechen — die
Aufräumung entfällt dann und der Eintrag verfällt wie bisher mit dem TTL.

**Tests:** `SessionStoreServiceTest.reusingSidDropsPreviousSessionEntry` ·
`reregisteringTheSameSessionIsIdempotent` ·
`brokenPreviousSessionDoesNotBreakLogin`

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/service/SessionStoreService.java:77-84`
(`put`) im Zusammenspiel mit `:115` (`remove`)

`put` schreibt in zwei Caches: `sid → session` und `session.getId() → session`.
Wird dieselbe `sid` mit einer **neuen** Session erneut registriert, überschreibt
das nur den `sid`-Eintrag. Der Eintrag der alten Session im
`byHttpSessionIdCache` bleibt stehen und ist über `remove(sid)` nicht mehr
erreichbar, weil der Lookup dort über den `sid`-Cache läuft, der inzwischen auf
die neue Session zeigt.

**Auswirkung:** Ein Backchannel-Logout hinterlässt eine veraltete
Session-Referenz, die bis zum 8-h-TTL im Speicher bleibt. Tritt auf, wenn ein
Nutzer sich neu anmeldet, während das IdP die `sid` beibehält.

**Fix-Richtung:** In `put` den bisherigen Eintrag zur `sid` lesen und dessen
`session.getId()` aus `byHttpSessionIdCache` invalidieren, bevor neu geschrieben
wird.

**Gepinnt war das durch:**
`SessionStoreServiceTest.reusingSidOrphansPreviousSessionIdEntry`.

</details>

---

## 10 — ✅ BEHOBEN (2026-08-23) · Fehlendes `user` ergab 500 statt 400

**Umsetzung** in `StorageController.receiveObject`: `object.user() == null` wird
direkt nach der Typprüfung mit `400 "Missing user data"` abgelehnt, bevor
`FileStorageService` aufgerufen wird.

**Kompatibilität:** Rein statuscode-seitig. Ein solcher Body konnte **nie**
gespeichert werden — `FileStorageService.buildSecretData:247` dereferenziert
`object.user()` unbedingt und wirft eine NPE, und zwar **vor** dem
`sdbSecretDataRepository.save(...)`. Es gab also auch vorher keinen Teilschreib-
vorgang; der Fix benennt lediglich die verursachende Seite korrekt. Kein
legitimer Payload ist betroffen.

**Tests:** `StorageControllerTest.missingUserIsBadRequest` ·
`missingUserOnArchiveIsBadRequest` — beide prüfen zusätzlich per
`verify(..., never())`, dass nichts gespeichert wird.

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/controller/api/StorageController.java:199`

Ein Body mit erlaubtem `type`, aber ohne `user`-Objekt wird nicht validiert. Der
Fehler tritt erst bei `user.username()` im `try`-Block auf, der pauschal auf
`500 "Failed to store object"` abbildet — nach einem bereits erfolgten
Schreibversuch in `FileStorageService`.

**Auswirkung:** Ein Client-Fehler wird als Serverfehler gemeldet, und der
Datensatz kann teilweise geschrieben worden sein, bevor die Exception auftritt.

**Fix-Richtung:** `object.user()` zusammen mit der `type`-Prüfung validieren und
mit 400 ablehnen, bevor `fileStorageService` aufgerufen wird.

**Gepinnt war das durch:**
`StorageControllerTest.missingUserYieldsInternalServerError`.

</details>

---

## 11 — ✅ SO GEWOLLT (bestätigt TL) · `setSecret`: `size` zählt Zeichen, Checksumme deckt Bytes

**Ort:** `src/main/java/l9g/account/info/db/model/SdbSecretData.java:171-172`

```java
this.size = secret.length();                        // UTF-16-Zeichen
this.checksum = calculateChecksum(secret.getBytes()); // Bytes, Plattform-Charset
```

Für jeden Nicht-ASCII-Inhalt beschreibt `size` nicht die Datenmenge, die
`checksum` abdeckt — `"äöü"` ergibt `size = 3` bei 6 geprüften Bytes.
Zusätzlich hängt die Checksumme vom Plattform-Charset ab, ist also zwischen
Umgebungen nicht reproduzierbar. `setValue(byte[])` ist konstruktionsbedingt
korrekt; nur der String-Pfad weicht ab.

**Kein Handlungsbedarf — nach Prüfung als gewollt bestätigt.** Die Prüfsumme
bleibt ein gültiger Integritätsnachweis über die gespeicherte Zeichenkette;
`size` ist eine Anzeige- und Mengenangabe, keine Aussage über die Anzahl der
geprüften Bytes. Eine Umstellung würde die Prüfsummen **aller** bereits
gespeicherten Datensätze invalidieren — genau das Bestandsdatenrisiko, das gegen
den Fix sprach.

**Zusatzbefund, der die Entscheidung stützt:** Das `getBytes()` ohne Charset ist
auf der Java-21-Basis dieses Projekts unkritisch. Seit **JEP 400** (Java 18) ist
UTF-8 der Standard-Charset, die Prüfsumme ist also umgebungsübergreifend
reproduzierbar — solange niemand `-Dfile.encoding` explizit setzt. Die
ursprüngliche Einordnung als latentes Portabilitätsproblem war damit zu
pessimistisch.

**Gepinnt durch:** `SdbSecretDataTest.setSecretSizeAndChecksumDisagreeForNonAscii`
— der Test hält beide Hälften fest, damit die Semantik nicht versehentlich
„korrigiert" wird.

---

## 12 — ✅ BEHOBEN durch Thorsten Ludewig · Tippfehler im Alias `est-id-status`

**Umsetzung:** `case "est-id-status"` → `case "ext-id-status"` in
`SdbSecretType.fromString`, ohne Kompatibilitäts-Alias.

**Kompatibilität nachträglich geprüft — unkritisch:** Der ursprüngliche
Vorschlag lautete, die alte Schreibweise als Alias zu behalten. Das ist hier
nicht nötig:
- Einziger Aufrufer von `fromString` ist `FileStorageService:230` mit dem
  `side`-Parameter aus `ApiScanController`; die JavaScript sendet
  ausschließlich `front` oder `back` (`workflow.js:310`, `workflow_photo.js:205`).
- `est-id-status` kommt sonst **nirgends** im Repository vor — nicht im Code,
  nicht in der JS, nicht in Konfiguration oder Doku.
- Die `EXT_IDENTIFICATION_*`-Typen erreichen die Anwendung über
  `StorageController`, wo Jackson sie per Enum-**Namen** auflöst
  (`"EXT_IDENTIFICATION_STATUS"`), nicht über `fromString`.

**Tests:** `SdbSecretTypeTest.statusAliasIsSpelledConsistently` (umgedreht) und
die parametrisierte Alias-Liste angepasst.

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/db/model/SdbSecretType.java:74`

`case "est-id-status"` statt `"ext-id-status"` — das Gegenstück in Zeile 76
heißt korrekt `"ext-id-archive"`. Wer den naheliegenden Alias verwendet, bekommt
eine `IllegalArgumentException`.

**Fix-Richtung:** `"ext-id-status"` ergänzen und `"est-id-status"` als
Kompatibilitäts-Alias behalten, bis sicher ist, dass kein Aufrufer die alte
Schreibweise nutzt.

**Gepinnt war das durch:** `SdbSecretTypeTest.statusAliasIsMisspelled`.

</details>

---

## 13 — ✅ BEHOBEN (2026-08-23) · Literal-Name `"null null"`

**Umsetzung:** `EndUserData.description()` baut den Anzeigenamen über die neue
private Methode `displayName()`, die nur vorhandene Teile verwendet — beide
`null` → `null`, ein Teil vorhanden → dieser Teil allein.

**Kompatibilität:** Betrifft ausschließlich **neu** geschriebene Datensätze.
Bestandsdatensätze behalten ihren Wert (auch `"null null"`). Kein Schema, kein
Protokoll berührt. Die Anzeige ist unkritisch: `useraudit.html:345` rendert
`d.name` direkt über `statusRow('Name', d.name)`, ohne Parsing oder Splitting;
ein fehlender Name wird dank `@JsonInclude(NON_NULL)` einfach nicht ausgegeben.

**Tests:** `StorageObjectTest.missingNamePartsYieldNoName` ·
`singleNamePartIsUsedAlone` · `descriptionWithoutNameOmitsTheKey`

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/dto/StorageObject.java:54`

```java
this.givenName + " " + this.surname
```

Ohne Null-Prüfung. Fehlen beide Namensteile, wird der String `"null null"` als
Anzeigename persistiert (`FileStorageService.buildSecretData` schreibt das
Ergebnis in `description`). Bei einem fehlenden Teil entsprechend
`"Marie null"`.

**Fix-Richtung:** Null-Teile herausfiltern und die Reste zusammenfügen; bei
beiden `null` besser `null` liefern als einen Platzhalter-String.

**Gepinnt war das durch:**
`StorageObjectTest.missingNamePartsProduceLiteralNullNull` und
`singleMissingNamePartIsConcatenated`.

</details>

---

## 14 — ✅ BEHOBEN (2026-08-23) · Nicht-String-Claims in `Map<String, String>`

**Umsetzung:**
- Rückgabetyp von `decodeJwtPayload` auf `Map<String, Object>` geändert. Claims
  behalten damit ihren JSON-Typ; der `ClassCastException`-Fallstrick an der
  Aufrufstelle ist weg.
- `new String(decodedBytes, StandardCharsets.UTF_8)` statt Plattform-Default —
  Umlaute in Claims sind jetzt umgebungsunabhängig.

**Aufrufstellen:** Nur `AppController:137-140`, das die Maps zur Anzeige in
`app.html` ins Model legt (Thymeleaf greift per `${idTokenMap.email}` und
`th:each` zu, beides typunabhängig). `BackchannelLogoutController` nutzt die
Methode seit Fix #2 nicht mehr. Keine Signaturänderung nach außen nötig.

**Tests:** `JwtServiceTest.claimsKeepTheirJsonType` (ersetzt
`decodeJwtPayloadLeaksNonStringValues`) · `nonAsciiClaimsAreDecodedAsUtf8`

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/service/JwtService.java:126`

```java
sorted.putAll(objectMapper.readValue(decodedPayload, HashMap.class));
```

Unchecked: `exp`, `iat` und Boolean-Claims landen als `Integer`/`Boolean` in
einer als `Map<String, String>` deklarierten Map. Jeder Aufrufer, der einen
solchen Claim als `String` liest, bekommt eine `ClassCastException` an der
Aufrufstelle — nicht hier.

Nebenbefund in Zeile 122: `new String(decodedBytes)` ohne Charset, also
Plattform-Default. Umlaute in Claims sind damit umgebungsabhängig.

**Fix-Richtung:** Rückgabetyp auf `Map<String, Object>` ändern (oder Werte
konsequent per `String.valueOf` konvertieren) und
`new String(decodedBytes, StandardCharsets.UTF_8)` verwenden.

**Gepinnt war das durch:** `JwtServiceTest.decodeJwtPayloadLeaksNonStringValues`.

</details>

---

## 15 — ✅ BEHOBEN (2026-08-23) · `StorageObject.equals` verglich `data` per Referenz

**Umsetzung:** `equals`, `hashCode` und `toString` sind überschrieben.
`equals`/`hashCode` nutzen `Arrays.equals`/`Arrays.hashCode` für das
`byte[]`-Feld, sind null-sicher und konsistent zueinander.

`toString` gibt zusätzlich **nicht mehr** den Identity-Hash des Arrays aus,
sondern dessen Größe (`data=14 bytes`). Das ist bewusst so: `data` enthält
Ausweisdokument-Inhalte, die niemals in eine Logzeile geraten dürfen — die
generierte Implementierung war zwar nicht undicht, aber auch nutzlos.

**Kompatibilität:** Nichts in `src/main` verglich `StorageObject`-Instanzen; die
JSON-Serialisierung ist unberührt (Jackson nutzt die Record-Komponenten, nicht
`equals`/`toString`).

**Tests:** `StorageObjectTest.equalsComparesDataByContent` ·
`equalsHandlesNullPayload` · `toStringDoesNotLeakThePayload`

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/dto/StorageObject.java:32`

Weil `data` ein `byte[]` ist, nutzt das generierte Record-`equals`
Referenzidentität. Zwei Objekte mit byte-identischem Payload sind ungleich;
`toString()` gibt einen Identity-Hash aus statt des Inhalts.

**Auswirkung:** Nur eine Falle für Aufrufer und Testcode — im Produktivcode
wird derzeit nirgends verglichen.

**Fix-Richtung:** Kompaktes `equals`/`hashCode` mit `Arrays.equals` überschreiben
oder — falls Vergleichbarkeit nie gebraucht wird — im Javadoc dokumentieren.

**Gepinnt war das durch:** `StorageObjectTest.equalsComparesDataByReference`.

</details>

---

## 16 — ✅ BEHOBEN (2026-08-23) · `adminId` wurde als eigener `createdBy` geführt

**Umsetzung:** `VaultService.addVaultAdminKey` hat jetzt die Signatur
`(String createdBy, VaultAdminKey key)`. `VaultApiController.addAdminkey`
übergibt `principal.getName()` — der `@AuthenticationPrincipal` war dort bereits
vorhanden — und schreibt zusätzlich eine Audit-Zeile
`VAULT_ADMINKEY_ENROLLED: createdBy=…, adminId=…`.

**Kompatibilität:** Ändert nur, was bei **künftigen** Vault-Admin-Keys als
`createdBy` gespeichert wird; Bestandseinträge bleiben unverändert. Kein Schema,
kein Protokoll, kein Frontend berührt (`enrollment.html` sendet weiterhin
denselben `VaultAdminKey`-Body). Einziger Aufrufer war
`VaultApiController:82`.

**Tests:** `VaultServiceTest.addVaultAdminKeyRecordsTheActingAdmin`

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/vault/VaultService.java:59`

```java
dbService.saveVaultAdminKey(key.adminId(), new SdbVaultAdminKey(
  key.adminId(), key.adminId(), ...));
```

`key.adminId()` wird zweimal übergeben: als Publisher/`createdBy` und als
`adminId` der Entity. Der Audit-Trail schreibt damit den Schlüsseleigentümer als
seinen eigenen Ersteller, nicht den handelnden Administrator.

**Auswirkung:** Nachvollziehbarkeit im Vault-Audit — relevant für NIS2, weil
nicht rekonstruierbar ist, wer einen Admin-Schlüssel angelegt hat.

**Fix-Richtung:** Den authentifizierten Principal durchreichen und als
`createdBy` verwenden.

**Gepinnt war das durch:** `VaultServiceTest.addVaultAdminKeyPassesAdminIdTwice`.

</details>

---

## 17 — ✅ BEHOBEN (2026-08-23) · Javadoc von `shutdown()` widersprach der Implementierung

**Umsetzung:** Das Javadoc ist korrigiert und benennt jetzt ausdrücklich, dass
nur die Cache-**Referenzen** verworfen werden und die `HttpSession`-Objekte
absichtlich **nicht** invalidiert werden — ihr Lebenszyklus gehört dem
Servlet-Container, und ein Invalidieren an dieser Stelle würde einen
Graceful Restart stören.

**Kompatibilität:** Reine Dokumentationsänderung, kein Codeverhalten berührt.
Die Alternative (tatsächlich invalidieren) wurde bewusst verworfen, weil sie
laufende Sitzungen beim Neustart abgeschossen hätte.

**Tests:** `SessionStoreServiceTest.shutdownClearsCachesWithoutInvalidating`
(unverändert gültig, Begründung im Javadoc angepasst)

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/service/SessionStoreService.java:154-158`

Das Javadoc sagt „invalidating all cached sessions", die Implementierung leert
aber nur die beiden Caches — `HttpSession.invalidate()` wird nie aufgerufen.
Beim Herunterfahren übernimmt das üblicherweise der Container, die Wirkung ist
also harmlos; die Doku ist trotzdem falsch.

**Fix-Richtung:** Entweder Javadoc korrigieren oder die Sessions tatsächlich
invalidieren.

**Gepinnt war das durch:**
`SessionStoreServiceTest.shutdownClearsCachesWithoutInvalidating`.

</details>

---

## 18 — ✅ BEHOBEN (2026-08-23) · Fehlendes `publicJwk` → NPE statt 400

**Umsetzung** in `AuthService.verifyJwt`:
- `publicJwk == null` oder leer → `400` mit „Signature pad has no public key!",
  geprüft **vor** dem `try`-Block.
- `JWK.parse(...)` liefert keinen `RSAKey` (EC-, oct-Schlüssel) → `400` mit
  „Signature pad public key is not an RSA key!" statt einer
  `ClassCastException`. Umgesetzt per `instanceof`-Pattern, der harte Cast ist
  weg.

**Bestandsdaten unberührt:** Beide Änderungen wandeln ausschließlich
Absturzpfade (NPE bzw. CCE → HTTP 500) in gemappte 400er um. Kein Token, das
heute verifiziert, wird davon berührt — `createPrivateJWK()` erzeugt
ausschließlich RSA-Schlüssel, alle Pads im Feld sind RS256. Der Test
`existingRsaPadSignatureStillVerifies` sichert das ab.

**Tests:** `AuthServiceTest.padWithoutPublicJwkIsRejected` ·
`padWithEmptyPublicJwkIsRejected` · `padWithNonRsaPublicJwkIsRejected` ·
`existingRsaPadSignatureStillVerifies`

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/java/l9g/account/info/controller/api/AuthService.java:113`
(`verifyJwt`)

`JWK.parse(signaturePad.getPublicJwk())` wirft eine `NullPointerException`, wenn
das Pad noch keinen Schlüssel hat — gefangen werden nur `ParseException` und
`JOSEException`. Der Client sieht die 500er-Seite des `GlobalExceptionHandler`
statt eines 400. Analog wirft der Cast `(RSAKey)` eine `ClassCastException` bei
einem EC- oder oct-JWK.

**Fix-Richtung:** `publicJwk` auf `null` prüfen und den Cast durch eine
`instanceof`-Prüfung ersetzen; beides mit 400 beantworten.

**Gepinnt war das durch:** `AuthServiceTest.padWithoutPublicJwkThrowsNpe`.

</details>

---

## 19 — ✅ BEHOBEN (2026-08-23) · Lokaler Entwicklerpfad im Release-Artefakt

**Umsetzung** in `pom.xml`: `maven-jar-plugin` schließt `rebel.xml` beim Packen
aus.

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-jar-plugin</artifactId>
  <configuration>
    <excludes>
      <exclude>rebel.xml</exclude>
    </excludes>
  </configuration>
</plugin>
```

**Warum nicht die Datei löschen:** `rebel.xml` muss in `target/classes` liegen,
sonst findet der JRebel-Agent sein Mapping nicht und der Hot-Reload beim
Entwickeln bricht. Die Datei bleibt daher, wo sie ist — ausgeschlossen wird sie
nur aus dem Jar, das `spring-boot-maven-plugin` anschließend repackt.

Eine erste Variante hatte die Datei stattdessen in `prepare-package` aus
`target/classes` gelöscht. Das funktionierte, hätte aber nach einem
`mvn package` eine Lücke gelassen, bis der nächste `process-resources`-Lauf sie
zurückkopiert. Die Jar-Plugin-Variante lässt `target/classes` komplett
unangetastet und ist damit in jedem Szenario unkritisch.

**Verifikation** (nach `mvn clean package`):

| Prüfung | Ergebnis |
|---|---|
| `target/classes/rebel.xml` vorhanden (JRebel) | ✅ ja |
| `rebel.xml` im Jar | ✅ nein |
| `/Users/th/Projects` irgendwo in `BOOT-INF/classes` | ✅ kein Treffer |
| `META-INF/build-info.properties` im Jar | ✅ vorhanden |
| `BOOT-INF/lib`-Einträge | 116 |
| `Start-Class` im Manifest | `l9g.account.info.Application` |
| Tests | 271 grün |

**Nicht mitgemacht:** `rebel.xml` bleibt in git versioniert. Ein
`git rm --cached` plus `.gitignore` wäre konsequenter — die NetBeans-JRebel-
Integration erzeugt die Datei aus dem Token in `nb-configuration.xml` neu — ist
aber ein Eingriff in die Entwicklungsumgebung und wurde daher offen gelassen.
Der Pfad in der git-Historie lässt sich ohnehin nicht mehr entfernen; die
substanzielle Lücke (Pfad in jedem Release und Docker-Image) ist geschlossen.

<details>
<summary>Ursprünglicher Befund</summary>

**Ort:** `src/main/resources/rebel.xml` (in git eingecheckt)

Die JRebel-Konfiguration wird als `BOOT-INF/classes/rebel.xml` in das Fat-Jar
gepackt und enthält den absoluten Pfad
`/Users/th/Projects/l9g/webapp/l9g-accountinfo/target/classes`.

**Auswirkung:** Ohne JRebel-Agent zur Laufzeit wirkungslos, aber der lokale
Pfad wandert in jedes Release und in das Docker-Image — unnötige
Informationspreisgabe über die Build-Umgebung.

**Fix-Richtung:** `rebel.xml` aus `src/main/resources` entfernen und stattdessen
gitignoren (die NetBeans-JRebel-Integration generiert sie ohnehin lokal aus dem
Token in `nb-configuration.xml`). Alternativ im `spring-boot-maven-plugin` per
`<excludes>` aus dem Jar ausschließen.

**Status war: ungetestet** — Build-Artefakt, nicht per Unit-Test prüfbar;
verifiziert wurde stattdessen der Jar-Inhalt.

</details>

---

## 21 — ✅ SO GEWOLLT (bestätigt TL) · Leere Suchanfrage listet alle Personen

**Ort:** `src/main/java/l9g/account/info/service/LdapService.java:424` (`listPersons`)

Eine Anfrage, die auf nichts zusammenschrumpft — der leere String, nur
Leerzeichen, oder ausschließlich entfernte Zeichen wie `*` — ergibt den Filter
`(&(givenName=*)(sn=*)(objectClass=soniaPerson)(!(soniaIsUnregistered=*)))` und
liefert damit **jede** Person im Verzeichnis.

**Kein Regress:** Das galt vorher genauso. `ApiSearchController` entfernte `*`
schon immer zum leeren String, und der unsanitisierte Admin-Pfad erzeugte mit
`*` das ebenso permissive `(givenName=**)`. Die Zentralisierung der Bereinigung
hat daran nichts geändert.

**Einordnung:** Beide Suchendpunkte verlangen eine authentifizierte Sitzung
(`/api/v1/search/person` ein validiertes Pad, `/api/v1/admin/secret/search/person`
ADMIN bzw. AUDITADMIN mit entsiegeltem Vault). Das Auflisten ist damit eine
Funktion dieser Oberflächen für Berechtigte.

**Kein Handlungsbedarf — als gewollt bestätigt.** Der Eintrag bleibt hier
stehen, weil das Verhalten aus dem Aufruf nicht ersichtlich ist; der Test hält es
fest, damit es nicht versehentlich durch eine Mindestlängenprüfung
wegoptimiert wird.

**Gepinnt durch:** `LdapServiceInjectionTest.emptyQueryMatchesEveryPerson`
(4 Fälle)

---

## 22 — ✅ SO GEWOLLT (bestätigt TL) · Ein-Wort-Suche durchsucht nur den Nachnamen

**Ort:** `src/main/java/l9g/account/info/service/LdapService.java:425-431` (`listPersons`)

```java
String filter = String.format(userConfig.getFilterCommonName(), "", query, "");

String[] queryToken = query.split("\\s+");
if(queryToken.length > 1)
{
  filter = String.format(userConfig.getFilterCommonName(),
    queryToken[0].trim(), queryToken[1].trim(), "");
}
```

Bei **einem** Token wird der Wert als *zweites* Argument übergeben. Im Template
`(&(givenName=%s*)(sn=%s*)…)` ist das `sn` — eine Ein-Wort-Suche vergleicht also
nur den **Nachnamen**, und der Vorname bleibt als `(givenName=*)` offen. Die
Suche nach einem Vornamen allein findet niemanden.

**Kein Handlungsbedarf — als gewollt bestätigt.** Ein zweites Token schränkt
dann über den Vornamen ein. Der Eintrag bleibt hier stehen, weil die
Argumentreihenfolge das durchsuchte Feld bestimmt und das leicht zu übersehen ist
(das dritte `%s` wird nie verwendet, das Template hat nur zwei Platzhalter). Wer
die Suchsemantik anfasst, sollte das vorher wissen.

**Gepinnt durch:** `LdapServiceInjectionTest.singleTokenSearchesTheSurname` —
der Test hält beide Hälften fest: `muster` findet die Person, `marie` nicht.

---

## Hinweise zum Abarbeiten

- Die Suite läuft mit `mvn test` in unter 3 Sekunden — kein Spring-Context,
  keine DB, kein Netz. Aktuell **292 Tests**.
- Jeder noch offene, gepinnte Test trägt im Javadoc einen `PINNED DEFECT`- bzw.
  `PINNED GAP`-Block mit derselben Begründung wie hier. Nach einem Fix schlägt
  genau dieser Test fehl — das ist beabsichtigt und der Nachweis, dass der Fix
  greift.

### Bestandsdatenschutz — gilt für jeden weiteren Fix

Über **20 Signatur-Pads sind im Feld**; ihre bereits erzeugten Signaturen
lassen sich nicht ändern. Archivierte Signatur-JWTs müssen dauerhaft
verifizierbar bleiben. Jeder Fix ist daran zu messen:

- **#4b** wurde deshalb bewusst **nicht** gefixt (siehe dort).
- **#11** wurde genau deshalb **nicht** gefixt und als gewollt bestätigt: eine
  Umstellung hätte die Prüfsummen aller bestehenden String-Secrets invalidiert.
- **#12** (Alias-Tippfehler): den alten Namen `est-id-status` als
  Kompatibilitäts-Alias behalten, sonst brechen Aufrufer, die ihn benutzen.
- **#16** ändert nur, was bei *künftigen* Vault-Admin-Keys als `createdBy`
  geschrieben wird — Bestandseinträge bleiben, wie sie sind.
- **#13** ändert den persistierten Anzeigenamen künftiger Datensätze;
  Bestandsdatensätze behalten ihr `"null null"`.

### Abgeschlossen

Alle 22 Befunde sind erledigt: **18 behoben**, **1 von TL behoben** (#12),
**4 nach Prüfung als gewolltes Verhalten bestätigt** (#4b, #11, #21, #22).

Die vier bestätigten Verhaltensweisen bleiben in dieser Datei stehen und sind
durch Tests festgehalten — nicht als offene Punkte, sondern weil sie aus dem
Code allein nicht ersichtlich sind:

| # | Was man wissen muss |
|---|---|
| 4b | Pad-JWTs haben absichtlich kein `exp`; Signaturen laufen nie ab, weil dieselbe Methode das Archiv nachprüft |
| 11 | `size` ist Anzeige-/Mengenangabe, die Prüfsumme deckt die Bytes — Umstellung würde Bestandsprüfsummen invalidieren |
| 21 | Leere Suchanfrage listet bewusst alle Personen |
| 22 | Ein-Wort-Suche vergleicht den Nachnamen; die Argumentreihenfolge bestimmt das Feld |

Wer eine dieser Stellen anfasst, bekommt einen roten Test — genau so gedacht.
