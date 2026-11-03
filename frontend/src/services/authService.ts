import { api } from "./api";

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  name: string;
  onboardingCompleted: boolean;
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>("/auth/login", { email, password });
  return data;
}

export async function register(name: string, email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>("/auth/register", { name, email, password });
  return data;
}

export async function completeOnboarding(): Promise<void> {
  await api.post("/auth/onboarding/complete");
}
