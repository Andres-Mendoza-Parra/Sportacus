#!/usr/bin/env bash
# Arranque manual de todo el servidor (si no instalaste el arranque automático del paso 5, o tras un ./detener.sh).
# Levanta los contenedores y abre el túnel de Cloudflare, y muestra las direcciones.
# Uso:  ./iniciar.sh            (con --sin-tunel solo levanta los contenedores: acceso solo en la red local)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_env

msg "Levantando contenedores (Postgres, Ollama y backend)"
compose up -d

msg "Esperando al backend…"
wait_backend 420 || die "El backend no respondió. Revisa:  docker logs --tail 80 sportacus-backend"

if [ "${1:-}" != "--sin-tunel" ]; then
    if systemctl list-unit-files sportacus-tunel.service >/dev/null 2>&1 \
        && systemctl cat sportacus-tunel.service >/dev/null 2>&1; then
        msg "Iniciando el túnel (servicio sportacus-tunel)"
        sudo systemctl restart sportacus-tunel.service
    else
        msg "Iniciando el túnel en segundo plano"
        [ -f "$DIR/.tunel.pid" ] && kill "$(cat "$DIR/.tunel.pid")" 2>/dev/null || true
        nohup bash "$DIR/tunel.sh" ejecutar > "$DIR/tunel.log" 2>&1 &
        echo $! > "$DIR/.tunel.pid"
    fi
    for _ in $(seq 1 30); do [ -s "$URL_FILE" ] && break; sleep 2; done
fi

echo
bash "$DIR/url.sh"
