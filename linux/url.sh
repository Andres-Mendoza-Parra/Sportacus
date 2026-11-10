#!/usr/bin/env bash
# Muestra las direcciones para conectar la app: la de la red local y la URL pública del túnel de Cloudflare.
# Uso:  ./url.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

IP="$(lan_ip)"; PORT="$(backend_port)"
echo "Red local (mismo WiFi/LAN):  http://${IP:-?}:$PORT"
if [ -s "$URL_FILE" ]; then
    URL="$(cat "$URL_FILE")"
    echo "Internet (Cloudflare):       $URL"
    if curl -fs -m 8 "$URL/api/legal/documents" >/dev/null 2>&1; then
        echo "                             ✔ responde desde internet"
    else
        echo "                             (el túnel aún no responde; espera unos segundos y repite)"
    fi
else
    echo "Internet (Cloudflare):       sin túnel activo (./iniciar.sh o sudo systemctl start sportacus-tunel)"
fi
echo
echo "En la app, el backend va en la URL + /api  (ej. ${URL:-https://…trycloudflare.com}/api)"
echo "Recuerda: la URL de trycloudflare.com cambia cada vez que se reinicia el túnel."
