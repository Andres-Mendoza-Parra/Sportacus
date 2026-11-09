import { api } from "./api";
import { AccountView, Answers, FormSpec, ProfilePatch } from "./types";

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

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await api.post("/profile/password", { currentPassword, newPassword });
}

export async function exportProfileData(): Promise<unknown> {
  const { data } = await api.get<unknown>("/profile/export");
  return data;
}

/** Borrado total de la cuenta (el backend no pide cuerpo). */
export async function deleteAccount(): Promise<void> {
  await api.delete("/profile");
}
