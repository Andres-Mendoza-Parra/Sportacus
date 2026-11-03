import { api } from "./api";
import { cachedRequest } from "./cache";
import { invalidateUserContext } from "./contextBus";

export interface RoutineExercise {
  exerciseRecommendationId: string;
  exerciseId: string;
  name: string;
  icon: string;
  imageUrl: string | null;
  detailLabel: string;
  muscleGroupTag: string;
  difficultyTag: string;
  logged: boolean;
  /** Casilla del checklist (solo lista de tareas de la rutina vigente, no seguimiento histórico). */
  done: boolean;
  reaction: "like" | "dislike" | null;
  /** Valores crudos (el Taller los edita; detailLabel es solo para mostrar). */
  sets: number | null;
  reps: number | null;
  durationSec: number | null;
}

export interface RoutineDay {
  dayLabel: string;
  exercises: RoutineExercise[];
}

export interface Routine {
  routeId: string | null;
  activityId: string | null;
  activityName: string;
  description: string;
  durationMin: number | null;
  intensityLabel: string;
  goalLabel: string;
  affinityScore: number;
  rationale: string;
  generatedAt: string | null;
  profileStrengthPct: number;
  engineVersion: string | null;
  alternativesCount: number;
  doneCount: number;
  totalCount: number;
  days: RoutineDay[];
  /** Puntos que aportó cada factor al score de afinidad (ordenados por peso). */
  breakdown: ScoreFactor[];
}

export interface ScoreFactor {
  key: string;
  label: string;
  points: number;
}

export interface RoutineOption {
  activityId: string;
  name: string;
  description: string;
  score: number;
  rationale: string;
  intensityLabel: string;
  durationMin: number | null;
  environment: string;
  selected: boolean;
}

/** Silueta del cuerpo con los músculos trabajados en capas transparentes encima. */
export interface MuscleView {
  baseUrl: string;
  overlayUrls: string[];
}

export interface ExerciseDetail {
  exerciseRecommendationId: string;
  name: string;
  description: string | null;
  environmentTag: string;
  difficultyTag: string;
  muscleGroupTag: string | null;
  detailLabel: string;
  steps: string[];
  instructions: string[];
  tips: string[];
  musclesPrimary: string[];
  musclesSecondary: string[];
  muscleImageUrls: string[];
  muscleViews: MuscleView[];
  imageUrls: string[];
  videoUrl: string | null;
  equipment: string | null;
  mediaSource: string | null;
  contraindications: string | null;
  logged: boolean;
  done: boolean;
  reaction: "like" | "dislike" | null;
}

export interface DoneResult {
  done: boolean;
  doneCount: number;
  totalCount: number;
}

export type Reaction = "like" | "dislike";
export type RegenerateMode = "refresh" | "another";

/** Rellena con valores por defecto los campos nuevos que el backend podría no enviar aún. */
export function normalizeRoutine(raw: Partial<Routine>): Routine {
  const days = (raw.days ?? []).map((d) => ({
    dayLabel: d.dayLabel,
    exercises: (d.exercises ?? []).map((e) => ({
      ...e,
      imageUrl: e.imageUrl ?? null,
      reaction: e.reaction ?? null,
      done: e.done ?? false,
      sets: e.sets ?? null,
      reps: e.reps ?? null,
      durationSec: e.durationSec ?? null,
    })),
  }));
  const all = days.flatMap((d) => d.exercises);
  return {
    routeId: raw.routeId ?? null,
    activityId: raw.activityId ?? null,
    activityName: raw.activityName ?? "",
    description: raw.description ?? "",
    durationMin: raw.durationMin ?? null,
    intensityLabel: raw.intensityLabel ?? "",
    goalLabel: raw.goalLabel ?? "",
    affinityScore: raw.affinityScore ?? 0,
    rationale: raw.rationale ?? "",
    generatedAt: raw.generatedAt ?? null,
    profileStrengthPct: raw.profileStrengthPct ?? 0,
    engineVersion: raw.engineVersion ?? null,
    alternativesCount: raw.alternativesCount ?? 0,
    doneCount: raw.doneCount ?? all.filter((e) => e.done).length,
    totalCount: raw.totalCount ?? all.length,
    days,
    breakdown: raw.breakdown ?? [],
  };
}

export function normalizeDetail(raw: Partial<ExerciseDetail> & { exerciseRecommendationId: string; name: string }): ExerciseDetail {
  const steps = raw.steps ?? [];
  return {
    exerciseRecommendationId: raw.exerciseRecommendationId,
    name: raw.name,
    description: raw.description ?? null,
    environmentTag: raw.environmentTag ?? "",
    difficultyTag: raw.difficultyTag ?? "",
    muscleGroupTag: raw.muscleGroupTag ?? null,
    detailLabel: raw.detailLabel ?? "",
    steps,
    instructions: raw.instructions && raw.instructions.length > 0 ? raw.instructions : steps,
    tips: raw.tips ?? [],
    musclesPrimary: raw.musclesPrimary ?? [],
    musclesSecondary: raw.musclesSecondary ?? [],
    muscleImageUrls: raw.muscleImageUrls ?? [],
    muscleViews: raw.muscleViews ?? [],
    imageUrls: raw.imageUrls ?? [],
    videoUrl: raw.videoUrl ?? null,
    equipment: raw.equipment ?? null,
    mediaSource: raw.mediaSource ?? null,
    contraindications: raw.contraindications ?? null,
    logged: raw.logged ?? false,
    done: raw.done ?? false,
    reaction: raw.reaction ?? null,
  };
}

export async function fetchRoutine(): Promise<Routine> {
  return cachedRequest("routine", async () => {
    const { data } = await api.get<Routine>("/routine");
    return normalizeRoutine(data);
  });
}

/** Descarga el detalle de cada ejercicio de la rutina para poder consultarlos sin conexión. */
export function prefetchRoutineDetails(routine: Routine): void {
  routine.days
    .flatMap((d) => d.exercises)
    .forEach((e) => {
      fetchExerciseDetail(e.exerciseRecommendationId).catch(() => {});
    });
}

export async function fetchRoutineOptions(): Promise<RoutineOption[]> {
  const { data } = await api.get<RoutineOption[]>("/routine/options");
  return data ?? [];
}

export async function selectRoutine(activityId: string): Promise<Routine> {
  const { data } = await api.post<Routine>("/routine/select", { activityId });
  invalidateUserContext();
  return normalizeRoutine(data);
}

export async function regenerateRoutine(mode: RegenerateMode): Promise<Routine> {
  const { data } = await api.post<Routine>("/routine/regenerate", { mode });
  invalidateUserContext();
  return normalizeRoutine(data);
}

export async function swapExercise(exerciseRecommendationId: string): Promise<Routine> {
  const { data } = await api.post<Routine>(`/routine/exercises/${exerciseRecommendationId}/swap`);
  invalidateUserContext();
  return normalizeRoutine(data);
}

export async function sendExerciseFeedback(exerciseRecommendationId: string, reaction: Reaction): Promise<void> {
  await api.post(`/routine/exercises/${exerciseRecommendationId}/feedback`, { reaction });
}

export async function fetchExerciseDetail(exerciseRecommendationId: string): Promise<ExerciseDetail> {
  return cachedRequest(`exercise:${exerciseRecommendationId}`, async () => {
    const { data } = await api.get<ExerciseDetail>(`/routine/exercises/${exerciseRecommendationId}`);
    return normalizeDetail(data);
  });
}

export async function logExercise(exerciseRecommendationId: string): Promise<void> {
  await api.post(`/routine/exercises/${exerciseRecommendationId}/log`);
}

/** Marca o desmarca un ejercicio en el checklist de la rutina vigente. */
export async function setExerciseDone(exerciseRecommendationId: string, done: boolean): Promise<DoneResult> {
  const { data } = await api.put<DoneResult>(`/routine/exercises/${exerciseRecommendationId}/done`, { done });
  invalidateUserContext();
  return { done: data?.done ?? done, doneCount: data?.doneCount ?? 0, totalCount: data?.totalCount ?? 0 };
}

// ───────────────────────── Taller de rutina ─────────────────────────

export async function addRoutineDay(label: string): Promise<Routine> {
  const { data } = await api.post<Routine>("/routine/days", { label });
  invalidateUserContext();
  return normalizeRoutine(data);
}

export async function removeRoutineDay(dayIndex: number): Promise<Routine> {
  const { data } = await api.delete<Routine>(`/routine/days/${dayIndex}`);
  invalidateUserContext();
  return normalizeRoutine(data);
}

export async function addExerciseToDay(
  dayIndex: number,
  exerciseId: string,
  details: { sets?: number; reps?: number; durationSec?: number },
): Promise<Routine> {
  const { data } = await api.post<Routine>(`/routine/days/${dayIndex}/exercises`, { exerciseId, ...details });
  invalidateUserContext();
  return normalizeRoutine(data);
}

export async function updateRoutineExercise(
  exerciseRecommendationId: string,
  details: { sets?: number; reps?: number; durationSec?: number; dayIndex?: number },
): Promise<Routine> {
  const { data } = await api.put<Routine>(`/routine/exercises/${exerciseRecommendationId}`, details);
  invalidateUserContext();
  return normalizeRoutine(data);
}

export async function removeRoutineExercise(exerciseRecommendationId: string): Promise<Routine> {
  const { data } = await api.delete<Routine>(`/routine/exercises/${exerciseRecommendationId}`);
  invalidateUserContext();
  return normalizeRoutine(data);
}
