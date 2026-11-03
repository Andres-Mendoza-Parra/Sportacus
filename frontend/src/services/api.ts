import axios from "axios";
import AsyncStorage from "@react-native-async-storage/async-storage";

import { API_BASE_URL, AUTH_TOKEN_KEY } from "../constants/config";

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
  },
});

// Adjunta el token JWT (si existe) a cada request saliente.
api.interceptors.request.use(async (config) => {
  const token = await AsyncStorage.getItem(AUTH_TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

type UnauthorizedHandler = () => void;
let onUnauthorized: UnauthorizedHandler | null = null;

/** El AuthContext registra aquí qué hacer cuando el servidor responde 401 (token vencido o inválido). */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  onUnauthorized = handler;
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthCall = String(error?.config?.url ?? "").startsWith("/auth/");
    if (error?.response?.status === 401 && !isAuthCall) {
      onUnauthorized?.();
    }
    return Promise.reject(error);
  },
);

export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { messages?: string[] } | undefined;
    if (data?.messages?.length) {
      return data.messages.join("\n");
    }
    if (
      error.code === "ECONNABORTED" ||
      error.code === "ERR_NETWORK" ||
      error.message === "Network Error"
    ) {
      return "No se pudo conectar con el servidor. Revisa que el backend esté corriendo (puerto 8082) y que la IP/host en config.ts sea correcta.";
    }
    if (error.response) {
      return `Error del servidor (${error.response.status}). Revisa la consola del backend.`;
    }
    // Último recurso: muestra el mensaje crudo para poder diagnosticar.
    return `Error de red: ${error.message}`;
  }
  return "Ocurrió un error inesperado. Intenta de nuevo.";
}
