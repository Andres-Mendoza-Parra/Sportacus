import Slider from "@react-native-community/slider";
import React from "react";
import { Text, View } from "react-native";

import { useTheme } from "../../../theme";
import { painColor } from "../format";

export interface PainSliderProps {
  /** 0–10; undefined = sin responder (se muestra 0 atenuado). */
  value: number | undefined;
  onChange: (value: number) => void;
  label?: string;
  /** Límite de dolor del bloque: se marca en el texto si se supera. */
  max?: number | null;
  compact?: boolean;
}

function describe(pain: number): string {
  if (pain === 0) return "Sin dolor";
  if (pain <= 3) return "Leve";
  if (pain <= 5) return "Moderado";
  if (pain <= 7) return "Intenso";
  return "Muy intenso";
}

/** Escala de dolor 0–10 con color (verde ≤ 3, amarillo 4–5, rojo > 5). */
export default function PainSlider({ value, onChange, label = "¿Cuánto dolor tienes ahora?", max, compact }: PainSliderProps) {
  const { colors, shadow } = useTheme();
  const shown = value ?? 0;
  const color = painColor(shown, colors);
  const over = max != null && value != null && value > max;

  return (
    <View
      style={[
        { backgroundColor: colors.card, borderRadius: 18, padding: compact ? 12 : 18, gap: 6 },
        compact ? { borderWidth: 1, borderColor: colors.border } : shadow,
      ]}
    >
      <Text style={{ fontSize: compact ? 13 : 16, fontWeight: "700", color: colors.text }}>{label}</Text>
      <View style={{ flexDirection: "row", alignItems: "baseline", gap: 8, justifyContent: "center" }}>
        <Text style={{ fontSize: compact ? 28 : 44, fontWeight: "800", color, opacity: value == null ? 0.45 : 1 }}>{shown}</Text>
        <Text style={{ fontSize: 13, color: colors.textMuted }}>/10 · {describe(shown)}</Text>
      </View>
      <Slider
        accessibilityLabel={label}
        minimumValue={0}
        maximumValue={10}
        step={1}
        value={shown}
        onValueChange={(v) => onChange(Math.round(v))}
        minimumTrackTintColor={color}
        maximumTrackTintColor={colors.track}
        thumbTintColor={color}
        style={{ width: "100%", height: 40 }}
      />
      <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
        <Text style={{ fontSize: 11, color: colors.textFaint }}>0 · nada</Text>
        <Text style={{ fontSize: 11, color: colors.textFaint }}>10 · insoportable</Text>
      </View>
      {over ? (
        <Text style={{ fontSize: 12, color: colors.danger, fontWeight: "600" }}>
          Superaste el límite de {max}/10: detente o cambia el ejercicio.
        </Text>
      ) : null}
    </View>
  );
}
