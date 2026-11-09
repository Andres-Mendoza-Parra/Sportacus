import * as Speech from "expo-speech";
import { useCallback, useEffect, useRef, useState } from "react";

/** Indicaciones por voz (expo-speech) con interruptor de silencio. */
export function useVoice(initiallyEnabled = true) {
  const [enabled, setEnabled] = useState(initiallyEnabled);
  const enabledRef = useRef(enabled);

  useEffect(() => {
    enabledRef.current = enabled;
    if (!enabled) Speech.stop();
  }, [enabled]);

  useEffect(
    () => () => {
      Speech.stop();
    },
    [],
  );

  const speak = useCallback((text: string) => {
    if (!enabledRef.current) return;
    try {
      Speech.stop();
      Speech.speak(text, { language: "es-ES", rate: 0.95 });
    } catch {
      // sin motor de voz disponible
    }
  }, []);

  const toggle = useCallback(() => setEnabled((e) => !e), []);

  return { enabled, toggle, speak };
}
