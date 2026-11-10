import { api } from "./api";
import { AccountView, Answers, AuthResponse, FormSpec, ProfilePatch } from "./types";

/** Cuenta, perfil común y estado del recibimiento (docs/16 §3 y §7). */
export async function fetchAccount(): Promise<AccountView> {
  const { data } = await api.get<AccountView>("/profile");
  return data;
}

export async function fetchProfileForm(): Promise<FormSpec> {
  const { data } = await api.get<FormSpec>("/profile/form");
  return data;
}

export async function saveProfileForm(answers: Answers): Promise<AccountView> {
  const { data } = await api.post<AccountView>("/profile/form", answers);
  return data;
}

export async function updateProfile(patch: ProfilePatch): Promise<AccountView> {
  const { data } = await api.patch<AccountView>("/profile", patch);
  return data;
}

/** Cambia la contraseña. El servidor revoca los tokens anteriores y devuelve uno nuevo. */
export async function changePassword(currentPassword: string, newPassword: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>("/profile/password", { currentPassword, newPassword });
  return data;
}

export async function exportProfileData(): Promise<unknown> {
  const { data } = await api.get<unknown>("/profile/export");
  return data;
}

/** Borrado total de la cuenta (el backend no pide cuerpo). */
export async function deleteAccount(): Promise<void> {
  await api.delete("/profile");
}

/** Marca el recorrido inicial como visto (lo termine o lo salte). */
export async function completeTutorial(): Promise<void> {
  await api.post("/profile/tutorial", { completed: true });
}

/**
 * Consentimiento para tratar datos de salud (área "salud"). Sin él, el chequeo de salud y el check-in
 * responden 403. Revocarlo BORRA chequeos, condiciones, respuestas de check-in y métricas de bienestar.
 */
export async function setHealthDataConsent(granted: boolean): Promise<void> {
  await api.post("/profile/consent/salud", { granted });
}
