# Datenschutz-Folgenabschätzung (DSFA) – l9g-accountinfo {{version}}

*Automatisch erzeugt am {{date}} (`mvn -Prelease verify`) als Release-Nachweis nach Art. 35 DSGVO. Die vollständige DSFA mit Schwellwertanalyse, Verhältnismäßigkeitsprüfung und Detailbewertung steht in `ACCOUNT_DSFA.md`. Technisch fundierte Risikoanalyse, keine Rechtsberatung; die abschließende Würdigung obliegt dem DSB.*

## 1. Notwendigkeit

Die Anwendung verarbeitet besondere Kategorien personenbezogener Daten (Art. 9 DSGVO: Lichtbild, Unterschrift, Ausweisdaten, Video und Audio der Online-Identifizierung). Eine DSFA ist nach Art. 35 Abs. 3 lit. b DSGVO erforderlich.

## 2. Datenbestand dieses Release

### 2.1 Datentypen

{{dataTypeTable}}

### 2.2 Personenbezogene Spalten

{{personalDataTable}}

## 3. Risikobewertung

Bewertungsmaßstab: Wahrscheinlichkeit (W) und Schwere (S) je 1 = gering, 2 = mittel, 3 = hoch; Wert = W × S; Stufe 1–2 gering, 3–4 mittel, 6–9 hoch. Bewertet werden die Restrisiken nach den umgesetzten Maßnahmen. Quelle: `docs/compliance/risk-register.yaml`.

{{riskSummary}}

{{riskTable}}

## 4. Ergebnis

Hohe Restrisiken sind in der Übersicht in Abschnitt 3 ausgewiesen; sie sind mit den geplanten Maßnahmen zu senken, bevor die Verarbeitung als datenschutzkonform freigegeben wird. Ob eine Konsultation der Aufsichtsbehörde nach Art. 36 DSGVO erforderlich ist, entscheidet der DSB. Die DSFA ist bei wesentlichen Änderungen (neue Datenarten, neue Empfänger, geänderte Fristen) und spätestens alle 24 Monate zu überprüfen.

## 5. Freigabe

| Rolle | Name / Stelle | Datum | Unterschrift |
|---|---|---|---|
| Verantwortlicher | _________________ | ________ | ____________ |
| Datenschutzbeauftragter | _________________ | ________ | ____________ |
| Technischer Betrieb | _________________ | ________ | ____________ |
