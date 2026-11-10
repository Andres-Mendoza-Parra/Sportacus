import axios from "axios";
import AsyncStorage from "@react-native-async-storage/async-storage";

import { API_BASE_URL, AUTH_TOKEN_KEY } from "../constants/config";
import { setOnline } from "./connectivity";
import { ApiErrorBody } from "./types";

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

/** El AuthContext registra aquí qué hacer cuando el servidor responde 401 (token vencido o revocado). */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  onUnauthorized = handler;
}

/** ¿El error es de red (sin respuesta del servidor: sin conexión, servidor caído o tiempo agotado)? */
export function isNetworkError(error: unknown): boolean {
  return axios.isAxiosError(error) && !error.response;
}

/** Código HTTP del error, o null si no hubo respuesta. */
export function errorStatus(error: unknown): number | null {
  return axios.isAxiosError(error) ? (error.response?.status ?? null) : null;
}

/** Segundos de espera que manda el servidor en un 429 (bloqueo por intentos fallidos). */
export function retryAfterSeconds(error: unknown): number | null {
  if (!axios.isAxiosError(error)) return null;
  const body = error.response?.data as ApiErrorBody | undefined;
  if (typeof body?.retryAfterSeconds === "number" && body.retryAfterSeconds > 0) return body.retryAfterSeconds;
  const header = Number(error.response?.headers?.["retry-after"]);
  return Number.isFinite(header) && header > 0 ? header : null;
}

api.interceptors.response.use(
  (response) => {
    setOnline(true);
    return response;
  },
  (error) => {
    if (axios.isAxiosError(error)) {
      if (error.response) setOnline(true);
      else if (error.code !== "ERR_CANCELED") setOnline(false);
    }
    const isAuthCall = String(error?.config?.url ?? "").startsWith("/auth/");
    if (error?.response?.status === 401 && !isAuthCall) {
      onUnauthorized?.();
    }
    return Promise.reject(error);
  },
);

/** Mensaje para la persona usuaria: el del servidor si lo hay; si no, uno claro y sin tecnicismos. */
export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiErrorBody | undefined;
    if (data?.messages?.length) {
      return data.messages.join("\n");
    }
    if (typeof data?.message === "string" && data.message.trim()) {
      return data.message;
    }
    if (error.code === "ECONNABORTED") {
      return "El servidor está tardando en responder. Revisa tu conexión e intenta de nuevo.";
    }
    if (!error.response) {
      return "No pudimos conectarnos. Revisa tu conexión a internet e intenta de nuevo.";
    }
    const status = error.response.status;
    if (status === 401) return "Tu sesión expiró. Vuelve a entrar para continuar.";
    if (status === 403) return "No tienes permiso para hacer esto.";
    if (status === 404) return "No encontramos lo que buscabas.";
    if (status === 429) return "Hiciste muchos intentos seguidos. Espera un momento e intenta de nuevo.";
    if (status >= 500) return "Tuvimos un problema de nuestro lado. Intenta de nuevo en unos minutos.";
    return "No pudimos completar la acción. Revisa los datos e intenta de nuevo.";
  }
  return "Ocurrió un error inesperado. Intenta de nuevo.";
}
