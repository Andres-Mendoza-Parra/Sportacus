import AsyncStorage from "@react-native-async-storage/async-storage";

import { AUTH_USER_KEY } from "../constants/config";

/**
 * Preferencias locales por usuario (favoritos, plan semanal, modo de cuidado…).
 * Se guardan bajo "pref:<userId>:<clave>" y se borran al cerrar sesión.
 */
async function storageKey(name: string): Promise<string> {
  let userId = "anon";
  try {
    const raw = await AsyncStorage.getItem(AUTH_USER_KEY);
    if (raw) userId = (JSON.parse(raw) as { userId?: string }).userId ?? "anon";
  } catch {
    // sin sesión legible: se usa la clave anónima
  }
  return `pref:${userId}:${name}`;
}

export async function loadPref<T>(name: string, fallback: T): Promise<T> {
  try {
    const raw = await AsyncStorage.getItem(await storageKey(name));
    return raw ? ({ ...(typeof fallback === "object" && !Array.isArray(fallback) ? fallback : {}), ...JSON.parse(raw) } as T) : fallback;
  } catch {
    return fallback;
  }
}

/** Igual que loadPref pero sin mezclar con el valor por defecto (para listas y valores simples). */
export async function loadRawPref<T>(name: string, fallback: T): Promise<T> {
  try {
    const raw = await AsyncStorage.getItem(await storageKey(name));
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

export async function savePref<T>(name: string, value: T): Promise<void> {
  await AsyncStorage.setItem(await storageKey(name), JSON.stringify(value));
}
