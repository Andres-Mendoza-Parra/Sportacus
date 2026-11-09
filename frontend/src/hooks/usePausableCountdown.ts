import { useCallback, useState } from "react";

import { useCountdown } from "../components/ui/useCountdown";

/** Cuenta regresiva con pausa y reanudación (sobre useCountdown, que se basa en la hora de fin). */
export function usePausableCountdown(onFinish?: () => void) {
  const countdown = useCountdown(onFinish);
  const [paused, setPaused] = useState<number | null>(null);

  const start = useCallback(
    (seconds: number) => {
      setPaused(null);
      countdown.start(seconds);
    },
    [countdown],
  );

  const pause = useCallback(() => {
    if (!countdown.running) return;
    setPaused(countdown.remaining);
    countdown.stop();
  }, [countdown]);

  const resume = useCallback(() => {
    if (paused === null) return;
    const left = paused;
    setPaused(null);
    if (left > 0) countdown.start(left);
  }, [countdown, paused]);

  const reset = useCallback(() => {
    setPaused(null);
    countdown.stop();
  }, [countdown]);

  return {
    remaining: paused ?? countdown.remaining,
    running: countdown.running,
    paused: paused !== null,
    start,
    pause,
    resume,
    reset,
  };
}
