import AsyncStorage from "@react-native-async-storage/async-storage";
import { useSyncExternalStore } from "react";

import { errorStatus } from "./api";
import { SessionLogRequest, SessionResult } from "./types";
import { logSession } from "./worldService";

/**
 * Registros de sesión que no se pudieron enviar (sin conexión, sesión expirada o error del servidor).
 * Se guardan en el dispositivo con su `clientId` y se reintentan al volver la conexión o tras volver
 * a entrar: el backend es idempotente por clientId, así que reintentar nunca duplica.
 * No se borran al cerrar sesión (clearCache solo borra "cache:" y "pref:"): son de la persona y se
 * envían cuando vuelva a entrar con la misma cuenta.
 */
const KEY = "pending:sessions";
/** Lo que cubre el backend: registros con más de 60 días se rechazan. */
const MAX_AGE_MS = 60 * 24 * 60 * 60 * 1000;

export interface PendingSession {
  clientId: string;
  userId: string;
  /** Mundo dueño de la sesión (se envía como ?world= al reintentar). */
  world?: string | null;
  title: string;
  savedAt: number;
  request: SessionLogRequest;
}

let cachedCount = 0;
const listeners = new Set<() => void>();
let flushing: Promise<FlushOutcome> | null = null;

function notify(list: PendingSession[]) {
  cachedCount = list.length;
  listeners.forEach((l) => l());
}

async function readAll(): Promise<PendingSession[]> {
  try {
    const raw = await AsyncStorage.getItem(KEY);
    const list = raw ? (JSON.parse(raw) as PendingSession[]) : [];
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

async function writeAll(list: PendingSession[]): Promise<void> {
  await AsyncStorage.setItem(KEY, JSON.stringify(list)).catch(() => {});
  notify(list);
}

/** Guarda (o reemplaza, por clientId) un registro pendiente. */
export async function savePendingSession(entry: PendingSession): Promise<void> {
  const list = await readAll();
  await writeAll([...list.filter((p) => p.clientId !== entry.clientId), entry]);
}

export async function pendingFor(userId: string): Promise<PendingSession[]> {
  return (await readAll()).filter((p) => p.userId === userId);
}

/** Carga el contador inicial (para el aviso de "pendientes por enviar"). */
export async function refreshPendingCount(): Promise<number> {
  const list = await readAll();
  notify(list);
  return list.length;
}

export function usePendingCount(): number {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => {
        listeners.delete(l);
      };
    },
    () => cachedCount,
    () => cachedCount,
  );
}

export interface FlushOutcome {
  sent: SessionResult[];
  /** Rechazados por el servidor de forma definitiva (p. ej. ya no válidos). */
  dropped: number;
  remaining: number;
}

/** Errores que vale la pena reintentar más tarde: sin respuesta, 401, 408, 429 o 5xx. */
export function isRetryable(error: unknown): boolean {
  const status = errorStatus(error);
  return status === null || status === 401 || status === 408 || status === 429 || status >= 500;
}

/**
 * Envía los pendientes de esta cuenta, uno por uno. Se detiene ante el primer error de red o 401
 * (no tiene sentido seguir) y deja el resto para después.
 */
export function flushPendingSessions(userId: string): Promise<FlushOutcome> {
  if (flushing) return flushing;
  flushing = (async () => {
    const all = await readAll();
    const mine = all.filter((p) => p.userId === userId);
    const sent: SessionResult[] = [];
    const done = new Set<string>();
    let dropped = 0;
    for (const p of mine) {
      if (Date.now() - p.savedAt > MAX_AGE_MS) {
        done.add(p.clientId);
        dropped++;
        continue;
      }
      try {
        sent.push(await logSession({ ...p.request, clientId: p.clientId }, p.world ?? undefined));
        done.add(p.clientId);
      } catch (error) {
        if (isRetryable(error)) break;
        // 400/404/409: el servidor ya lo tiene (409) o no lo aceptará nunca; no se reintenta en bucle.
        done.add(p.clientId);
        if (errorStatus(error) !== 409) dropped++;
      }
    }
    const latest = await readAll();
    const rest = latest.filter((p) => !done.has(p.clientId));
    await writeAll(rest);
    return { sent, dropped, remaining: rest.filter((p) => p.userId === userId).length };
  })().finally(() => {
    flushing = null;
  });
  return flushing;
}

// ── clientId de la sesión en curso (persistido para que un reintento use el mismo) ──

const clientKey = (sessionId: string) => `session-client:${sessionId}`;

export async function rememberSessionClientId(sessionId: string, clientId: string): Promise<void> {
  await AsyncStorage.setItem(clientKey(sessionId), clientId).catch(() => {});
}

export async function forgetSessionClientId(sessionId: string): Promise<void> {
  await AsyncStorage.removeItem(clientKey(sessionId)).catch(() => {});
}
