import { api, extractErrorMessage } from "./api";
import { loadRawPref, savePref } from "./prefs";
import { ChatReply } from "./types";
import { regeneratePlan } from "./worldService";

/**
 * Asistente (docs/16 §8). El backend arma solo el contexto del mundo activo (ruta, nivel, salud,
 * próxima sesión) cuando includeProfile = true; el frontend solo guarda el historial local.
 */

// ───────────────────────── Historial persistente ─────────────────────────

export type ChatAction = { type: "regenerate_plan" };

export interface StoredMessage {
  id: number;
  from: "bot" | "user" | "system";
  text: string;
  time: string;
  personalized?: boolean;
  /** Acciones que el asistente propuso y que el usuario puede confirmar con un toque. */
  actions?: ChatAction[];
}

export interface StoredChat {
  sessionId: string;
  profileLoaded: boolean;
  messages: StoredMessage[];
}

const HISTORY_KEY = "chat_history";
const MAX_STORED_MESSAGES = 80;

function newSessionId(): string {
  return `sportacus-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

let sessionId = newSessionId();

export async function loadChatHistory(): Promise<StoredChat | null> {
  const stored = await loadRawPref<StoredChat | null>(HISTORY_KEY, null);
  if (stored?.sessionId) sessionId = stored.sessionId;
  return stored;
}

export async function saveChatHistory(chat: Omit<StoredChat, "sessionId">): Promise<void> {
  await savePref<StoredChat>(HISTORY_KEY, {
    sessionId,
    profileLoaded: chat.profileLoaded,
    messages: chat.messages.slice(-MAX_STORED_MESSAGES),
  });
}

/** Empieza una conversación nueva: se borra el historial local. */
export async function startNewConversation(): Promise<void> {
  sessionId = newSessionId();
  await savePref<StoredChat | null>(HISTORY_KEY, null);
}

/** Se llama al cerrar sesión para no arrastrar la conversación de otra cuenta. */
export function resetChatSession(): void {
  sessionId = newSessionId();
}

// ───────────────────────── Sugerencias ─────────────────────────

/** Sugerencias genéricas; el nombre del mundo activo las adapta sin lógica propia de cada mundo. */
export function suggestionsFor(worldName: string | null): string[] {
  const base = ["¿Qué entreno hoy?", "Explícame mi plan", "¿Cuánto debo descansar?", "¿Qué comer antes de entrenar?"];
  if (!worldName) return base;
  return [`¿Cómo progreso en ${worldName}?`, ...base, "Quiero un plan nuevo"];
}

// ───────────────────────── Acciones propuestas por el agente ─────────────────────────

const ACTION_TAG = /<accion>([\s\S]*?)<\/accion>/gi;

/** Separa el texto visible de las acciones incluidas con <accion>{json}</accion>. */
export function parseReply(output: string): { text: string; actions: ChatAction[] } {
  const actions: ChatAction[] = [];
  for (const match of output.matchAll(ACTION_TAG)) {
    try {
      const raw = JSON.parse(match[1]) as Record<string, unknown>;
      if (raw.tipo === "regenerar_plan" && actions.length === 0) actions.push({ type: "regenerate_plan" });
    } catch {
      // acción malformada: se ignora, el texto sigue siendo útil
    }
  }
  const text = output.replace(ACTION_TAG, "").replace(/\n{3,}/g, "\n\n").trim();
  return { text, actions };
}

export function describeAction(action: ChatAction): string {
  switch (action.type) {
    case "regenerate_plan":
      return "Regenerar mi plan con mis datos actuales";
  }
}

/** Ejecuta una acción ya confirmada por el usuario y devuelve el mensaje de resultado. */
export async function runAction(action: ChatAction): Promise<string> {
  switch (action.type) {
    case "regenerate_plan": {
      const plan = await regeneratePlan();
      return `Listo: generé «${plan.title}». Lo encuentras en la pestaña Plan.`;
    }
  }
}

// ───────────────────────── Envío ─────────────────────────

export async function askAssistant(message: string, includeProfile: boolean): Promise<ChatReply> {
  try {
    const { data } = await api.post<ChatReply>(
      "/chat",
      { message, includeProfile },
      { timeout: 120000 }, // el modelo local puede tardar varios segundos en responder
    );
    return data;
  } catch (error) {
    throw new Error(extractErrorMessage(error));
  }
}
