#!/usr/bin/env bash
# Funciones comunes de los scripts de linux/. No se ejecuta directamente: los demás scripts la cargan con `source`.

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(dirname "$DIR")"
ENV_FILE="$DIR/.env"
COMPOSE_FILE="$DIR/docker-compose.yml"
URL_FILE="$DIR/URL_PUBLICA.txt"

msg()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; exit 1; }

# Usuario "real" aunque el script corra con sudo.
real_user() { echo "${SUDO_USER:-$USER}"; }

# docker sin sudo si el usuario ya está en el grupo docker; con sudo si no (justo después de instalar).
docker_cmd() {
    if docker info >/dev/null 2>&1; then docker "$@"; else sudo docker "$@"; fi
}

need_env() { [ -f "$ENV_FILE" ] || die "Falta linux/.env. Ejecuta primero ./03-configurar.sh"; }

compose() {
    need_env
    docker_cmd compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

# Lee una variable del .env (vacío si no existe).
env_get() { grep -E "^$1=" "$ENV_FILE" 2>/dev/null | head -1 | cut -d= -f2-; }

backend_port() { local p; p="$(env_get BACKEND_PORT)"; echo "${p:-8082}"; }

# IP de la red local de este servidor.
lan_ip() { ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src"){print $(i+1); exit}}'; }

# Espera a que el backend responda (máx. $1 segundos, 300 por defecto).
wait_backend() {
    local max="${1:-300}" i=0 port; port="$(backend_port)"
    while [ "$i" -lt "$max" ]; do
        curl -fs "http://localhost:$port/api/legal/documents" >/dev/null 2>&1 && return 0
        sleep 3; i=$((i + 3))
    done
    return 1
}
