import React, { createContext, useContext, useEffect, useState } from "react";
import AsyncStorage from "@react-native-async-storage/async-storage";

import { setUnauthorizedHandler } from "../services/api";
import { AUTH_TOKEN_KEY, AUTH_USER_KEY } from "../constants/config";
import * as authService from "../services/authService";
import { clearCache } from "../services/cache";
import { resetChatSession } from "../services/chatService";

interface AuthUser {
  userId: string;
  email: string;
  name: string;
  onboardingCompleted: boolean;
}

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Actualiza datos locales del usuario (p. ej. el nombre tras editarlo en Ajustes). */
  updateUser: (patch: Partial<Pick<AuthUser, "name">>) => Promise<void>;
  /** Marca el onboarding (elección de módulo) como completo, en el servidor y localmente. */
  completeOnboarding: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Al abrir la app, intenta restaurar la sesión guardada.
  useEffect(() => {
    (async () => {
      try {
        const [storedToken, storedUser] = await Promise.all([
          AsyncStorage.getItem(AUTH_TOKEN_KEY),
          AsyncStorage.getItem(AUTH_USER_KEY),
        ]);
        if (storedToken && storedUser) {
          setToken(storedToken);
          const parsed = JSON.parse(storedUser);
          // Sesiones guardadas antes de este campo: se asumen ya completas (no son cuentas nuevas).
          setUser({ onboardingCompleted: true, ...parsed });
        }
      } finally {
        setIsLoading(false);
      }
    })();
  }, []);

  // Si el servidor responde 401 en una llamada autenticada, la sesión caducó: se cierra.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      logout();
    });
    return () => setUnauthorizedHandler(null);
  });

  async function persistSession(authToken: string, authUser: AuthUser) {
    await AsyncStorage.setItem(AUTH_TOKEN_KEY, authToken);
    await AsyncStorage.setItem(AUTH_USER_KEY, JSON.stringify(authUser));
    setToken(authToken);
    setUser(authUser);
  }

  async function login(email: string, password: string) {
    const response = await authService.login(email, password);
    await persistSession(response.token, {
      userId: response.userId,
      email: response.email,
      name: response.name,
      onboardingCompleted: response.onboardingCompleted,
    });
  }

  async function register(name: string, email: string, password: string) {
    const response = await authService.register(name, email, password);
    await persistSession(response.token, {
      userId: response.userId,
      email: response.email,
      name: response.name,
      onboardingCompleted: response.onboardingCompleted,
    });
  }

  async function logout() {
    await AsyncStorage.multiRemove([AUTH_TOKEN_KEY, AUTH_USER_KEY]);
    // Los datos guardados en el dispositivo pertenecen a la cuenta que sale.
    resetChatSession();
    await clearCache();
    setToken(null);
    setUser(null);
  }

  async function updateUser(patch: Partial<Pick<AuthUser, "name">>) {
    if (!user) return;
    const next = { ...user, ...patch };
    setUser(next);
    await AsyncStorage.setItem(AUTH_USER_KEY, JSON.stringify(next));
  }

  async function completeOnboarding() {
    await authService.completeOnboarding();
    if (!user) return;
    const next = { ...user, onboardingCompleted: true };
    setUser(next);
    await AsyncStorage.setItem(AUTH_USER_KEY, JSON.stringify(next));
  }

  return (
    <AuthContext.Provider
      value={{ user, token, isLoading, login, register, logout, updateUser, completeOnboarding }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth debe usarse dentro de un <AuthProvider>");
  }
  return context;
}
