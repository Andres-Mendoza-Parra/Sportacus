#!/usr/bin/env bash
# Respaldo de la base de datos en linux/backups/ (formato .dump). Conserva los últimos 14. Lo ejecuta el cron a las 3:00 a. m.
# Uso manual:  ./respaldo.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_env

DB="$(env_get SPORTACUS_DB)"; DB="${DB:-sportacus}"
DBUSER="$(env_get DB_USER)"; DBUSER="${DBUSER:-sportacus_app}"
OUT="sportacus-$(date +%Y%m%d-%H%M).dump"

docker_cmd ps --format '{{.Names}}' | grep -qx sportacus-postgres || die "Postgres no está corriendo"
docker_cmd exec sportacus-postgres pg_dump -U "$DBUSER" -d "$DB" -Fc --no-owner -f "/backups/$OUT"
msg "Respaldo creado: linux/backups/$OUT ($(du -h "$DIR/backups/$OUT" | cut -f1))"

# Rotación: se borran los respaldos antiguos, dejando los 14 más recientes.
ls -1t "$DIR"/backups/sportacus-*.dump 2>/dev/null | tail -n +15 | xargs -r rm -f
