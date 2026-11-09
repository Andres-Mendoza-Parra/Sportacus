import React from "react";
import { Text, View } from "react-native";

import { formatDistance, formatPace } from "../../../constants/labels";
import { formatStopwatch, useStopwatch } from "../../../hooks/useStopwatch";
import { useTheme } from "../../../theme";
import NumberField from "../NumberField";
import { BigButton, BigClock, BlockRendererProps } from "./shared";

/** Distancia objetivo a un ritmo: cronómetro + registro de la distancia (km) y el tiempo. */
export default function DistanceBlock({ block, entries, onChange, speak }: BlockRendererProps) {
  const { colors } = useTheme();
  const p = block.prescription;
  const entry = entries[0] ?? { completed: false };
  const watch = useStopwatch(500);
  const pace = formatPace(p.paceSecPerKm);
  const km = entry.distanceM != null ? entry.distanceM / 1000 : undefined;
  const seconds = watch.elapsed > 0 ? watch.elapsed : entry.durationS ?? 0;
  const actualPace = km && seconds > 0 ? formatPace(seconds / km) : null;

  return (
    <View style={{ gap: 12 }}>
      <Text style={{ textAlign: "center", fontSize: 15, color: colors.text, fontWeight: "700" }}>
        Objetivo: {formatDistance(p.distanceM)}
        {pace ? ` a ${pace}` : ""}
      </Text>
      <BigClock value={formatStopwatch(seconds, false)} caption={watch.running ? "En marcha" : entry.completed ? "Tiempo registrado" : "Cronómetro"} />
      <View style={{ flexDirection: "row", gap: 10 }}>
        {watch.running ? (
          <BigButton
            label="⏹ Terminar"
            tone="danger"
            onPress={() => {
              const total = watch.stop();
              speak("Terminado");
              onChange([{ ...entry, durationS: Math.round(total), completed: true }]);
            }}
          />
        ) : (
          <BigButton
            label={watch.elapsed > 0 ? "▶ Continuar" : "▶ Empezar"}
            onPress={() => {
              watch.start();
              speak("Adelante");
            }}
          />
        )}
      </View>
      <NumberField
        label="Distancia recorrida"
        unit="km"
        value={km}
        onChange={(v) => onChange([{ ...entry, distanceM: v == null ? undefined : Math.round(v * 1000) }])}
      />
      {actualPace ? <Text style={{ textAlign: "center", fontSize: 13, color: colors.textMuted }}>Tu ritmo: {actualPace}</Text> : null}
      {!entry.completed && !watch.running ? (
        <BigButton
          label="Marcar como hecho"
          tone="ghost"
          onPress={() => onChange([{ ...entry, durationS: watch.elapsed > 0 ? Math.round(watch.elapsed) : entry.durationS, completed: true }])}
        />
      ) : null}
    </View>
  );
}
