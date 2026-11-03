import { useCallback, useEffect, useRef, useState } from "react";
import { Vibration } from "react-native";

/**
 * Cuenta regresiva basada en la hora de fin (no acumula errores si la app se pausa un instante).
 * Vibra al terminar y avisa con onFinish.
 */
export function useCountdown(onFinish?: () => void) {
  const [remaining, setRemaining] = useState(0);
  const [total, setTotal] = useState(0);
  const endsAt = useRef<number | null>(null);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);
  const finishRef = useRef(onFinish);
  useEffect(() => {
    finishRef.current = onFinish;
  });

  const clear = useCallback(() => {
    if (timer.current) clearInterval(timer.current);
    timer.current = null;
    endsAt.current = null;
  }, []);

  const stop = useCallback(() => {
    clear();
    setRemaining(0);
    setTotal(0);
  }, [clear]);

  const start = useCallback(
    (seconds: number) => {
      clear();
      endsAt.current = Date.now() + seconds * 1000;
      setTotal(seconds);
      setRemaining(seconds);
      timer.current = setInterval(() => {
        if (endsAt.current == null) return;
        const left = Math.max(0, Math.ceil((endsAt.current - Date.now()) / 1000));
        setRemaining(left);
        if (left <= 0) {
          clear();
          setTotal(0);
          try {
            Vibration.vibrate([0, 300, 150, 300]);
          } catch {
            // sin vibración disponible (web)
          }
          finishRef.current?.();
        }
      }, 250);
    },
    [clear],
  );

  useEffect(() => clear, [clear]);

  return { remaining, total, running: total > 0, start, stop };
}

export function formatClock(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}
