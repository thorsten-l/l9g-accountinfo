--
-- l9g-accountinfo 2.5.1 — Migration
--
-- Stellt zwei Spalten der Tabelle secretdata auf text um:
--
--   secret       varchar(262144) -> text   (behebt einen aufgetretenen Fehler)
--   description  varchar(255)    -> text   (schliesst dieselbe Fehlerklasse)
--
-- Behebt: ERROR: value too long for type character varying(262144)
--         beim Speichern einer Signatur (SdbSecretData.secret).
--
-- Ursache: Die Nutzlast wird auf dem Weg in die Spalte ZWEIMAL base64-kodiert —
-- einmal als sigpng im Signatur-JWT, einmal durch die AES-256-Verschlüsselung.
-- Die Netto-Aufweitung vom PNG bis zum gespeicherten String liegt bei etwa
-- 1,78×. In varchar(262144) passten damit nur rund 110 KB Signatur-PNG; größere
-- Signaturen scheiterten am INSERT und waren verloren.
--
-- Messung (crypto-core 1.0.5, AES-256-GCM + Base64 + "{AES256}"-Prefix):
--   Klartext 196.574 Zeichen -> 262.144 Zeichen verschlüsselt (Faktor 1,3336)
--   Das JWT selbst kodiert die Claims erneut base64url (Faktor 1,3333).
--
-- Der Wert ist bereits verschlüsselt, wenn er die Spalte erreicht; eine
-- Zeichenlängen-Grenze ist hier also keine sinnvolle Schranke für die Nutzlast.
--
-- ---------------------------------------------------------------------------
-- WICHTIG: Diese Migration ist NICHT optional.
-- Hibernate läuft mit ddl-auto: update und ändert bestehende Spaltentypen
-- NICHT. Ohne dieses Skript bleibt die Spalte varchar(262144) und der Fehler
-- tritt weiter auf.
-- ---------------------------------------------------------------------------
--
-- Datenverlustrisiko: keines. In PostgreSQL ist varchar(n) -> text eine
-- Metadaten-Änderung ohne Tabellen-Rewrite; bestehende Werte bleiben
-- unverändert, es wird keine Zeile angefasst. Die Anweisung nimmt kurz ein
-- ACCESS EXCLUSIVE Lock auf die Tabelle und läuft in Millisekunden.
--
-- Aufruf (Beispiel):
--   psql -h localhost -U <user> -d signaturedb -f 2.5.1-secret-column-to-text.sql
--

\echo '--- Vorher ---'
SELECT column_name, data_type, character_maximum_length
  FROM information_schema.columns
 WHERE table_name = 'secretdata'
   AND column_name IN ('secret', 'description')
 ORDER BY column_name;

BEGIN;

ALTER TABLE secretdata
  ALTER COLUMN secret TYPE text;

ALTER TABLE secretdata
  ALTER COLUMN description TYPE text;

COMMIT;

\echo '--- Nachher (erwartet: beide text, character_maximum_length leer) ---'
SELECT column_name, data_type, character_maximum_length
  FROM information_schema.columns
 WHERE table_name = 'secretdata'
   AND column_name IN ('secret', 'description')
 ORDER BY column_name;

--
-- ---------------------------------------------------------------------------
-- Warum description mitgeht
-- ---------------------------------------------------------------------------
--
-- SdbSecretData.description hatte keine @Column-Laengenangabe und war damit
-- varchar(255). Dort landet UNVERSCHLUESSELT ein JSON-Objekt:
--
--   Signaturpfad:  {"name":"...","mail":"..."}
--   Storage-API:   {"pid":"...","mail":"...","name":"...","username":"..."}
--
-- Bei langen Werten (UUID-artige pid + lange Mailadresse + Doppelname) kann das
-- 255 Zeichen ueberschreiten und denselben Fehler fuer eine andere Spalte
-- ausloesen. Aufgetreten war das noch nicht; die Umstellung ist Vorsorge.
--
-- Beide Spalten sind in SdbSecretData mit @Column(columnDefinition = "text")
-- gespiegelt, damit ein frisch angelegtes Schema dieselbe Definition erhaelt.
--
