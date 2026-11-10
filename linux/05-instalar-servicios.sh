#!/usr/bin/env bash
# PASO 5. Arranque automático: al encender el servidor se levantan solos los contenedores y el túnel de Cloudflare.
# Instala dos servicios de systemd y un respaldo diario de la base de datos (3:00 a. m.).
# Uso:  sudo ./05-instalar-servicios.sh        (para quitarlos:  sudo ./05-instalar-servicios.sh --quitar)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ "$(id -u)" -eq 0 ] || die "Ejecuta con sudo:  sudo ./05-instalar-servicios.sh"
USER_NAME="$(real_user)"

if [ "${1:-}" = "--quitar" ]; then
    systemctl disable --now sportacus-tunel.service sportacus.service 2>/dev/null || true
    rm -f /etc/systemd/system/sportacus.service /etc/systemd/system/sportacus-tunel.service /etc/cron.d/sportacus-respaldo
    systemctl daemon-reload
    msg "Servicios quitados."
    exit 0
fi

[ -f "$ENV_FILE" ] || die "Falta linux/.env. Ejecuta primero ./03-configurar.sh"
PORT="$(backend_port)"

msg "Instalando cloudflared"
sudo -u "$USER_NAME" bash "$DIR/tunel.sh" instalar

msg "Creando servicio sportacus (contenedores)"
cat > /etc/systemd/system/sportacus.service <<UNIT
[Unit]
Description=Sportacus - Postgres, Ollama y backend (Docker Compose)
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$DIR
ExecStart=/usr/bin/docker compose --env-file $ENV_FILE -f $COMPOSE_FILE up -d
ExecStop=/usr/bin/docker compose --env-file $ENV_FILE -f $COMPOSE_FILE stop
TimeoutStartSec=600
TimeoutStopSec=120

[Install]
WantedBy=multi-user.target
UNIT

msg "Creando servicio sportacus-tunel (Cloudflare Tunnel)"
cat > /etc/systemd/system/sportacus-tunel.service <<UNIT
[Unit]
Description=Sportacus - Cloudflare Tunnel
After=sportacus.service network-online.target
Wants=network-online.target

[Service]
User=$USER_NAME
WorkingDirectory=$DIR
# Espera hasta 6 min a que el backend responda antes de abrir el túnel.
ExecStartPre=/bin/bash -c 'for i in \$(seq 1 120); do curl -fs http://localhost:$PORT/api/legal/documents >/dev/null && exit 0; sleep 3; done; exit 0'
ExecStart=/bin/bash $DIR/tunel.sh ejecutar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT

msg "Programando el respaldo diario de la base de datos"
cat > /etc/cron.d/sportacus-respaldo <<CRON
0 3 * * * $USER_NAME /bin/bash $DIR/respaldo.sh >> $DIR/backups/respaldo.log 2>&1
CRON
chmod 644 /etc/cron.d/sportacus-respaldo

systemctl daemon-reload
systemctl enable sportacus.service sportacus-tunel.service
systemctl restart sportacus.service
systemctl restart sportacus-tunel.service

msg "Listo: desde ahora, al encender el servidor todo arranca solo (tarda 2–3 min)."
echo "    Ver la URL pública:  ./url.sh      Ver el estado:  ./estado.sh"
