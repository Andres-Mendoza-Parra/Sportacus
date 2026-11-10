#!/usr/bin/env bash
# Cierre ordenado del servidor: cierra el túnel y detiene los contenedores (los datos se conservan en los volúmenes).
# Uso:  ./detener.sh                  detiene Sportacus
#       ./detener.sh --apagar-equipo  además apaga el equipo (para quitarle la corriente sin riesgo para la base de datos)
# Nota: si instalaste el arranque automático, Sportacus volverá a levantarse solo la próxima vez que enciendas el equipo.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

if systemctl cat sportacus-tunel.service >/dev/null 2>&1; then
    msg "Cerrando el túnel"
    sudo systemctl stop sportacus-tunel.service || true
fi
if [ -f "$DIR/.tunel.pid" ]; then
    kill "$(cat "$DIR/.tunel.pid")" 2>/dev/null || true
    rm -f "$DIR/.tunel.pid"
fi
pkill -f "cloudflared tunnel" 2>/dev/null || true
rm -f "$URL_FILE"

msg "Deteniendo contenedores (Postgres cierra limpio)"
compose stop

msg "Sportacus detenido. Los datos siguen guardados."
if [ "${1:-}" = "--apagar-equipo" ]; then
    msg "Apagando el equipo…"
    sudo poweroff
fi
