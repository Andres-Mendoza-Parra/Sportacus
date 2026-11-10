import AsyncStorage from "@react-native-async-storage/async-storage";

import { AUTH_TOKEN_KEY } from "../constants/config";
import { api } from "./api";

/**
 * Analítica propia (sin terceros): cola en memoria respaldada en AsyncStorage y envío por lotes a
 * POST /events. Solo nombres de evento y propiedades cortas y enumerables: NUNCA datos de salud ni
 * texto libre (las propiedades se filtran a números, booleanos y textos cortos sin espacios raros).
 */
export type EventName =
  | "app_open"
  | "screen_view"
  | "tutorial_step"
  | "tutorial_completed"
  | "tutorial_skipped"
  | "menu_opened"
  | "block_detail_opened"
  | "why_viewed"
  | "session_started"
  | "session_abandoned"
  | "assistant_message"
  | "palette_changed"
  | "reminder_enabled";

export type EventProps = Record<string, string | number | boolean | null | undefined>;

interface QueuedEvent {
  name: string;
  props?: Record<string, string | number | boolean>;
  at: string;
}

const STORAGE_KEY = "analytics:queue";
const NAME_RE = /^[a-z][a-z0-9_]{2,59}$/;
/** Solo identificadores (claves de mundo, pantallas, colores…): nada de frases. */
const SAFE_TEXT_RE = /^[A-Za-z0-9_\-/:[\]().]{1,60}$/;
const BATCH = 50;
const MAX_QUEUE = 500;

let queue: QueuedEvent[] = [];
let loaded = false;
let sending: Promise<void> | null = null;

async function ensureLoaded(): Promise<void> {
  if (loaded) return;
  loaded = true;
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    const stored = raw ? (JSON.parse(raw) as QueuedEvent[]) : [];
    if (Array.isArray(stored)) queue = [...stored, ...queue].slice(-MAX_QUEUE);
  } catch {
    // copia dañada: se descarta
  }
}

function persist(): void {
  AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(queue)).catch(() => {});
}

function sanitize(props?: EventProps): Record<string, string | number | boolean> | undefined {
  if (!props) return undefined;
  const out: Record<string, string | number | boolean> = {};
  for (const [k, v] of Object.entries(props)) {
    if (Object.keys(out).length >= 10) break;
    if (!NAME_RE.test(k) && !/^[a-z]{1,2}$/.test(k)) continue;
    if (typeof v === "number" && Number.isFinite(v)) out[k] = v;
    else if (typeof v === "boolean") out[k] = v;
    else if (typeof v === "string" && SAFE_TEXT_RE.test(v)) out[k] = v;
  }
  return Object.keys(out).length ? out : undefined;
}

/** Encola un evento (no bloquea ni falla nunca). */
export function track(name: EventName, props?: EventProps): void {
  if (!NAME_RE.test(name)) return;
  queue.push({ name, props: sanitize(props), at: new Date().toISOString() });
  if (queue.length > MAX_QUEUE) queue = queue.slice(-MAX_QUEUE);
  ensureLoaded().then(persist);
}

/** Envía la cola por lotes de 50. Si falla, los eventos se quedan para el siguiente intento. */
export function flushAnalytics(): Promise<void> {
  if (sending) return sending;
  sending = (async () => {
    await ensureLoaded();
    const token = await AsyncStorage.getItem(AUTH_TOKEN_KEY).catch(() => null);
    if (!token) return;
    while (queue.length > 0) {
      const batch = queue.slice(0, BATCH);
      try {
        await api.post("/events", { events: batch }, { timeout: 8000 });
      } catch {
        return;
      }
      queue = queue.slice(batch.length);
      persist();
    }
  })().finally(() => {
    sending = null;
  });
  return sending;
}

/** Al cerrar sesión: intenta enviar lo pendiente y descarta el resto (no se mezcla con otra cuenta). */
export async function flushAndResetAnalytics(): Promise<void> {
  await flushAnalytics().catch(() => {});
  queue = [];
  await AsyncStorage.removeItem(STORAGE_KEY).catch(() => {});
}
