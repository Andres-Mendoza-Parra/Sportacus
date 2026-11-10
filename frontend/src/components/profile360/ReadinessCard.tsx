import React from "react";
import { Text, View } from "react-native";

import { Readiness } from "../../services/types";
import { useTheme } from "../../theme";
import AnimatedBar from "../ui/AnimatedBar";

/** Color e ícono según cuánto del volumen planeado conviene hacer hoy. */
export function readinessTone(factor: number, colors: { success: string; warn: string; danger: string }): { color: string; icon: string } {
  if (factor >= 0.9) return { color: colors.success, icon: "🟢" };
  if (factor >= 0.75) return { color: colors.warn, icon: "🟡" };
  return { color: colors.danger, icon: "🟠" };
}

/** Cómo estás hoy (tras el check-in): etiqueta, % del volumen sugerido y razones. */
export default function ReadinessCard({ readiness, compact }: { readiness: Readiness; compact?: boolean }) {
  const { colors } = useTheme();
  const pct = Math.round(Math.max(0, Math.min(1, readiness.factor)) * 100);
  const tone = readinessTone(readiness.factor, colors);

  return (
    <View style={{ gap: 10 }}>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
        <Text style={{ fontSize: compact ? 26 : 34 }}>{tone.icon}</Text>
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: compact ? 15 : 18, fontWeight: "800", color: colors.text }}>{readiness.label}</Text>
          <Text style={{ fontSize: 12, color: colors.textMuted, marginTop: 2 }}>{`Hoy te sugerimos hacer ~${pct}% de lo planeado`}</Text>
        </View>
        <Text style={{ fontSize: compact ? 18 : 24, fontWeight: "800", color: tone.color }}>{`${pct}%`}</Text>
      </View>
      <AnimatedBar pct={pct} color={tone.color} height={8} />
      {readiness.reasons.length > 0 ? (
        <View style={{ gap: 4 }}>
          {readiness.reasons.map((r) => (
            <Text key={r} style={{ fontSize: 13, color: colors.textMuted, lineHeight: 19 }}>
              {`• ${r}`}
            </Text>
          ))}
        </View>
      ) : null}
    </View>
  );
}
