import { useEffect, useRef, useState } from "react";

/**
 * Valor numérico que sube suavemente (ease-out) desde el anterior hasta `target`.
 * Se usa para contadores y para animar el radar sin depender de worklets.
 */
export function useAnimatedNumber(target: number, duration = 900): number {
  const [value, setValue] = useState(0);
  const fromRef = useRef(0);

  useEffect(() => {
    const from = fromRef.current;
    const start = Date.now();
    let raf: ReturnType<typeof requestAnimationFrame>;

    const tick = () => {
      const t = Math.min(1, (Date.now() - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      const next = from + (target - from) * eased;
      fromRef.current = next;
      setValue(next);
      if (t < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target, duration]);

  return value;
}
