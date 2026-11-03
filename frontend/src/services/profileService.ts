import { api } from "./api";

export interface ProfileModule {
  name: string;
  status: "activo" | "disponible";
}

export interface ProfileSuggestion {
  questionnaireKey: string;
  title: string;
  reason: string;
  impactPct: number;
}

export interface ProfileData {
  name: string;
  email: string;
  age: number | null;
  weightKg: number | null;
  heightCm: number | null;
  country: string | null;
  city: string | null;
  language: string | null;
  memberSince: string | null;
  profileStrengthPct: number;
  suggestions: ProfileSuggestion[];
  activeModule: string | null;
  modules: ProfileModule[];
}

export interface ProfileUpdate {
  name?: string;
  country?: string;
  city?: string;
  language?: string;
}

function normalize(raw: Partial<ProfileData>): ProfileData {
  return {
    name: raw.name ?? "",
    email: raw.email ?? "",
    age: raw.age ?? null,
    weightKg: raw.weightKg ?? null,
    heightCm: raw.heightCm ?? null,
    country: raw.country ?? null,
    city: raw.city ?? null,
    language: raw.language ?? null,
    memberSince: raw.memberSince ?? null,
    profileStrengthPct: raw.profileStrengthPct ?? 0,
    suggestions: raw.suggestions ?? [],
    activeModule: raw.activeModule ?? null,
    modules: raw.modules ?? [],
  };
}

export async function fetchProfile(): Promise<ProfileData> {
  const { data } = await api.get<ProfileData>("/profile");
  return normalize(data);
}

export async function updateProfile(update: ProfileUpdate): Promise<ProfileData> {
  const { data } = await api.patch<ProfileData>("/profile", update);
  return normalize(data);
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await api.post("/profile/password", { currentPassword, newPassword });
}

export async function exportProfileData(): Promise<unknown> {
  const { data } = await api.get<unknown>("/profile/export");
  return data;
}

export async function deleteAccount(password: string): Promise<void> {
  await api.delete("/profile", { data: { password } });
}
