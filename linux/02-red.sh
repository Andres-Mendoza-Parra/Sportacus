#!/usr/bin/env bash
# PASO 2 (una sola vez). Configuración de red para el acceso desde la red local (misma WiFi/LAN):
#   · firewall (ufw): solo SSH y el puerto del backend; la base de datos y la IA no se exponen
#   · IP fija opcional (netplan), para que la dirección del servidor no cambie al reiniciar el router
# Uso:  sudo ./02-red.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ "$(id -u)" -eq 0 ] || die "Ejecuta con sudo:  sudo ./02-red.sh"
PORT="$(backend_port)"
[ -f "$ENV_FILE" ] || PORT=8082

IFACE="$(ip route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="dev"){print $(i+1); exit}}')"
IP="$(lan_ip)"
GATEWAY="$(ip route | awk '/default/ {print $3; exit}')"
PREFIX="$(ip -o -4 addr show dev "$IFACE" | awk '{print $4}' | head -1 | cut -d/ -f2)"
[ -n "$IFACE" ] && [ -n "$IP" ] || die "No se detectó la red. ¿Hay cable o WiFi conectado?"

msg "Interfaz: $IFACE · IP actual: $IP/$PREFIX · puerta de enlace: $GATEWAY"

# ── Firewall: se permite SSH ANTES de activarlo para no quedarte afuera
msg "Configurando el firewall (ufw)"
ufw allow OpenSSH >/dev/null
ufw allow "${PORT}/tcp" comment 'Sportacus backend' >/dev/null
ufw --force enable >/dev/null
ufw status | sed 's/^/    /'

# ── IP fija (opcional)
if [ -d "/sys/class/net/$IFACE/wireless" ]; then
    warn "La conexión es WiFi: no se configura IP fija automáticamente."
    warn "Recomendado: reserva la IP $IP para este equipo en tu router (reserva DHCP por dirección MAC)."
else
    read -r -p "¿Fijar la IP $IP en este servidor? (s/N) " ANS
    if [[ "${ANS,,}" == "s" ]]; then
        FILE=/etc/netplan/60-sportacus-ip-fija.yaml
        cat > "$FILE" <<YAML
network:
  version: 2
  ethernets:
    $IFACE:
      dhcp4: false
      addresses: [$IP/$PREFIX]
      routes:
        - to: default
          via: $GATEWAY
      nameservers:
        addresses: [1.1.1.1, 8.8.8.8]
YAML
        chmod 600 "$FILE"
        netplan generate
        netplan apply
        msg "IP fija aplicada ($IP). Para volver a DHCP: sudo rm $FILE && sudo netplan apply"
    fi
fi

echo
msg "Dirección del servidor en la red local:  http://$IP:$PORT"
echo "    Prueba desde otro equipo de la misma red:  http://$IP:$PORT/api/legal/documents"
echo
echo "Siguiente: ./03-configurar.sh"
