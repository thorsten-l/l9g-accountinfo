#!/usr/bin/env bash
#
# Updates the CHECK constraint of the "secretdata.type" column so it accepts
# all SdbSecretType enum values (including the EXT_* types).
#
# Background: the column is mapped via @Enumerated(EnumType.STRING), so
# Hibernate creates a varchar column guarded by a CHECK constraint. With
# ddl-auto=update that constraint is created once but never updated, so new
# enum constants must be added to it manually.
#
# Runs psql inside the dockerized Postgres container.
#
set -euo pipefail

# --- configuration (override via environment if needed) ----------------------
CONTAINER="${CONTAINER:-signaturedb}"
DB_USER="${DB_USER:-signaturedb}"
DB_NAME="${DB_NAME:-signaturedb}"

# All current SdbSecretType values. Keep in sync with SdbSecretType.java.
read -r -d '' TYPE_VALUES <<'SQL' || true
    'SIGNATURE_PAD_JSON',
    'ID_SIGNATURE_JWT',
    'ID_FRONT_IMAGE',
    'ID_BACK_IMAGE',
    'EXT_IDENTIFICATION_STATUS',
    'EXT_IDENTIFICATION_ARCHIVE'
SQL
# -----------------------------------------------------------------------------

echo ">> Container : ${CONTAINER}"
echo ">> Database  : ${DB_NAME} (user ${DB_USER})"

# Sanity check: container running?
if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "ERROR: container '${CONTAINER}' is not running." >&2
  exit 1
fi

# Build the SQL. A DO block drops every CHECK constraint on the secretdata
# table that references the "type" column (regardless of its generated name),
# then we add a fresh one with the full value list.
SQL_SCRIPT=$(cat <<SQL
DO \$\$
DECLARE
  c text;
BEGIN
  FOR c IN
    SELECT conname
    FROM pg_constraint
    WHERE conrelid = 'secretdata'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%type%'
  LOOP
    EXECUTE format('ALTER TABLE secretdata DROP CONSTRAINT %I', c);
    RAISE NOTICE 'dropped constraint %', c;
  END LOOP;
END
\$\$;

ALTER TABLE secretdata ADD CONSTRAINT secretdata_type_check
  CHECK (type IN (
${TYPE_VALUES}
  ));
SQL
)

echo ">> Applying constraint update ..."
echo "${SQL_SCRIPT}" | docker exec -i "${CONTAINER}" \
  psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}"

echo ">> Done. Current constraint definition:"
docker exec -i "${CONTAINER}" \
  psql -U "${DB_USER}" -d "${DB_NAME}" -c \
  "SELECT conname, pg_get_constraintdef(oid)
     FROM pg_constraint
    WHERE conrelid = 'secretdata'::regclass AND contype = 'c';"
