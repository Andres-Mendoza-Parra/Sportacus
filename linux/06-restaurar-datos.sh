#!/usr/bin/env bash
# PASO 6 (opcional). Carga en el servidor un respaldo de la base de datos (.dump, formato pg_dump -Fc) para llevarte
# los usuarios y planes existentes. REEMPLAZA los datos que haya en el servidor, así que pide confirmación.
# Uso:  ./06-restaurar-datos.sh ruta/al/archivo.dump
# Cómo obtener el .dump en tu PC y copiarlo al servidor: ver README.md ("Llevar tus datos al servidor").
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_env

FILE="${1:-}"
[ -n "$FILE" ] && [ -f "$FILE" ] || die "Uso: ./06-restaurar-datos.sh archivo.dump"
DB="$(env_get SPORTACUS_DB)"; DB="${DB:-sportacus}"
DBUSER="$(env_get DB_USER)"; DBUSER="${DBUSER:-sportacus_app}"

docker_cmd ps --format '{{.Names}}' | grep -qx sportacus-postgres || die "El contenedor de Postgres no está corriendo (./iniciar.sh)"

read -r -p "Esto REEMPLAZA la base del servidor con $(basename "$FILE"). ¿Continuar? (escribe: si) " ANS
[ "$ANS" = "si" ] || { echo "Cancelado"; exit 1; }

# Respaldo de seguridad de lo que haya ahora
"$DIR/respaldo.sh" || warn "No se pudo hacer el respaldo previo (¿base vacía?). Continúo."

NAME="restaurar-$(date +%s).dump"
cp "$FILE" "$DIR/backups/$NAME"

msg "Deteniendo el backend"
compose stop backend

msg "Limpiando la base del servidor (se conserva pgvector)"
docker_cmd exec -i sportacus-postgres psql -q -U postgres -d "$DB" -v ON_ERROR_STOP=1 <<'SQL'
DROP SCHEMA IF EXISTS sportacus CASCADE;
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT format('%I.%I', schemaname, tablename) AS t FROM pg_tables WHERE schemaname = 'sportacus_v2' LOOP
    EXECUTE 'DROP TABLE IF EXISTS ' || r.t || ' CASCADE';
  END LOOP;
  FOR r IN SELECT p.oid::regprocedure AS f FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
           WHERE n.nspname = 'sportacus_v2'
             AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = p.oid AND d.deptype = 'e') LOOP
    EXECUTE 'DROP FUNCTION IF EXISTS ' || r.f || ' CASCADE';
  END LOOP;
  FOR r IN SELECT format('%I.%I', sequence_schema, sequence_name) AS s FROM information_schema.sequences
           WHERE sequence_schema = 'sportacus_v2' LOOP
    EXECUTE 'DROP SEQUENCE IF EXISTS ' || r.s || ' CASCADE';
  END LOOP;
END $$;
SQL

msg "Restaurando $(basename "$FILE")"
LOG="$(docker_cmd exec sportacus-postgres pg_restore -U postgres -d "$DB" --no-owner --role="$DBUSER" "/backups/$NAME" 2>&1 || true)"
# Único error esperado: el esquema sportacus_v2 ya existe (lo creó el init junto con pgvector).
if echo "$LOG" | grep 'ERROR:' | grep -v 'schema "sportacus_v2" already exists' | grep -q .; then
    echo "$LOG"; die "pg_restore reportó errores inesperados"
fi
docker_cmd exec sportacus-postgres psql -q -U postgres -d "$DB" \
    -c "ALTER SCHEMA sportacus OWNER TO $DBUSER; ALTER SCHEMA sportacus_v2 OWNER TO $DBUSER;" >/dev/null 2>&1 || true
rm -f "$DIR/backups/$NAME"

msg "Arrancando el backend (aplica las migraciones pendientes solo)"
compose start backend
wait_backend 300 || die "El backend no arrancó: docker logs --tail 80 sportacus-backend"

USERS="$(docker_cmd exec sportacus-postgres psql -U "$DBUSER" -d "$DB" -tAc 'SELECT count(*) FROM sportacus_v2.app_user')"
msg "Restauración completa: $USERS usuarios en el servidor."
