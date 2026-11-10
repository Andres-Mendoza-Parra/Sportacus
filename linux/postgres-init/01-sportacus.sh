#!/bin/sh
# Se ejecuta UNA sola vez, cuando el volumen de datos está vacío (primer arranque del contenedor).
# Crea el usuario y la base del backend, y la extensión pgvector dentro del esquema sportacus_v2
# (el backend usa search_path = sportacus_v2, así el tipo "vector" se resuelve sin calificar).
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE "$SPORTACUS_DB_USER" LOGIN PASSWORD '$SPORTACUS_DB_PASSWORD';
CREATE DATABASE "$SPORTACUS_DB" OWNER "$SPORTACUS_DB_USER" ENCODING 'UTF8' TEMPLATE template0;
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$SPORTACUS_DB" <<SQL
CREATE SCHEMA IF NOT EXISTS sportacus_v2 AUTHORIZATION "$SPORTACUS_DB_USER";
CREATE EXTENSION IF NOT EXISTS vector SCHEMA sportacus_v2;
SQL

echo "Sportacus: base '$SPORTACUS_DB', usuario '$SPORTACUS_DB_USER' y pgvector listos"
