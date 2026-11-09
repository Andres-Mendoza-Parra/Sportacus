import React, { createContext, useContext, useEffect, useState } from "react";
import AsyncStorage from "@react-native-async-storage/async-storage";

import { setUnauthorizedHandler } from "../services/api";
import { AUTH_TOKEN_KEY, AUTH_USER_KEY } from "../constants/config";
import * as authService from "../services/authService";
import { clearCache } from "../services/cache";
import { resetChatSession } from "../services/chatService";
import { useTheme } from "../theme";

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
  /** Marca localmente el recibimiento como completo (el servidor lo marca al inscribirse en el primer mundo). */
  markOnboarded: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const { setWorldColor } = useTheme();

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
          const parsed = JSON.parse(storedUser) as AuthUser;
          setUser({ ...parsed, onboardingCompleted: parsed.onboardingCompleted === true });
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
    setWorldColor(null);
    setToken(null);
    setUser(null);
  }

  async function updateUser(patch: Partial<Pick<AuthUser, "name">>) {
    if (!user) return;
    const next = { ...user, ...patch };
    setUser(next);
    await AsyncStorage.setItem(AUTH_USER_KEY, JSON.stringify(next));
  }

  async function markOnboarded() {
    if (!user || user.onboardingCompleted) return;
    const next = { ...user, onboardingCompleted: true };
    setUser(next);
    await AsyncStorage.setItem(AUTH_USER_KEY, JSON.stringify(next));
  }

  return (
    <AuthContext.Provider
      value={{ user, token, isLoading, login, register, logout, updateUser, markOnboarded }}
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
