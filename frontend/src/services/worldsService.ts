import { api } from "./api";
import { Answers, DiscoveryResult, EnrollResponse, FormSpec, MyWorld, WorldCard } from "./types";

/** Catálogo de mundos, descubrimiento, inscripción y cambio de mundo activo (docs/16 §3–4). */
export async function fetchWorlds(): Promise<WorldCard[]> {
  const { data } = await api.get<WorldCard[]>("/worlds");
  return data;
}

export async function fetchDiscoveryForm(): Promise<FormSpec> {
  const { data } = await api.get<FormSpec>("/worlds/discovery/form");
  return data;
}

export async function runDiscovery(answers: Answers): Promise<DiscoveryResult> {
  const { data } = await api.post<DiscoveryResult>("/worlds/discovery", answers);
  return data;
}

export async function fetchWorldOnboarding(key: string): Promise<FormSpec> {
  const { data } = await api.get<FormSpec>(`/worlds/${encodeURIComponent(key)}/onboarding`);
  return data;
}

/** Crea el plan inicial y deja el mundo como activo. */
export async function enrollWorld(key: string, answers: Answers): Promise<EnrollResponse> {
  const { data } = await api.post<EnrollResponse>(`/worlds/${encodeURIComponent(key)}/enroll`, answers, { timeout: 30000 });
  return data;
}

export async function fetchMyWorlds(): Promise<MyWorld[]> {
  const { data } = await api.get<MyWorld[]>("/me/worlds");
  return data;
}

export async function activateWorld(key: string): Promise<MyWorld> {
  const { data } = await api.post<MyWorld>(`/me/worlds/${encodeURIComponent(key)}/activate`);
  return data;
}
