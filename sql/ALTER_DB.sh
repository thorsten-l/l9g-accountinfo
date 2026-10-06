#!/usr/bin/env bash
#
# Copyright 2026 Thorsten Ludewig (t.ludewig@gmail.com).
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Wendet die Migration 2.5.1 auf den laufenden signaturedb-Container an:
#
#   secretdata.secret       varchar(262144) -> text
#   secretdata.description  varchar(255)    -> text
#
# Das Skript ist idempotent: sind beide Spalten schon text, endet es ohne
# Änderung. Vor dem ALTER wird ein pg_dump der Datenbank geschrieben.
#
set -euo pipefail

CONTAINER="${CONTAINER:-signaturedb}"
DB_NAME="${DB_NAME:-signaturedb}"
DB_USER="${DB_USER:-signaturedb}"
BACKUP_DIR="${BACKUP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/backup}"

DO_BACKUP=1
DRY_RUN=0

usage()
{
  cat <<EOF
ALTER_DB.sh — Migration 2.5.1 auf den signaturedb-Container anwenden

  secretdata.secret       varchar(262144) -> text
  secretdata.description  varchar(255)    -> text

Optionen
  --dry-run      Nur anzeigen, was geschehen würde. Keine Änderung.
  --no-backup    Ohne pg_dump vorab. Nicht empfohlen.
  -h, --help     Diese Hilfe.

Umgebungsvariablen (Defaults passen zu docker-compose.yaml)
  CONTAINER=$CONTAINER
  DB_NAME=$DB_NAME
  DB_USER=$DB_USER
  BACKUP_DIR=$BACKUP_DIR

Beispiele
  ./ALTER_DB.sh --dry-run
  ./ALTER_DB.sh
  CONTAINER=signaturedb-test ./ALTER_DB.sh

Hinweise
  - Der Verbindungsaufbau läuft per 'docker exec' über den Unix-Socket im
    Container, es wird also kein Passwort benötigt.
  - varchar(n) -> text ist in PostgreSQL eine Metadaten-Änderung ohne
    Tabellen-Rewrite: bestehende Werte bleiben unverändert, es wird keine Zeile
    angefasst. Die Anweisung nimmt kurz ein ACCESS EXCLUSIVE Lock und läuft in
    Millisekunden.
  - Die Anwendung muss dafür nicht gestoppt werden. Ein laufender Schreibzugriff
    wartet für die Dauer des Locks.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run)   DRY_RUN=1 ;;
    --no-backup) DO_BACKUP=0 ;;
    -h|--help)   usage; exit 0 ;;
    *)           echo "ALTER_DB.sh: unbekannte Option '$1'" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
die()  { printf '\n\033[1;31mALTER_DB.sh: %s\033[0m\n' "$*" >&2; exit 1; }

# Führt psql im Container aus, still und ohne Formatierung.
psql_q()
{
  docker exec -i "$CONTAINER" \
    psql -U "$DB_USER" -d "$DB_NAME" -qtAX -c "$1"
}

# Zählt Treffer eines Musters in der gzip-Sicherung.
#
# Bewusst über eine Kommandosubstitution und nicht als 'gzip -dc | grep -q':
# grep steigt aus, sobald die Ausgabe verworfen wird, gzip bekommt dann SIGPIPE
# (Exit 141), und 'set -o pipefail' macht daraus einen Fehlschlag der ganzen
# Pipeline — obwohl das Muster vorhanden ist. Bei einer kleinen Datei fällt das
# nicht auf, weil gzip fertig ist, bevor grep aussteigt; bei einer echten
# Sicherung schlägt es zu.
dump_matches()
{
  local file="$1" pattern="$2" count
  count="$( { gzip -dc "$file" | grep -c -- "$pattern"; } 2>/dev/null || true )"
  count="${count%%[^0-9]*}"
  [ -n "$count" ] && [ "$count" -gt 0 ]
}

# ---------------------------------------------------------------- Vorbedingungen

step "Vorbedingungen prüfen"

command -v docker >/dev/null 2>&1 || die "docker nicht gefunden."

docker inspect "$CONTAINER" >/dev/null 2>&1 \
  || die "Container '$CONTAINER' existiert nicht. Mit CONTAINER=<name> überschreiben."

running="$(docker inspect -f '{{.State.Running}}' "$CONTAINER")"
[ "$running" = "true" ] \
  || die "Container '$CONTAINER' läuft nicht. Erst 'docker compose up -d' ausführen."
info "Container '$CONTAINER' läuft."

docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 \
  || die "PostgreSQL in '$CONTAINER' ist nicht bereit (pg_isready)."

# pg_isready allein genügt nicht: während der Erstinitialisierung antwortet der
# temporäre initdb-Server bereits mit "ready", obwohl die Zieldatenbank noch
# nicht existiert und der Server gleich neu startet. Eine echte Abfrage schließt
# dieses Fenster.
psql_q 'SELECT 1' >/dev/null 2>&1 \
  || die "Datenbank '$DB_NAME' ist nicht abfragbar. Läuft der Container noch
     hoch oder fährt er herunter? Kurz warten und erneut versuchen."
info "PostgreSQL ist bereit und '$DB_NAME' ist abfragbar."

version="$(psql_q 'SHOW server_version;')"
[ -n "$version" ] || die "Serverversion konnte nicht gelesen werden."
info "Version: $version"

table_exists="$(psql_q "SELECT to_regclass('public.secretdata') IS NOT NULL;")"
[ "$table_exists" = "t" ] \
  || die "Tabelle 'secretdata' nicht gefunden in Datenbank '$DB_NAME'."

# --------------------------------------------------------------- Ist-Zustand

step "Aktuelle Spaltentypen"

current="$(psql_q "
  SELECT column_name || ' ' || data_type ||
         coalesce('(' || character_maximum_length || ')', '')
    FROM information_schema.columns
   WHERE table_schema = 'public'
     AND table_name = 'secretdata'
     AND column_name IN ('secret', 'description')
   ORDER BY column_name;")"
printf '%s\n' "$current" | while IFS= read -r line; do info "$line"; done

pending=""
for col in secret description; do
  t="$(psql_q "
    SELECT data_type
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'secretdata'
       AND column_name = '$col';")"
  [ -n "$t" ] || die "Spalte '$col' nicht gefunden."
  if [ "$t" != "text" ]; then
    pending="$pending $col"
  fi
done

if [ -z "$pending" ]; then
  step "Nichts zu tun"
  info "Beide Spalten sind bereits 'text'. Migration schon angewendet."
  exit 0
fi

info "Umzustellen:$pending"

rows="$(psql_q 'SELECT count(*) FROM secretdata;')"
info "Datensätze in secretdata: $rows (werden nicht angefasst)"

if [ "$DRY_RUN" -eq 1 ]; then
  step "Dry-Run — es wird nichts geändert"
  for col in $pending; do
    info "ALTER TABLE secretdata ALTER COLUMN $col TYPE text;"
  done
  exit 0
fi

# ------------------------------------------------------------------- Backup

if [ "$DO_BACKUP" -eq 1 ]; then
  step "Sicherung anlegen"
  mkdir -p "$BACKUP_DIR"
  stamp="$(date +%Y%m%d-%H%M%S)"
  dump="${BACKUP_DIR}/${DB_NAME}-vor-2.5.1-${stamp}.sql.gz"

  # pg_dump im Container, Ausgabe direkt auf den Host komprimieren.
  if docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" \
       | gzip > "$dump"
  then
    size="$(du -h "$dump" | cut -f1)"
    info "Sicherung: $dump ($size)"
  else
    rm -f "$dump"
    die "pg_dump ist fehlgeschlagen — Migration abgebrochen.
     Mit --no-backup ließe sich das überspringen, davon ist aber abzuraten."
  fi

  # Vollständigkeit prüfen, nicht die Größe: eine kleine Datenbank ergibt
  # legitim einen kleinen Dump. pg_dump schließt mit einer Marker-Zeile ab, und
  # gzip -t deckt einen abgebrochenen Schreibvorgang auf.
  gzip -t "$dump" 2>/dev/null \
    || die "Sicherung ist kein gültiges gzip — Migration abgebrochen.
     $dump prüfen."

  dump_matches "$dump" 'PostgreSQL database dump complete' \
    || die "Sicherung enthält nicht die Abschlussmarke von pg_dump, ist also
     unvollständig — Migration abgebrochen. $dump prüfen."

  dump_matches "$dump" 'CREATE TABLE.*secretdata' \
    || die "Sicherung enthält die Tabelle secretdata nicht — Migration
     abgebrochen. $dump prüfen."

  info "Sicherung geprüft: gzip gültig, pg_dump vollständig, secretdata enthalten."
else
  step "Sicherung übersprungen (--no-backup)"
fi

# ---------------------------------------------------------------- Migration

step "Migration anwenden"

# Alles in einer Transaktion: entweder beide Spalten oder keine.
if docker exec -i "$CONTAINER" \
     psql -U "$DB_USER" -d "$DB_NAME" -qX -v ON_ERROR_STOP=1 <<'SQL'
BEGIN;
ALTER TABLE secretdata ALTER COLUMN secret TYPE text;
ALTER TABLE secretdata ALTER COLUMN description TYPE text;
COMMIT;
SQL
then
  info "ALTER TABLE erfolgreich."
else
  die "Migration fehlgeschlagen. Die Transaktion wurde zurückgerollt, das
     Schema ist unverändert."
fi

# ------------------------------------------------------------- Verifikation

step "Verifikation"

ok=1
for col in secret description; do
  t="$(psql_q "
    SELECT data_type ||
           coalesce('(' || character_maximum_length || ')', '')
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'secretdata'
       AND column_name = '$col';")"
  if [ "$t" = "text" ]; then
    info "OK    secretdata.$col ist jetzt text"
  else
    info "FEHLT secretdata.$col ist $t"
    ok=0
  fi
done

rows_after="$(psql_q 'SELECT count(*) FROM secretdata;')"
if [ "$rows_after" = "$rows" ]; then
  info "OK    Datensätze unverändert: $rows_after"
else
  info "WARNUNG Datensatzzahl verändert: $rows -> $rows_after"
  ok=0
fi

[ "$ok" -eq 1 ] || die "Verifikation nicht bestanden — Ausgabe oben prüfen."

step "Fertig"
info "Die Anwendung kann Signaturen jetzt ohne Größenbegrenzung speichern."
info "Ein Neustart der Anwendung ist nicht erforderlich."
