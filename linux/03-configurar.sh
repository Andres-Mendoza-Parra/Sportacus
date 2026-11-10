#!/usr/bin/env bash
# PASO 3 (una sola vez). Crea linux/.env con secretos aleatorios (contraseñas, clave JWT y clave de cifrado de salud).
# Si el .env ya existe NO lo sobrescribe: perder SPORTACUS_DATA_KEY dejaría ilegibles los datos de salud cifrados.
# Uso:  ./03-configurar.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

if [ -f "$ENV_FILE" ]; then
    msg "Ya existe linux/.env: no se modifica. (Para rehacerlo, renómbralo primero y guarda una copia.)"
    exit 0
fi
command -v openssl >/dev/null || die "Falta openssl (sudo apt install openssl)"

echo "Correos de administración (ven el resumen de analítica y pueden reindexar el asistente)."
read -r -p "Correos separados por comas (Enter para ninguno): " ADMINS

echo
echo "Modelo de chat para este servidor (3 GB de RAM, sin GPU):"
echo "  1) qwen2.5:1.5b  — recomendado, ~1,2 GB, respuestas en 10–30 s"
echo "  2) qwen2.5:0.5b  — mínimo, muy rápido pero de menor calidad"
echo "  3) qwen2.5:3b    — mejor calidad, pero no cabe bien en 3 GB (usará el swap y será lento)"
read -r -p "Elige 1, 2 o 3 [1]: " M
case "${M:-1}" in 2) MODEL="qwen2.5:0.5b" ;; 3) MODEL="qwen2.5:3b" ;; *) MODEL="qwen2.5:1.5b" ;; esac

cat > "$ENV_FILE" <<EOF
# Generado por 03-configurar.sh el $(date '+%Y-%m-%d %H:%M'). NO subir a git. Guarda una copia en un lugar seguro.
POSTGRES_SUPERUSER_PASSWORD=$(openssl rand -hex 20)
SPORTACUS_DB=sportacus
DB_USER=sportacus_app
DB_PASSWORD=$(openssl rand -hex 20)
POSTGRES_PORT=5433

# Seguridad de la aplicación (el perfil prod no arranca sin estas dos).
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
SPORTACUS_DATA_KEY=$(openssl rand -base64 32 | tr -d '\n')
SPORTACUS_ADMIN_EMAILS=${ADMINS// /}

# Asistente de IA local
RAG_CHAT_MODEL=$MODEL
RAG_EMBEDDING_MODEL=nomic-embed-text
RAG_NUM_CTX=4096
RAG_TIMEOUT_SECONDS=240
RAG_KEEP_ALIVE=10m

# Red
BACKEND_PORT=8082

# Cloudflare Tunnel. Vacío = Quick Tunnel gratis (URL aleatoria trycloudflare.com que cambia en cada arranque).
# Con dominio propio: crea un túnel con nombre en el panel de Cloudflare y pega aquí su token y la URL fija.
CLOUDFLARE_TUNNEL_TOKEN=
CLOUDFLARE_PUBLIC_URL=
EOF
chmod 600 "$ENV_FILE"

msg "Creado linux/.env (permisos 600) con secretos aleatorios."
warn "Guarda una copia del .env fuera del servidor: si pierdes SPORTACUS_DATA_KEY, los datos de salud cifrados no se pueden recuperar."
echo
echo "Siguiente: ./04-desplegar.sh"
