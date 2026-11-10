import React, { createContext, useContext, useEffect, useRef, useState } from "react";
import AsyncStorage from "@react-native-async-storage/async-storage";

import { setUnauthorizedHandler } from "../services/api";
import { AUTH_TOKEN_KEY, AUTH_USER_KEY } from "../constants/config";
import { flushAndResetAnalytics } from "../services/analytics";
import * as authService from "../services/authService";
import { clearCache } from "../services/cache";
import { resetChatSession } from "../services/chatService";
import { AuthResponse, RegisterRequest } from "../services/types";
import { useTheme } from "../theme";

export interface AuthUser {
  userId: string;
  email: string;
  name: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isLoading: boolean;
  /** true si la sesión se cerró porque el servidor la dio por vencida o revocada (401). */
  sessionExpired: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  /** Adopta un token nuevo emitido por el servidor (p. ej. tras cambiar la contraseña). */
  adoptSession: (response: AuthResponse) => Promise<void>;
  /** Actualiza datos locales del usuario (p. ej. el nombre tras editarlo en Ajustes). */
  updateUser: (patch: Partial<Pick<AuthUser, "name">>) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);
  const { setWorldColor } = useTheme();
  const tokenRef = useRef<string | null>(null);
  useEffect(() => {
    tokenRef.current = token;
  });

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
          setUser({ userId: parsed.userId, email: parsed.email, name: parsed.name });
        }
      } finally {
        setIsLoading(false);
      }
    })();
  }, []);

  // Si el servidor responde 401 en una llamada autenticada, la sesión venció o se revocó: se cierra
  // y la pantalla de entrada avisa (en vez de expulsar en silencio). Lo pendiente queda guardado.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      if (!tokenRef.current) return;
      tokenRef.current = null;
      setSessionExpired(true);
      logout();
    });
    return () => setUnauthorizedHandler(null);
  });

  async function persistSession(authToken: string, authUser: AuthUser) {
    await AsyncStorage.setItem(AUTH_TOKEN_KEY, authToken);
    await AsyncStorage.setItem(AUTH_USER_KEY, JSON.stringify(authUser));
    setSessionExpired(false);
    setToken(authToken);
    setUser(authUser);
  }

  async function login(email: string, password: string) {
    const response = await authService.login(email, password);
    await persistSession(response.token, {
      userId: response.userId,
      email: response.email,
      name: response.name,
    });
  }

  async function register(request: RegisterRequest) {
    const response = await authService.register(request);
    await persistSession(response.token, {
      userId: response.userId,
      email: response.email,
      name: response.name,
    });
  }

  async function adoptSession(response: AuthResponse) {
    await persistSession(response.token, { userId: response.userId, email: response.email, name: response.name });
  }

  async function logout() {
    // Lo último de la analítica sale con el token que aún sirve (si sirve) y se descarta el resto.
    await flushAndResetAnalytics();
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

  return (
    <AuthContext.Provider value={{ user, token, isLoading, sessionExpired, login, register, logout, adoptSession, updateUser }}>
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
