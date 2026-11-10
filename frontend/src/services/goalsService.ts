import { api } from "./api";
import { GoalView } from "./types";

/** Metas de un mundo (sin `world`, las del mundo activo). */
export async function fetchGoals(world?: string | null): Promise<GoalView[]> {
  const { data } = await api.get<GoalView[]>("/world/goals", { params: world ? { world } : undefined });
  return data;
}

/** Marca la meta como lograda (suma el logro "meta_<goalKey>"). */
export async function completeGoal(id: string): Promise<GoalView> {
  const { data } = await api.post<GoalView>(`/world/goals/${encodeURIComponent(id)}/complete`);
  return data;
}

/** Vuelve a dejar la meta activa. */
export async function reopenGoal(id: string): Promise<GoalView> {
  const { data } = await api.post<GoalView>(`/world/goals/${encodeURIComponent(id)}/reopen`);
  return data;
}
