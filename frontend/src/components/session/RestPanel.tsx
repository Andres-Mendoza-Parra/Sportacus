import React, { useEffect } from "react";
import { Text, View } from "react-native";
import Animated, { FadeIn } from "react-native-reanimated";

import { useTheme } from "../../theme";
import AnimatedBar from "../ui/AnimatedBar";
import PressableScale from "../ui/PressableScale";
import { formatClock, useCountdown } from "../ui/useCountdown";

interface Props {
  seconds: number;
  tips: string[];
  /** Desplaza el consejo inicial (p. ej. el número de serie) para no repetir siempre el mismo. */
  seed?: number;
  onDone: () => void;
}

/** Descanso entre series: cuenta regresiva automática, consejo rotativo del mundo y botón para saltarlo. */
export default function RestPanel({ seconds, tips, seed = 0, onDone }: Props) {
  const { colors } = useTheme();
  const { remaining, total, start, stop } = useCountdown(onDone);

  useEffect(() => {
    start(seconds);
  }, [seconds, start]);

  const elapsed = total > 0 ? total - remaining : 0;
  const tip = tips.length > 0 ? tips[(seed + Math.floor(elapsed / 10)) % tips.length] : null;
  const pct = total > 0 ? (elapsed / total) * 100 : 0;

  return (
    <Animated.View
      entering={FadeIn.duration(250)}
      style={{ backgroundColor: colors.primarySoft, borderRadius: 16, padding: 14, gap: 8 }}
    >
      <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
        <Text style={{ fontSize: 13, fontWeight: "700", color: colors.primary }}>⏱️ Descansa</Text>
        <Text style={{ fontSize: 30, fontWeight: "800", color: colors.primary }}>{formatClock(remaining)}</Text>
      </View>
      <AnimatedBar pct={pct} height={6} />
      {tip ? <Text style={{ fontSize: 12, color: colors.text, lineHeight: 17 }}>💡 {tip}</Text> : null}
      <PressableScale
        onPress={() => {
          stop();
          onDone();
        }}
        accessibilityRole="button"
        style={{ alignSelf: "flex-end", paddingVertical: 8, paddingHorizontal: 14, borderRadius: 12, backgroundColor: colors.card }}
      >
        <Text style={{ fontSize: 13, fontWeight: "700", color: colors.primary }}>Saltar descanso</Text>
      </PressableScale>
    </Animated.View>
  );
}
