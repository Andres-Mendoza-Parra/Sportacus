import React from "react";
import { Text, View } from "react-native";

import { useTheme } from "../../../theme";
import PressableScale from "../../ui/PressableScale";

const RPE: Record<number, string> = {
  1: "Muy suave: casi sin esfuerzo",
  2: "Suave: podrías seguir horas",
  3: "Ligero: respiras con calma",
  4: "Moderado: empiezas a sudar",
  5: "Algo duro: hablar cuesta un poco",
  6: "Duro: frases cortas",
  7: "Muy duro: solo unas palabras",
  8: "Intenso: quedaban 1–2 repeticiones",
  9: "Casi máximo: no quedaba nada",
  10: "Máximo: el esfuerzo más alto posible",
};

interface Props {
  value: number | undefined;
  onChange: (value: number) => void;
}

/** Esfuerzo percibido (RPE 1–10) con la descripción de cada nivel. */
export default function RpePicker({ value, onChange }: Props) {
  const { colors, shadow } = useTheme();
  return (
    <View style={[{ backgroundColor: colors.card, borderRadius: 18, padding: 16, gap: 12 }, shadow]}>
      <Text style={{ fontSize: 16, fontWeight: "700", color: colors.text }}>¿Qué tan dura fue la sesión?</Text>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
        {Object.keys(RPE).map((k) => {
          const n = Number(k);
          const selected = value === n;
          const tone = n <= 4 ? colors.success : n <= 7 ? colors.warn : colors.danger;
          return (
            <PressableScale
              key={k}
              onPress={() => onChange(n)}
              accessibilityRole="button"
              accessibilityLabel={`Esfuerzo ${n}: ${RPE[n]}`}
              accessibilityState={{ selected }}
              style={{
                width: 48,
                height: 48,
                borderRadius: 14,
                alignItems: "center",
                justifyContent: "center",
                backgroundColor: selected ? tone : colors.cardAlt,
                borderWidth: 1.5,
                borderColor: selected ? tone : colors.border,
              }}
            >
              <Text style={{ fontSize: 17, fontWeight: "800", color: selected ? "#fff" : colors.text }}>{n}</Text>
            </PressableScale>
          );
        })}
      </View>
      <Text style={{ fontSize: 13, color: colors.textMuted, minHeight: 18 }}>
        {value ? `RPE ${value} · ${RPE[value]}` : "Toca un número del 1 (muy suave) al 10 (máximo)."}
      </Text>
    </View>
  );
}
