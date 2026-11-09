import React from "react";
import { Text, View } from "react-native";

import { Theme, useThemedStyles } from "../../theme";
import PressableScale from "../ui/PressableScale";

export type Reaction = "like" | "dislike";

interface Props {
  value: Reaction | null;
  busy: boolean;
  onChange: (reaction: Reaction) => void;
}

/** "¿Te gusta este ejercicio?" con 👍 / 👎; el elegido queda resaltado. */
export default function FeedbackButtons({ value, busy, onChange }: Props) {
  const styles = useThemedStyles(createStyles);
  const options: { key: Reaction; icon: string; label: string }[] = [
    { key: "like", icon: "👍", label: "Me gusta" },
    { key: "dislike", icon: "👎", label: "No me gusta" },
  ];
  return (
    <View style={styles.wrap}>
      <Text style={styles.title}>¿Te gusta este ejercicio?</Text>
      <View style={styles.row}>
        {options.map((o) => {
          const selected = value === o.key;
          return (
            <PressableScale
              key={o.key}
              disabled={busy}
              onPress={() => onChange(o.key)}
              style={[styles.btn, selected && styles.btnSelected]}
              accessibilityRole="button"
              accessibilityLabel={o.label}
              accessibilityState={{ selected, disabled: busy }}
            >
              <Text style={{ fontSize: 18 }}>{o.icon}</Text>
              <Text style={[styles.btnText, selected && styles.btnTextSelected]}>{o.label}</Text>
            </PressableScale>
          );
        })}
      </View>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  wrap: { gap: 8 },
  title: { fontSize: 13, fontWeight: "600" as const, color: colors.textMuted, textAlign: "center" as const },
  row: { flexDirection: "row" as const, gap: 10 },
  btn: {
    flex: 1,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    justifyContent: "center" as const,
    gap: 6,
    paddingVertical: 12,
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.card,
  },
  btnSelected: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  btnText: { fontSize: 13, fontWeight: "600" as const, color: colors.textMuted },
  btnTextSelected: { color: colors.primary },
});
