# Prozessbeschreibung / Verzeichnis von Verarbeitungstätigkeiten – l9g-accountinfo {{version}}

*Automatisch erzeugt am {{date}} (`mvn -Prelease verify`) als Release-Nachweis nach Art. 30 DSGVO. Die ausführliche Verfahrensbeschreibung mit Betriebsumgebung, Datenflüssen und Betroffenenrechten steht in `ACCOUNT_PROZESS_BESCHREIBUNG.md`. Technische Beschreibung, keine Rechtsberatung; die datenschutzrechtliche Bewertung obliegt dem DSB.*

## 1. Zweck der Anwendung

l9g-accountinfo unterstützt Identitäts- und Ausweisprozesse: Ausgabe und Aktivierung von Benutzerkonten und Chipkarten, Erfassung handschriftlicher Unterschriften über Signature-Pads und Übernahme von Online-Identifizierungsergebnissen eines externen eID-Dienstes. Verarbeitet werden auch **besondere Kategorien personenbezogener Daten (Art. 9 DSGVO)**: Lichtbild, Unterschrift, Ausweisdaten, Video und Audio.

## 2. Gespeicherte Datentypen

Je Wert des Enums `SdbSecretType`; Beschreibungen aus `docs/compliance/processing-activities.yaml`.

{{dataTypeTable}}

## 3. Datenmodell

Ermittelt per Reflection über alle JPA-Entities (inklusive geerbter Spalten aus `SdbUuidObject`).

{{entityTable}}

### 3.1 Personenbezogene Spalten

Gekennzeichnet mit `@PersonalData` im Code.

{{personalDataTable}}

## 4. Verarbeitungstätigkeiten

{{processingActivities}}

## 5. Speicherbegrenzung

Der `DbDeletionScheduler` gleicht täglich mit dem LDAP-Verzeichnis ab und löscht für jede Person, die länger als die Karenzzeit (Standard P90D) nicht mehr im Verzeichnis steht, alle Nachweise in der Datenbank, die zugehörigen verschlüsselten Dateien und den Abgleichseintrag. Daneben gibt es die manuelle Sofort-Löschung je Person im Personen-Audit.

## 6. Verantwortlichkeiten (durch die Organisation auszufüllen)

| Rolle | Name / Stelle |
|---|---|
| Verantwortlicher (Art. 4 Nr. 7) | _________________ |
| Datenschutzbeauftragter | _________________ |
| Technischer Betrieb | _________________ |
| Auftragsverarbeiter (eID-Dienst, Hosting) | _________________ |
