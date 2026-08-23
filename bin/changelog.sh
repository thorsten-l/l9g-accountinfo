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
# Pflegt CHANGELOG.md im "Keep a Changelog"-Format.
#
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHANGELOG="${PROJECT_DIR}/CHANGELOG.md"
POM="${PROJECT_DIR}/pom.xml"

# Erlaubte Abschnitte laut Keep a Changelog.
SECTIONS="Added Changed Deprecated Removed Fixed Security"

usage()
{
  cat <<'EOF'
changelog.sh — CHANGELOG.md pflegen

  check
      Prüft, dass die Version aus pom.xml in CHANGELOG.md dokumentiert ist.
      Bricht mit Exit-Code 1 ab, wenn nicht. Hängt im Maven-Build an der
      validate-Phase, damit kein Release ohne Eintrag entsteht.

      Sonderfall: endet die Version auf -SNAPSHOT, genügt ein [Unreleased]-
      Abschnitt mit Inhalt.

  unreleased <Abschnitt> "<Text>"
      Fügt einen Eintrag unter [Unreleased] ein.
      Abschnitt: Added | Changed | Deprecated | Removed | Fixed | Security

  release [<Version>]
      Wandelt [Unreleased] in einen Versionsabschnitt mit heutigem Datum um
      und legt ein neues, leeres [Unreleased] darüber an. Ohne Argument wird
      die Version aus pom.xml verwendet. Aktualisiert auch die
      Vergleichs-Links am Dateiende.

  version
      Gibt die Version aus pom.xml aus.

Beispiele
  bin/changelog.sh unreleased Fixed "Zeitzone in der Pad-Übersicht korrigiert"
  bin/changelog.sh unreleased Security "Rate-Limit für /api/v1/userinfo"
  bin/changelog.sh check
  bin/changelog.sh release 2.5.1
EOF
}

die()
{
  echo "changelog.sh: $*" >&2
  exit 1
}

# Liest die Projektversion aus pom.xml, ohne Maven zu starten: das erste
# <version> nach </parent> ist die des Projekts.
pom_version()
{
  [ -f "$POM" ] || die "pom.xml nicht gefunden: $POM"
  sed -n '/<\/parent>/,$p' "$POM" \
    | grep -m1 -o '<version>[^<]*</version>' \
    | sed 's/<[^>]*>//g'
}

# Prüft, ob ein Abschnittsname erlaubt ist.
check_section()
{
  local wanted="$1" s
  for s in $SECTIONS; do
    [ "$s" = "$wanted" ] && return 0
  done
  die "unbekannter Abschnitt '$wanted', erlaubt: $SECTIONS"
}

# Liefert 0, wenn [Unreleased] mindestens eine Listenzeile enthält.
unreleased_has_content()
{
  awk '
    /^## \[Unreleased\]/ { inside = 1; next }
    /^## \[/            { inside = 0 }
    inside && /^[-*] /  { found = 1 }
    END                 { exit(found ? 0 : 1) }
  ' "$CHANGELOG"
}

cmd_check()
{
  [ -f "$CHANGELOG" ] || die "CHANGELOG.md nicht gefunden: $CHANGELOG"

  local version
  version="$(pom_version)"
  [ -n "$version" ] || die "Version konnte nicht aus pom.xml gelesen werden"

  if [[ "$version" == *-SNAPSHOT ]]; then
    if unreleased_has_content; then
      echo "changelog.sh: OK — $version, [Unreleased] ist gefüllt"
      return 0
    fi
    die "Version $version ist ein SNAPSHOT, aber [Unreleased] in CHANGELOG.md ist leer.
     Eintrag hinzufügen:  bin/changelog.sh unreleased Fixed \"...\""
  fi

  if grep -qE "^## \[${version//./\\.}\]" "$CHANGELOG"; then
    echo "changelog.sh: OK — $version ist in CHANGELOG.md dokumentiert"
    return 0
  fi

  die "Version $version fehlt in CHANGELOG.md.
     Abschnitt anlegen:   bin/changelog.sh release $version
     oder Eintrag setzen: bin/changelog.sh unreleased Added \"...\""
}

cmd_unreleased()
{
  local section="${1:-}" text="${2:-}"
  [ -n "$section" ] && [ -n "$text" ] || die "Aufruf: unreleased <Abschnitt> \"<Text>\""
  check_section "$section"
  [ -f "$CHANGELOG" ] || die "CHANGELOG.md nicht gefunden"

  grep -q '^## \[Unreleased\]' "$CHANGELOG" \
    || die "kein [Unreleased]-Abschnitt in CHANGELOG.md"

  local tmp
  tmp="$(mktemp)"

  # Die Datei wird komplett gepuffert (ein Changelog ist klein) und der
  # [Unreleased]-Block gezielt bearbeitet: der neue Eintrag kommt ans ENDE des
  # passenden Abschnitts, damit die Reihenfolge chronologisch bleibt und die
  # Leerzeilen des Keep-a-Changelog-Formats erhalten bleiben.
  SECTION="$section" TEXT="$text" awk '
    { line[NR] = $0 }
    END {
      n = NR
      section_heading = "### " ENVIRON["SECTION"]

      # Grenzen des [Unreleased]-Blocks bestimmen
      ustart = 0
      for(i = 1; i <= n; i ++ )
      {
        if(line[i] ~ /^## \[Unreleased\]/) { ustart = i; break }
      }
      uend = n + 1
      for(i = ustart + 1; i <= n; i ++ )
      {
        if(line[i] ~ /^## \[/) { uend = i; break }
      }

      # gesuchten Abschnitt innerhalb des Blocks suchen
      shead = 0
      for(i = ustart + 1; i < uend; i ++ )
      {
        if(line[i] == section_heading) { shead = i; break }
      }

      blocklen = 0
      if(shead == 0)
      {
        # Abschnitt fehlt: hinter der letzten nicht-leeren Zeile des Blocks neu anlegen
        insert_after = ustart
        for(i = ustart + 1; i < uend; i ++ )
        {
          if(line[i] != "") insert_after = i
        }
        block[++ blocklen] = ""
        block[++ blocklen] = section_heading
        block[++ blocklen] = ""
        block[++ blocklen] = "- " ENVIRON["TEXT"]
      }
      else
      {
        # Ende des Abschnitts finden, dann hinter dessen letzte nicht-leere Zeile
        send = uend
        for(i = shead + 1; i < uend; i ++ )
        {
          if(line[i] ~ /^### /) { send = i; break }
        }
        insert_after = shead
        for(i = shead + 1; i < send; i ++ )
        {
          if(line[i] != "") insert_after = i
        }
        if(insert_after == shead) block[++ blocklen] = ""
        block[++ blocklen] = "- " ENVIRON["TEXT"]
      }

      for(i = 1; i <= n; i ++ )
      {
        print line[i]
        if(i == insert_after)
        {
          for(j = 1; j <= blocklen; j ++ ) print block[j]
        }
      }
    }
  ' "$CHANGELOG" > "$tmp"

  mv "$tmp" "$CHANGELOG"
  echo "changelog.sh: [Unreleased] / $section — $text"
}

cmd_release()
{
  local version="${1:-}"
  [ -n "$version" ] || version="$(pom_version)"
  [ -n "$version" ] || die "keine Version angegeben und keine in pom.xml gefunden"

  [ -f "$CHANGELOG" ] || die "CHANGELOG.md nicht gefunden"
  grep -qE "^## \[${version//./\\.}\]" "$CHANGELOG" \
    && die "Version $version ist in CHANGELOG.md bereits dokumentiert"

  unreleased_has_content \
    || die "[Unreleased] ist leer — es gibt nichts zu veröffentlichen"

  local today previous tmp
  today="$(date +%F)"
  previous="$(grep -m1 -oE '^## \[[0-9][^]]*\]' "$CHANGELOG" \
    | sed 's/^## \[//; s/\]$//' || true)"
  tmp="$(mktemp)"

  VERSION="$version" TODAY="$today" awk '
    /^## \[Unreleased\]/ && !done {
      print "## [Unreleased]"
      print ""
      print "## [" ENVIRON["VERSION"] "] - " ENVIRON["TODAY"]
      done = 1
      next
    }
    { print }
  ' "$CHANGELOG" > "$tmp"
  mv "$tmp" "$CHANGELOG"

  # Vergleichs-Links am Dateiende fortschreiben, falls vorhanden.
  if grep -q '^\[Unreleased\]:' "$CHANGELOG" && [ -n "$previous" ]; then
    local repo
    repo="$(sed -n 's|^\[Unreleased\]: \(.*\)/compare/.*|\1|p' "$CHANGELOG" \
      | head -1)"
    if [ -n "$repo" ]; then
      tmp="$(mktemp)"
      VERSION="$version" PREVIOUS="$previous" REPO="$repo" awk '
        /^\[Unreleased\]:/ {
          print "[Unreleased]: " ENVIRON["REPO"] "/compare/v" \
            ENVIRON["VERSION"] "...HEAD"
          print "[" ENVIRON["VERSION"] "]: " ENVIRON["REPO"] "/compare/v" \
            ENVIRON["PREVIOUS"] "...v" ENVIRON["VERSION"]
          next
        }
        { print }
      ' "$CHANGELOG" > "$tmp"
      mv "$tmp" "$CHANGELOG"
    fi
  fi

  echo "changelog.sh: [Unreleased] → [$version] - $today"
  echo "changelog.sh: pom.xml muss ggf. noch auf $version gesetzt werden"
}

case "${1:-}" in
  check)      shift; cmd_check "$@" ;;
  unreleased) shift; cmd_unreleased "$@" ;;
  release)    shift; cmd_release "$@" ;;
  version)    pom_version ;;
  -h|--help|help|"") usage ;;
  *)          die "unbekannter Befehl '${1}' — siehe --help" ;;
esac
