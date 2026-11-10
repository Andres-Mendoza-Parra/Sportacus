#!/usr/bin/env bash
# PASO 1 (una sola vez). Prepara el servidor Ubuntu: instala Docker y Compose, lo deja listo para usarse sin sudo,
# asegura swap para compilar con poca RAM y evita que el equipo se suspenda (es un portátil: tapa cerrada = sigue encendido).
# Uso:  sudo ./01-instalar-docker.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ "$(id -u)" -eq 0 ] || die "Ejecuta con sudo:  sudo ./01-instalar-docker.sh"
USER_NAME="$(real_user)"

msg "Actualizando paquetes e instalando herramientas básicas"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg git ufw openssl

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    msg "Docker ya está instalado: $(docker --version)"
else
    msg "Instalando Docker Engine y Compose desde el repositorio oficial"
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    . /etc/os-release
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" \
        > /etc/apt/sources.list.d/docker.list
    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker

if ! id -nG "$USER_NAME" | tr ' ' '\n' | grep -qx docker; then
    usermod -aG docker "$USER_NAME"
    msg "Usuario $USER_NAME añadido al grupo docker (se aplica al volver a iniciar sesión)"
fi

# Rotación de logs de Docker: sin esto, los logs pueden llenar el disco con los meses.
mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
    cat > /etc/docker/daemon.json <<'JSON'
{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
JSON
    systemctl restart docker
fi

# Swap: compilar el backend con Maven necesita memoria extra en un equipo de ~3 GB.
SWAP_MB=$(awk '/SwapTotal/ {print int($2/1024)}' /proc/meminfo)
if [ "$SWAP_MB" -lt 2000 ]; then
    msg "Creando /swapfile de 2 GB (swap actual: ${SWAP_MB} MB)"
    fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
else
    msg "Swap suficiente (${SWAP_MB} MB)"
fi
echo 'vm.swappiness=20' > /etc/sysctl.d/99-sportacus.conf && sysctl -q --system >/dev/null

# Modo servidor: no suspender ni al cerrar la tapa ni por inactividad.
msg "Desactivando la suspensión del equipo"
mkdir -p /etc/systemd/logind.conf.d
cat > /etc/systemd/logind.conf.d/sportacus.conf <<'CONF'
[Login]
HandleLidSwitch=ignore
HandleLidSwitchExternalPower=ignore
HandleLidSwitchDocked=ignore
IdleAction=ignore
CONF
systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target >/dev/null 2>&1 || true
systemctl kill -s HUP systemd-logind >/dev/null 2>&1 || true

msg "Listo. Docker: $(docker --version)"
echo
echo "Siguiente: cierra la sesión SSH y vuelve a entrar (para usar docker sin sudo) y ejecuta ./02-red.sh"
