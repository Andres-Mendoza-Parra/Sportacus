#!/usr/bin/env bash
# Cloudflare Tunnel: publica el backend en internet con HTTPS, sin abrir puertos del router ni IP pública.
#   ./tunel.sh instalar    instala cloudflared (una vez; lo hace 05-instalar-servicios.sh)
#   ./tunel.sh ejecutar    abre el túnel y deja la URL pública en linux/URL_PUBLICA.txt (lo lanza systemd o iniciar.sh)
#
# Modo gratis y sin dominio ("Quick Tunnel"): CLOUDFLARE_TUNNEL_TOKEN vacío en el .env. La URL es aleatoria
# (https://algo.trycloudflare.com) y CAMBIA cada vez que el túnel se reinicia.
# Modo URL fija (requiere dominio en Cloudflare): pon CLOUDFLARE_TUNNEL_TOKEN y CLOUDFLARE_PUBLIC_URL en el .env.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

instalar() {
    if command -v cloudflared >/dev/null 2>&1; then
        msg "cloudflared ya instalado: $(cloudflared --version | head -1)"
        return 0
    fi
    local arch deb
    arch="$(dpkg --print-architecture)"            # amd64 | arm64
    deb="$(mktemp --suffix=.deb)"
    msg "Descargando cloudflared ($arch)"
    curl -fL -o "$deb" "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${arch}.deb" \
        || die "No se pudo descargar cloudflared (¿hay internet?)"
    sudo dpkg -i "$deb" || die "No se pudo instalar cloudflared"
    rm -f "$deb"
    msg "cloudflared instalado: $(cloudflared --version | head -1)"
}

ejecutar() {
    command -v cloudflared >/dev/null 2>&1 || die "Falta cloudflared: ./tunel.sh instalar"
    [ -f "$ENV_FILE" ] || die "Falta linux/.env"
    local port token fixed
    port="$(backend_port)"; token="$(env_get CLOUDFLARE_TUNNEL_TOKEN)"; fixed="$(env_get CLOUDFLARE_PUBLIC_URL)"
    rm -f "$URL_FILE"

    if [ -n "$token" ]; then
        msg "Túnel con nombre (URL fija)"
        [ -n "$fixed" ] && echo "$fixed" > "$URL_FILE"
        exec cloudflared tunnel --no-autoupdate run --token "$token"
    fi

    msg "Quick Tunnel hacia http://localhost:$port (la URL pública aparecerá en unos segundos)"
    cloudflared tunnel --no-autoupdate --url "http://localhost:$port" 2>&1 | while IFS= read -r line; do
        echo "$line"
        if [[ "$line" =~ (https://[a-z0-9-]+\.trycloudflare\.com) ]]; then
            echo "${BASH_REMATCH[1]}" > "$URL_FILE"
            echo ">>> URL PÚBLICA: ${BASH_REMATCH[1]}"
        fi
    done
}

case "${1:-}" in
    instalar) instalar ;;
    ejecutar) ejecutar ;;
    *) echo "Uso: $0 instalar | ejecutar"; exit 1 ;;
esac
