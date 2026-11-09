import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Cronómetro ascendente basado en marcas de tiempo (no acumula error si la app se pausa un instante).
 * `elapsed` está en segundos con décimas.
 */
export function useStopwatch(tickMs = 100) {
  const [elapsed, setElapsed] = useState(0);
  const [running, setRunning] = useState(false);
  const startedAt = useRef<number | null>(null);
  const accumulated = useRef(0);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  const clear = useCallback(() => {
    if (timer.current) clearInterval(timer.current);
    timer.current = null;
  }, []);

  const start = useCallback(() => {
    if (startedAt.current !== null) return;
    startedAt.current = Date.now();
    setRunning(true);
    timer.current = setInterval(() => {
      if (startedAt.current === null) return;
      setElapsed(accumulated.current + (Date.now() - startedAt.current) / 1000);
    }, tickMs);
  }, [tickMs]);

  /** Pausa y devuelve los segundos acumulados. */
  const stop = useCallback((): number => {
    clear();
    if (startedAt.current !== null) {
      accumulated.current += (Date.now() - startedAt.current) / 1000;
      startedAt.current = null;
    }
    setRunning(false);
    setElapsed(accumulated.current);
    return accumulated.current;
  }, [clear]);

  const reset = useCallback(() => {
    clear();
    startedAt.current = null;
    accumulated.current = 0;
    setRunning(false);
    setElapsed(0);
  }, [clear]);

  useEffect(() => clear, [clear]);

  return { elapsed, running, start, stop, reset };
}

/** 75.4 → "1:15.4" (con décimas) o "1:15". */
export function formatStopwatch(seconds: number, tenths = true): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  const base = `${m}:${String(s).padStart(2, "0")}`;
  return tenths ? `${base}.${Math.floor((seconds * 10) % 10)}` : base;
}
