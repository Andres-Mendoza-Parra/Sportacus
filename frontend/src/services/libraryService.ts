import { api } from "./api";
import { invalidateUserContext } from "./contextBus";
import { ExerciseDetail, Routine, normalizeDetail, normalizeRoutine } from "./routineService";

export interface LibraryExercise {
  id: string;
  name: string;
  muscleGroup: string;
  equipment: string | null;
  difficultyTag: string;
  environmentTag: string;
  icon: string;
  imageUrl: string | null;
  favorite: boolean;
  /** Ejercicio "por tiempo" (flexibilidad/mente-cuerpo/rehabilitación): usa duración en vez de reps. */
  timed: boolean;
}

export interface LibraryFilters {
  muscles: string[];
  difficulties: string[];
  moduleName: string;
}

export interface LibraryQuery {
  q?: string;
  muscle?: string;
  equipment?: string;
  /** 1 (principiante) … 5 (experto). */
  difficulty?: number;
  favoritesOnly?: boolean;
  limit?: number;
  offset?: number;
}

export async function fetchLibraryFilters(): Promise<LibraryFilters> {
  const { data } = await api.get<LibraryFilters>("/library/filters");
  return { muscles: data.muscles ?? [], difficulties: data.difficulties ?? [], moduleName: data.moduleName ?? "" };
}

export async function searchLibrary(query: LibraryQuery): Promise<LibraryExercise[]> {
  const { data } = await api.get<LibraryExercise[]>("/library/exercises", { params: query });
  return data ?? [];
}

export async function fetchLibraryDetail(exerciseId: string): Promise<ExerciseDetail> {
  const { data } = await api.get<ExerciseDetail>(`/library/exercises/${exerciseId}`);
  return normalizeDetail(data);
}

export async function addToRoutine(exerciseId: string): Promise<Routine> {
  const { data } = await api.post<Routine>("/library/add-to-routine", { exerciseId });
  invalidateUserContext();
  return normalizeRoutine(data);
}

export async function addFavorite(exerciseId: string): Promise<void> {
  await api.post(`/library/exercises/${exerciseId}/favorite`);
}

export async function removeFavorite(exerciseId: string): Promise<void> {
  await api.delete(`/library/exercises/${exerciseId}/favorite`);
}
