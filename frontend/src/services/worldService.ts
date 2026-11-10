import { api } from "./api";
import { cachedRequest } from "./cache";
import {
  AssessmentResult,
  AssessmentView,
  BlockDetail,
  BlockView,
  DashboardView,
  MovementDetail,
  MovementList,
  PlanView,
  ProgressView,
  SessionDetail,
  SessionLogRequest,
  SessionResult,
  SessionSummary,
  SessionUi,
} from "./types";

/**
 * Endpoints del mundo activo (docs/16 §5). Las lecturas principales se guardan para uso sin conexión;
 * la clave de caché incluye el mundo para no mezclar datos al cambiar de mundo.
 */
let worldKey = "none";

/** WorldContext avisa aquí cuál es el mundo activo (solo afecta a las claves de caché). */
export function setCacheWorld(key: string | null): void {
  worldKey = key ?? "none";
}

function cacheKey(name: string): string {
  return `world:${worldKey}:${name}`;
}

export function fetchDashboard(): Promise<DashboardView> {
  return cachedRequest(cacheKey("dashboard"), async () => (await api.get<DashboardView>("/world/dashboard")).data);
}

export function fetchProgress(): Promise<ProgressView> {
  return cachedRequest(cacheKey("progress"), async () => (await api.get<ProgressView>("/world/progress")).data);
}

export function fetchPlan(): Promise<PlanView> {
  return cachedRequest(cacheKey("plan"), async () => (await api.get<PlanView>("/world/plan")).data);
}

export async function regeneratePlan(): Promise<PlanView> {
  const { data } = await api.post<PlanView>("/world/plan/regenerate", undefined, { timeout: 30000 });
  return data;
}

export function fetchSession(sessionId: string): Promise<SessionDetail> {
  return cachedRequest(cacheKey(`session:${sessionId}`), async () =>
    (await api.get<SessionDetail>(`/world/sessions/${encodeURIComponent(sessionId)}`)).data,
  );
}

export async function fetchSessionUi(): Promise<SessionUi> {
  const { data } = await api.get<SessionUi>("/world/session-ui");
  return data;
}

/** Cambia el movimiento del bloque por una alternativa segura del mismo patrón. `world` = mundo dueño del bloque. */
export async function swapBlock(blockId: string, world?: string | null): Promise<BlockView> {
  const { data } = await api.post<BlockView>(`/world/blocks/${encodeURIComponent(blockId)}/swap`, undefined, {
    params: world ? { world } : undefined,
  });
  clearBlockDetailMemo();
  return data;
}

/** `world` fuerza el mundo destino (p. ej. "base" para la actividad libre); sin él, el mundo activo. */
export async function logSession(request: SessionLogRequest, world?: string): Promise<SessionResult> {
  const { data } = await api.post<SessionResult>("/world/sessions/log", request, {
    timeout: 30000,
    params: world ? { world } : undefined,
  });
  return data;
}

export async function fetchHistory(limit = 20): Promise<SessionSummary[]> {
  const { data } = await api.get<SessionSummary[]>("/world/sessions/history", { params: { limit } });
  return data;
}

export async function reportNextDayPain(logId: string, pain: number): Promise<void> {
  await api.post(`/world/sessions/${encodeURIComponent(logId)}/next-day-pain`, { pain });
}

export function fetchAssessments(): Promise<AssessmentView[]> {
  return cachedRequest(cacheKey("assessments"), async () => (await api.get<AssessmentView[]>("/world/assessments")).data);
}

export async function recordAssessment(key: string, value: number): Promise<AssessmentResult> {
  const { data } = await api.post<AssessmentResult>(`/world/assessments/${encodeURIComponent(key)}`, { value });
  return data;
}

export interface MovementQuery {
  q?: string;
  pattern?: string | null;
  favorites?: boolean;
}

export async function fetchMovements(query: MovementQuery = {}): Promise<MovementList> {
  const params: Record<string, string> = {};
  if (query.q?.trim()) params.q = query.q.trim();
  if (query.pattern) params.pattern = query.pattern;
  if (query.favorites) params.favorites = "true";
  const { data } = await api.get<MovementList>("/world/movements", { params });
  return data;
}

export async function fetchMovement(key: string): Promise<MovementDetail> {
  const { data } = await api.get<MovementDetail>(`/world/movements/${encodeURIComponent(key)}`);
  return data;
}

export async function setFavorite(key: string, favorite: boolean): Promise<void> {
  const url = `/world/movements/${encodeURIComponent(key)}/favorite`;
  if (favorite) await api.post(url);
  else await api.delete(url);
}

export async function sendMovementFeedback(key: string, reaction: "like" | "dislike"): Promise<void> {
  await api.post(`/world/movements/${encodeURIComponent(key)}/feedback`, { reaction });
}

/** Detalle de un bloque del plan (por qué, dosis, cómo hacerlo, precauciones). `world` fuerza el mundo. */
export function fetchBlockDetail(blockId: string, world?: string | null): Promise<BlockDetail> {
  return cachedRequest(cacheKey(`block:${blockId}`), async () =>
    (
      await api.get<BlockDetail>(`/world/blocks/${encodeURIComponent(blockId)}`, {
        params: world ? { world } : undefined,
      })
    ).data,
  );
}

/** Caché en memoria por bloque + movimiento (un cambio de ejercicio invalida la entrada). */
const blockDetailMemo = new Map<string, Promise<BlockDetail>>();

/** Olvida los detalles de bloque en memoria (tras cambiar datos que afectan al plan). */
export function clearBlockDetailMemo(): void {
  blockDetailMemo.clear();
}

/** Igual que fetchBlockDetail pero reutiliza la respuesta mientras el bloque no cambie de movimiento. */
export function getBlockDetailCached(block: Pick<BlockView, "id" | "movementKey">): Promise<BlockDetail> {
  const key = `${worldKey}:${block.id}:${block.movementKey ?? ""}`;
  const hit = blockDetailMemo.get(key);
  if (hit) return hit;
  const promise = fetchBlockDetail(block.id).catch((error: unknown) => {
    blockDetailMemo.delete(key);
    throw error;
  });
  blockDetailMemo.set(key, promise);
  return promise;
}
