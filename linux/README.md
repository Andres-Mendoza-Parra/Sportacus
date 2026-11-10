# Sportacus en el servidor Ubuntu (carpeta `linux/`)

Todo lo necesario para dejar el servidor funcionando: Docker, Postgres + pgvector, Ollama (IA local, sin GPU), el backend
dockerizado y el túnel de Cloudflare para publicarlo en internet. No se usa n8n. Esta carpeta no modifica nada de
`backend/` ni `frontend/`.

Pensado para el equipo actual: **3 GB de RAM, sin GPU, 98 GB de disco**. Por eso el modelo de chat por defecto es
`qwen2.5:1.5b` (el `3b` no cabe bien).

## Qué hay aquí
| Archivo | Para qué |
|---|---|
| `01-instalar-docker.sh` | Instala Docker, swap y evita que el portátil se suspenda |
| `02-red.sh` | Firewall e IP fija para la red local |
| `03-configurar.sh` | Crea `.env` con contraseñas y claves aleatorias |
| `04-desplegar.sh` | Compila el backend, levanta todo y descarga los modelos |
| `05-instalar-servicios.sh` | Arranque automático al encender + respaldo diario |
| `06-restaurar-datos.sh` | Carga tus usuarios actuales desde un `.dump` (opcional) |
| `iniciar.sh` / `detener.sh` | Arranque y cierre manual y ordenado |
| `tunel.sh` | Instala y abre el túnel de Cloudflare (lo usan los servicios) |
| `url.sh` / `estado.sh` | Ver direcciones y estado del servidor |
| `respaldo.sh` | Respaldo de la base de datos en `backups/` (guarda los últimos 14) |
| `docker-compose.yml`, `Dockerfile.backend`, `postgres-init/` | Contenedores y su imagen |

## Instalación inicial (una sola vez)
Desde tu PC: `ssh user38@IP-DEL-SERVIDOR`. Ya en el servidor:

```bash
git clone -b Sportacus_v2 https://github.com/Andres-Mendoza-Parra/Sportacus.git
cd Sportacus/linux
chmod +x *.sh

sudo ./01-instalar-docker.sh     # luego cierra la sesión SSH y vuelve a entrar
sudo ./02-red.sh
./03-configurar.sh               # pregunta correos de administración y modelo (Enter = recomendado)
./04-desplegar.sh                # 15–30 min la primera vez
sudo ./05-instalar-servicios.sh  # arranque automático + túnel
./url.sh                         # muestra la URL pública
```

**Guarda una copia de `linux/.env` fuera del servidor.** Si se pierde `SPORTACUS_DATA_KEY`, los datos de salud cifrados no
se pueden recuperar.

## Cada vez que enciendes el servidor
**Con el paso 5 instalado: nada.** Enciéndelo, espera 2–3 minutos y listo. Los contenedores y el túnel arrancan solos.
Para saber la URL pública de hoy (cambia en cada arranque):

```bash
ssh user38@IP-DEL-SERVIDOR
cd ~/Sportacus/linux && ./url.sh
```

Sin el paso 5, después de encender: `cd ~/Sportacus/linux && ./iniciar.sh`.

Para apagar sin riesgo para la base de datos: `./detener.sh --apagar-equipo`.

## Direcciones
- **Red local (misma WiFi/LAN):** `http://IP-DEL-SERVIDOR:8082` (la IP la muestra `./url.sh`).
- **Internet:** `https://algo-aleatorio.trycloudflare.com` (Quick Tunnel de Cloudflare: gratis, sin dominio ni cuenta).
  - La URL **cambia cada vez que se reinicia el túnel** (cada encendido). Hay que ponerla en la app, con `/api` al final.
  - Cuando haya dominio: crea un túnel con nombre en el panel de Cloudflare Zero Trust, pega `CLOUDFLARE_TUNNEL_TOKEN` y
    `CLOUDFLARE_PUBLIC_URL` en `.env` y reinicia el servicio (`sudo systemctl restart sportacus-tunel`). La URL será fija.

## Llevar tus datos al servidor
En tu PC (con el contenedor `sportacus-postgres` corriendo):

```powershell
docker exec sportacus-postgres pg_dump -U sportacus_app -d sportacus -Fc --no-owner -f /backups/para-servidor.dump
scp docker\backups\para-servidor.dump user38@IP-DEL-SERVIDOR:~/Sportacus/linux/backups/
```

En el servidor: `./06-restaurar-datos.sh backups/para-servidor.dump`

## Actualizar a una versión nueva
```bash
cd ~/Sportacus/linux && ./04-desplegar.sh --actualizar
```

## Día a día
| Quiero… | Comando |
|---|---|
| Ver el estado (contenedores, RAM, IA, URL) | `./estado.sh` |
| Ver la URL pública | `./url.sh` |
| Ver qué responde el backend | `docker logs -f sportacus-backend` |
| Ver las peticiones al modelo de IA | `docker logs -f sportacus-ollama` |
| Hacer un respaldo ahora | `./respaldo.sh` |
| Parar / arrancar manualmente | `./detener.sh` / `./iniciar.sh` |
| Quitar el arranque automático | `sudo ./05-instalar-servicios.sh --quitar` |

## Seguridad
- Postgres solo es accesible desde el propio servidor (`127.0.0.1`); Ollama no publica ningún puerto.
- Solo se abren SSH y el puerto 8082 en el firewall. El túnel no necesita abrir nada en el router.
- El backend corre con el perfil `prod` y no arranca sin `JWT_SECRET` y `SPORTACUS_DATA_KEY`.

## Limitaciones conocidas
- La disponibilidad depende de que el servidor esté encendido y con internet. Sin redundancia.
- Quick Tunnel no ofrece garantía de disponibilidad (es para pruebas y demos) y su URL cambia.
- Sin GPU, el asistente tarda entre 10 y 30 segundos por respuesta (la primera, más, mientras carga el modelo).
- Si la app móvil usa una IP o URL fija, hay que actualizarla cuando cambie la URL del túnel.
