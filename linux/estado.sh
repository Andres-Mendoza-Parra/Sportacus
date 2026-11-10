#!/usr/bin/env bash
# Estado del servidor: contenedores, memoria, disco, asistente de IA y direcciones.
# Uso:  ./estado.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_env

echo "── Contenedores"
compose ps
echo
echo "── Memoria y disco"
free -h | sed -n '1,2p'
df -h / | tail -1 | awk '{print "disco: " $3 " usados de " $2 " (" $5 ")"}'
echo
echo "── Asistente de IA"
PORT="$(backend_port)"
curl -fs -m 5 "http://localhost:$PORT/api/chat/status" || echo "(el backend no responde)"
echo
docker_cmd exec sportacus-ollama ollama ps 2>/dev/null || true
echo
echo "── Servicios de arranque automático"
for s in sportacus sportacus-tunel; do
    printf '%s: %s\n' "$s" "$(systemctl is-active $s 2>/dev/null || echo 'no instalado')"
done
echo
bash "$DIR/url.sh"
