import React from "react";
import { Text, View } from "react-native";

import { useTheme } from "../../../theme";
import PressableScale from "../../ui/PressableScale";

const MOODS = [
  { value: 1, icon: "😫", label: "Muy mal" },
  { value: 2, icon: "😕", label: "Mal" },
  { value: 3, icon: "😐", label: "Normal" },
  { value: 4, icon: "🙂", label: "Bien" },
  { value: 5, icon: "😄", label: "Genial" },
];

interface Props {
  value: number | undefined;
  onChange: (value: number) => void;
  label?: string;
}

/** Ánimo 1–5 con emojis. */
export default function MoodPicker({ value, onChange, label = "¿Cómo te sientes?" }: Props) {
  const { colors, shadow } = useTheme();
  return (
    <View style={[{ backgroundColor: colors.card, borderRadius: 18, padding: 16, gap: 12 }, shadow]}>
      <Text style={{ fontSize: 16, fontWeight: "700", color: colors.text }}>{label}</Text>
      <View style={{ flexDirection: "row", justifyContent: "space-between", gap: 6 }}>
        {MOODS.map((m) => {
          const selected = value === m.value;
          return (
            <PressableScale
              key={m.value}
              onPress={() => onChange(m.value)}
              accessibilityRole="button"
              accessibilityLabel={m.label}
              accessibilityState={{ selected }}
              style={{
                flex: 1,
                alignItems: "center",
                paddingVertical: 10,
                borderRadius: 14,
                borderWidth: 1.5,
                borderColor: selected ? colors.primary : colors.border,
                backgroundColor: selected ? colors.primarySoft : colors.cardAlt,
              }}
            >
              <Text style={{ fontSize: 26, opacity: value == null || selected ? 1 : 0.5 }}>{m.icon}</Text>
              <Text style={{ fontSize: 10, color: selected ? colors.primary : colors.textMuted, marginTop: 2 }}>{m.label}</Text>
            </PressableScale>
          );
        })}
      </View>
    </View>
  );
}
