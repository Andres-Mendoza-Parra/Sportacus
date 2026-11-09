import React, { useState } from "react";
import { Text, View } from "react-native";

import { formatPace } from "../../../constants/labels";
import { usePausableCountdown } from "../../../hooks/usePausableCountdown";
import { useTheme } from "../../../theme";
import { formatClock } from "../../ui/useCountdown";
import { BigButton, BigClock, BlockRendererProps } from "./shared";

/** Trabajo continuo durante durationS (movilidad, rodaje…), con pausa; registra el tiempo hecho. */
export default function TimeBlock({ block, entries, onChange, speak }: BlockRendererProps) {
  const { colors } = useTheme();
  const p = block.prescription;
  const target = p.durationS ?? 300;
  const entry = entries[0] ?? { completed: false };
  const [started, setStarted] = useState(false);

  function complete(seconds: number) {
    onChange([{ ...entry, completed: true, durationS: Math.max(0, Math.round(seconds)) }]);
    setStarted(false);
  }

  const timer = usePausableCountdown(() => {
    speak("¡Bloque terminado!");
    complete(target);
  });

  const pace = formatPace(p.paceSecPerKm);
  const showClock = started ? timer.remaining : entry.completed ? 0 : target;

  return (
    <View style={{ gap: 12 }}>
      <BigClock value={formatClock(showClock)} caption={entry.completed ? `Hecho: ${formatClock(entry.durationS ?? 0)}` : "Tiempo restante"} />
      {p.intensity || pace ? (
        <Text style={{ textAlign: "center", fontSize: 14, color: colors.text, fontWeight: "600" }}>
          {[p.intensity ? `Intensidad: ${p.intensity}` : null, pace ? `Ritmo ${pace}` : null].filter(Boolean).join(" · ")}
        </Text>
      ) : null}
      <View style={{ flexDirection: "row", gap: 10 }}>
        {!started ? (
          <BigButton
            label={entry.completed ? "↺ Repetir" : "▶ Empezar"}
            onPress={() => {
              setStarted(true);
              timer.start(target);
              speak("Empezamos");
            }}
          />
        ) : (
          <>
            <BigButton label={timer.paused ? "▶ Reanudar" : "⏸ Pausa"} tone="ghost" onPress={timer.paused ? timer.resume : timer.pause} />
            <BigButton
              label="✓ Terminar"
              onPress={() => {
                const done = target - timer.remaining;
                timer.reset();
                complete(done);
              }}
            />
          </>
        )}
      </View>
      {!started && !entry.completed ? (
        <BigButton label="Marcar como hecho sin cronómetro" tone="ghost" onPress={() => complete(target)} />
      ) : null}
    </View>
  );
}
