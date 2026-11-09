import { useFocusEffect } from "expo-router";
import { useCallback, useEffect, useRef, useState } from "react";

import { extractErrorMessage } from "../services/api";

export interface Loader<T> {
  data: T | null;
  error: string | null;
  /** true solo en la primera carga (sin datos todavía). */
  loading: boolean;
  /** true mientras se recarga con datos ya visibles (pull-to-refresh). */
  refreshing: boolean;
  reload: () => Promise<void>;
  setData: (updater: T | ((prev: T | null) => T)) => void;
}

/**
 * Carga datos asíncronos con estados de carga/error y recarga manual.
 * Vuelve a cargar cuando cambia cualquier valor de `deps` (p. ej. la versión del mundo activo).
 */
export function useLoader<T>(fetcher: () => Promise<T>, deps: readonly unknown[] = []): Loader<T> {
  const [data, setDataState] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const fetcherRef = useRef(fetcher);
  const hasData = useRef(false);
  const requestId = useRef(0);

  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  const reload = useCallback(async () => {
    const id = ++requestId.current;
    if (hasData.current) setRefreshing(true);
    else setLoading(true);
    try {
      const result = await fetcherRef.current();
      if (id !== requestId.current) return;
      hasData.current = true;
      setDataState(result);
      setError(null);
    } catch (e) {
      if (id !== requestId.current) return;
      setError(extractErrorMessage(e));
    } finally {
      if (id === requestId.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, []);

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  const setData = useCallback((updater: T | ((prev: T | null) => T)) => {
    setDataState((prev) => (typeof updater === "function" ? (updater as (p: T | null) => T)(prev) : updater));
  }, []);

  return { data, error, loading, refreshing, reload, setData };
}

/** Recarga al volver a la pantalla (no en el primer enfoque, que ya cubre useLoader). */
export function useReloadOnFocus(reload: () => Promise<void>): void {
  const first = useRef(true);
  useFocusEffect(
    useCallback(() => {
      if (first.current) {
        first.current = false;
        return;
      }
      reload();
    }, [reload]),
  );
}
