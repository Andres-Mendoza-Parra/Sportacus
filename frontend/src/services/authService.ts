import { api } from "./api";
import { AuthResponse, RegisterRequest } from "./types";

export type { AuthResponse, RegisterRequest };

export async function login(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>("/auth/login", { email, password });
  return data;
}

export async function register(request: RegisterRequest): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>("/auth/register", request);
  return data;
}
