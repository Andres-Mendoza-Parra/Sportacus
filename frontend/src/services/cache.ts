import AsyncStorage from "@react-native-async-storage/async-storage";
import axios from "axios";

/**
 * Caché local para usar la app sin conexión: cada petición exitosa guarda su respuesta y,
 * si después falla por red, se devuelve la última copia guardada.
 */
const PREFIX = "cache:";
const servedFromCache = new Set<string>();

function isNetworkError(error: unknown): boolean {
  return axios.isAxiosError(error) && !error.response;
}

export async function cachedRequest<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
  try {
    const fresh = await fetcher();
    servedFromCache.delete(key);
    AsyncStorage.setItem(PREFIX + key, JSON.stringify(fresh)).catch(() => {});
    return fresh;
  } catch (error) {
    if (isNetworkError(error)) {
      const raw = await AsyncStorage.getItem(PREFIX + key).catch(() => null);
      if (raw) {
        servedFromCache.add(key);
        return JSON.parse(raw) as T;
      }
    }
    throw error;
  }
}

/** ¿La última lectura de esta clave salió de la copia local (sin conexión)? */
export function isServedFromCache(key: string): boolean {
  return servedFromCache.has(key);
}

/** Borra las copias locales (se usa al cerrar sesión para no mezclar datos entre cuentas). */
export async function clearCache(): Promise<void> {
  servedFromCache.clear();
  const keys = await AsyncStorage.getAllKeys().catch(() => [] as readonly string[]);
  const own = keys.filter((k) => k.startsWith(PREFIX) || k.startsWith("pref:"));
  if (own.length > 0) await AsyncStorage.multiRemove(own).catch(() => {});
}
