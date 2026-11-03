import { Platform } from "react-native";

/**
 * Configuración de conexión al backend.
 *
 * - En WEB (navegador en la misma PC donde corre el backend): usa "localhost"
 *   automáticamente, no necesitas tocar nada.
 * - En celular físico con Expo Go o en el emulador de Android Studio:
 *   "localhost" no sirve (el dispositivo no es la misma máquina), así que
 *   debes poner la IP local de tu PC en LOCAL_IP. Verla en Windows con
 *   `ipconfig` → "Dirección IPv4" del adaptador Wi-Fi/Ethernet
 *   (ej: 192.168.1.50). Para el emulador de Android Studio puedes usar
 *   10.0.2.2 en su lugar.
 */
const LOCAL_IP = "192.168.1.37"; // IP de tu PC en el Wi-Fi (ipconfig → IPv4). Cámbiala si tu red asigna otra.

const HOST = Platform.OS === "web" ? "localhost" : LOCAL_IP;

export const API_BASE_URL = `http://${HOST}:8082/api`;

/** Webhook del chat de n8n (workflow con Google Gemini). Corre en Docker, puerto 5678. */
export const N8N_CHAT_URL = `http://${HOST}:5678/webhook/e445d7ac-0bd7-43af-8add-147cf1e6c55b/chat`;

export const AUTH_TOKEN_KEY = "sportacus_auth_token";
export const AUTH_USER_KEY = "sportacus_auth_user";
