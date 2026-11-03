import axios from "axios";

import { N8N_CHAT_URL } from "../constants/config";
import { loadCareMode } from "./careMode";
import { getContextVersion } from "./contextBus";
import { loadRawPref, savePref } from "./prefs";
import { fetchProfile } from "./profileService";
import { fetchProgress } from "./progressService";
import { Question, fetchQuestionnaireDetail, fetchQuestionnaireList } from "./questionnaireService";
import { Routine, fetchRoutine, regenerateRoutine, setExerciseDone, swapExercise } from "./routineService";

export interface ChatReply {
  reply: string;
  personalized: boolean;
}

// ───────────────────────── Historial persistente ─────────────────────────

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

/** Contexto del usuario ya armado; se genera al pulsar «Cargar mis datos» y viaja con cada mensaje. */
let profileContext: string | null = null;
let loadedVersion = -1;
let suggestions: string[] = [];
/** id de ejercicio recomendado → nombre, para describir las acciones que propone el asistente. */
const exerciseNames = new Map<string, string>();

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

/** Empieza una conversación nueva: el agente olvida la anterior (nueva sesión de memoria). */
export async function startNewConversation(): Promise<void> {
  sessionId = newSessionId();
  profileContext = null;
  loadedVersion = -1;
  await savePref<StoredChat | null>(HISTORY_KEY, null);
}

/** Se llama al cerrar sesión para no arrastrar contexto de otra cuenta. */
export function resetChatSession(): void {
  sessionId = newSessionId();
  profileContext = null;
  loadedVersion = -1;
  suggestions = [];
  exerciseNames.clear();
}

export function isProfileContextStale(): boolean {
  return profileContext !== null && loadedVersion !== getContextVersion();
}

export function getSuggestions(): string[] {
  return suggestions;
}

// ───────────────────────── Contexto del usuario ─────────────────────────

/** Convierte la respuesta guardada de una pregunta (ids, listas, números) en texto legible. */
function formatAnswer(question: Question, value: unknown): string | null {
  if (value == null || value === "" || (Array.isArray(value) && value.length === 0)) return null;
  const label = (v: unknown) => question.options?.find((o) => o.id === String(v))?.text ?? String(v);
  const text = Array.isArray(value) ? value.map(label).join(", ") : label(value);
  return question.unit && !Array.isArray(value) ? `${text} ${question.unit}` : text;
}

interface SectionResult {
  text: string;
  tips?: string[];
}

/** Cada sección falla de forma independiente: si una no carga, el resto del contexto sigue sirviendo. */
async function profileSection(): Promise<SectionResult> {
  const p = await fetchProfile();
  const lines = [
    `Nombre: ${p.name}`,
    p.age != null && `Edad: ${p.age} años`,
    p.weightKg != null && `Peso: ${p.weightKg} kg`,
    p.heightCm != null && `Estatura: ${p.heightCm} cm`,
    (p.city || p.country) && `Ubicación: ${[p.city, p.country].filter(Boolean).join(", ")}`,
    `Perfil completado al ${p.profileStrengthPct}%`,
  ].filter(Boolean);
  const care = await loadCareMode();
  if (care.assistantNote) lines.push(`Modo de cuidado: ${care.label}. ${care.assistantNote}`);
  return { text: `## Datos personales\n${lines.join("\n")}` };
}

const TEST_LABELS: Record<string, string> = {
  test_sentadillas_30s: "Sentadillas en 30 s",
  test_flexiones_30s: "Flexiones en 30 s",
  test_plancha_s: "Plancha",
  test_equilibrio_s: "Equilibrio a una pierna",
  test_flexibilidad_cm: "Flexibilidad (sentarse y alcanzar)",
};

async function progressSection(): Promise<SectionResult> {
  const p = await fetchProgress();
  const lines = [
    `Sesiones completadas: ${p.totalSessions} (esta semana: ${p.sessionsThisWeek}, racha actual: ${p.currentStreak}, mejor racha: ${p.longestStreak})`,
    p.avgRpe != null && `Esfuerzo percibido reciente: ${p.avgRpe.toFixed(1)}/10`,
  ].filter(Boolean);
  const weight = p.series.find((s) => s.key === "peso_kg");
  if (weight && weight.points.length > 1) {
    lines.push(`Peso: de ${weight.points[0].value} a ${weight.points[weight.points.length - 1].value} kg`);
  }
  const tests = p.series
    .filter((s) => s.key.startsWith("test_"))
    .map((s) => {
      const last = s.points[s.points.length - 1];
      const trend = s.points.length > 1 ? ` (antes ${s.points[0].value})` : "";
      return `- ${TEST_LABELS[s.key] ?? s.key}: ${last.value} ${s.unit ?? ""}${trend}`;
    });
  const text = [`## Progreso\n${lines.join("\n")}`, tests.length ? `### Evaluación física\n${tests.join("\n")}` : ""]
    .filter(Boolean)
    .join("\n");
  return { text };
}

async function questionnairesSection(): Promise<SectionResult> {
  const list = await fetchQuestionnaireList();
  const all = list.groups.flatMap((g) => g.items);
  const answered = all.filter((q) => q.answeredCount > 0);
  const pending = all.filter((q) => q.status !== "completo").map((q) => q.title);

  const blocks = await Promise.all(
    answered.map(async (q) => {
      const detail = await fetchQuestionnaireDetail(q.key);
      const lines = detail.questions
        .map((question) => {
          const answer = formatAnswer(question, detail.answers[question.id]);
          return answer && `- ${question.prompt}: ${answer}`;
        })
        .filter(Boolean);
      return lines.length ? `### ${detail.title}\n${lines.join("\n")}` : null;
    }),
  );

  const out = [`## Cuestionarios respondidos\n${blocks.filter(Boolean).join("\n\n") || "Ninguno todavía."}`];
  if (pending.length) out.push(`Cuestionarios incompletos o sin iniciar: ${pending.join(", ")}`);

  const tips: string[] = [];
  if (pending.length > 0) tips.push("¿Qué cuestionario me conviene completar primero?");
  if (answered.some((q) => q.key === "alimentacion")) tips.push("Dame ideas de comida antes y después de entrenar");
  if (answered.some((q) => q.key === "lesiones")) tips.push("Tengo una molestia, ¿cómo adapto mi rutina?");
  return { text: out.join("\n"), tips };
}

async function routineSection(): Promise<SectionResult> {
  const r: Routine = await fetchRoutine();
  if (!r.activityName) return { text: "## Rutina actual\nAún no tiene rutina generada." };
  exerciseNames.clear();
  const days = r.days
    .map((d) => {
      const list = d.exercises
        .map((e) => {
          exerciseNames.set(e.exerciseRecommendationId, e.name);
          return `${e.name} [id:${e.exerciseRecommendationId}]${e.done ? " (hecho)" : ""}`;
        })
        .join("; ");
      return `- ${d.dayLabel}: ${list}`;
    })
    .join("\n");
  const header = [
    `Disciplina: ${r.activityName}`,
    r.goalLabel && `Objetivo: ${r.goalLabel}`,
    r.intensityLabel && `Intensidad: ${r.intensityLabel}`,
    r.durationMin != null && `Duración por sesión: ${r.durationMin} min`,
    `Progreso: ${r.doneCount}/${r.totalCount} ejercicios hechos`,
    r.rationale && `Por qué se recomendó: ${r.rationale}`,
  ].filter(Boolean);

  const next = r.days.flatMap((d) => d.exercises).find((e) => !e.done);
  const tips = [`¿Por qué me recomendaron ${r.activityName}?`];
  if (next) tips.push(`¿Cómo hago bien ${next.name}?`);
  return { text: `## Rutina actual\n${header.join("\n")}\n${days}`, tips };
}

const BASE_SUGGESTIONS = [
  "¿Cuánto debo descansar?",
  "Rutina de calentamiento",
  "¿Qué comer antes de entrenar?",
  "Progresión de carga",
];

/**
 * Reúne perfil, cuestionarios y rutina en un texto para el agente y calcula sugerencias.
 * Lanza error solo si ninguna sección pudo cargarse.
 */
export async function loadProfileContext(): Promise<void> {
  const version = getContextVersion();
  const results = await Promise.allSettled([profileSection(), questionnairesSection(), routineSection(), progressSection()]);
  const ok = results.flatMap((r) => (r.status === "fulfilled" ? [r.value] : []));
  if (ok.length === 0) throw new Error("No se pudieron cargar tus datos.");
  profileContext = ok.map((s) => s.text).join("\n\n");
  loadedVersion = version;
  const dynamic = ok.flatMap((s) => s.tips ?? []);
  suggestions = [...dynamic, ...BASE_SUGGESTIONS].slice(0, 6);
}

// ───────────────────────── Acciones propuestas por el agente ─────────────────────────

export type ChatAction =
  | { type: "swap"; id: string }
  | { type: "regenerate"; mode: "refresh" | "another" }
  | { type: "done"; id: string; done: boolean };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ACTION_TAG = /<accion>([\s\S]*?)<\/accion>/gi;

/** Separa el texto visible de las acciones que el agente incluyó con <accion>{json}</accion>. */
export function parseReply(output: string): { text: string; actions: ChatAction[] } {
  const actions: ChatAction[] = [];
  for (const match of output.matchAll(ACTION_TAG)) {
    try {
      const raw = JSON.parse(match[1]) as Record<string, unknown>;
      const id = typeof raw.id === "string" && UUID.test(raw.id) && exerciseNames.has(raw.id) ? raw.id : null;
      if (raw.tipo === "cambiar_ejercicio" && id) actions.push({ type: "swap", id });
      else if (raw.tipo === "marcar_hecho" && id) actions.push({ type: "done", id, done: raw.hecho !== false });
      else if (raw.tipo === "regenerar_rutina") {
        actions.push({ type: "regenerate", mode: raw.modo === "another" ? "another" : "refresh" });
      }
    } catch {
      // acción malformada: se ignora, el texto sigue siendo útil
    }
  }
  const text = output.replace(ACTION_TAG, "").replace(/\n{3,}/g, "\n\n").trim();
  return { text, actions };
}

export function describeAction(action: ChatAction): string {
  switch (action.type) {
    case "swap":
      return `Cambiar «${exerciseNames.get(action.id) ?? "ejercicio"}» por una alternativa`;
    case "done":
      return `${action.done ? "Marcar como hecho" : "Desmarcar"} «${exerciseNames.get(action.id) ?? "ejercicio"}»`;
    case "regenerate":
      return action.mode === "another" ? "Probar una disciplina distinta" : "Regenerar mi rutina con mis datos";
  }
}

/** Ejecuta una acción ya confirmada por el usuario y devuelve el mensaje de resultado. */
export async function runAction(action: ChatAction): Promise<string> {
  switch (action.type) {
    case "swap":
      await swapExercise(action.id);
      return "Listo, cambié el ejercicio. Mira tu rutina para ver la alternativa.";
    case "done":
      await setExerciseDone(action.id, action.done);
      return action.done ? "Marcado como hecho." : "Desmarcado.";
    case "regenerate":
      await regenerateRoutine(action.mode);
      return "Rutina actualizada. La encuentras en la pestaña Rutina.";
  }
}

// ───────────────────────── Envío ─────────────────────────

export async function askAssistant(message: string, includeProfile: boolean): Promise<ChatReply> {
  const personalized = includeProfile && profileContext != null;
  const chatInput = personalized
    ? `<contexto_usuario>\n${profileContext}\n</contexto_usuario>\n\nPregunta del usuario: ${message}`
    : message;

  const { data } = await axios.post<{ output?: string }>(
    N8N_CHAT_URL,
    { action: "sendMessage", sessionId, chatInput },
    { timeout: 60000 },
  );
  return { reply: data.output ?? "No recibí respuesta del asistente.", personalized };
}
