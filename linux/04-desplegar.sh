#!/usr/bin/env bash
# PASO 4. Construye el backend, levanta Postgres + Ollama + backend, descarga los modelos de IA y espera a que todo responda.
# La primera vez tarda 15–30 min (compilación + descarga de modelos). Es repetible: sirve también para actualizar.
# Uso:  ./04-desplegar.sh              despliega con el código que ya está en el servidor
#       ./04-desplegar.sh --actualizar hace `git pull` antes (para actualizar a una versión nueva)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_env
docker_cmd info >/dev/null 2>&1 || sudo docker info >/dev/null 2>&1 || die "Docker no responde. ¿Corriste 01-instalar-docker.sh?"

if [ "${1:-}" = "--actualizar" ]; then
    msg "Actualizando el código (git pull)"
    git -C "$REPO" pull --ff-only
fi

msg "Construyendo la imagen del backend (la primera vez tarda varios minutos y usa bastante memoria)"
compose build backend

msg "Levantando contenedores"
compose up -d

msg "Esperando al backend (arranca, aplica las migraciones y abre el puerto)…"
if wait_backend 420; then
    msg "Backend respondiendo."
else
    warn "El backend no respondió en 7 minutos. Revisa:  docker logs --tail 80 sportacus-backend"
    exit 1
fi

IP="$(lan_ip)"; PORT="$(backend_port)"
echo
compose ps
echo
msg "Despliegue listo"
echo "    Red local:  http://$IP:$PORT"
echo "    Modelos de IA: se descargan en segundo plano (compose logs -f ollama-modelos). Cuando terminen,"
echo "    el backend indexa la memoria del asistente solo; ./estado.sh muestra el avance."
echo
echo "Siguientes pasos opcionales:"
echo "    ./06-restaurar-datos.sh archivo.dump   cargar los usuarios de tu base actual"
echo "    sudo ./05-instalar-servicios.sh        que todo arranque solo al encender el servidor"
