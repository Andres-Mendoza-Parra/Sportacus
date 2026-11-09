import React from "react";
import { Text, View } from "react-native";
import Animated, { ZoomIn } from "react-native-reanimated";

import { Theme, useTheme, useThemedStyles } from "../../theme";
import AnimatedBar from "../ui/AnimatedBar";
import PressableScale from "../ui/PressableScale";
import { formatClock } from "../ui/useCountdown";

/** Cuenta "3, 2, 1" grande antes de arrancar un test con tiempo. */
export function GetReady({ remaining }: { remaining: number }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={styles.center} accessibilityLiveRegion="assertive">
      <Text style={styles.caption}>Prepárate…</Text>
      <Animated.Text key={remaining} entering={ZoomIn.duration(250)} style={styles.huge}>
        {Math.max(1, remaining)}
      </Animated.Text>
    </View>
  );
}

/** Reloj de cuenta regresiva con barra de progreso. */
export function CountdownClock({ remaining, total }: { remaining: number; total: number }) {
  const styles = useThemedStyles(createStyles);
  const pct = total > 0 ? ((total - remaining) / total) * 100 : 0;
  return (
    <View style={{ alignItems: "center", gap: 8 }} accessibilityLabel={`Quedan ${remaining} segundos`}>
      <Text style={styles.clock}>{formatClock(remaining)}</Text>
      <AnimatedBar pct={pct} height={8} style={{ alignSelf: "stretch" }} />
    </View>
  );
}

/** Botón circular gigante para contar repeticiones con un toque. */
export function TapCounter({ count, onTap, disabled }: { count: number; onTap: () => void; disabled?: boolean }) {
  const { colors } = useTheme();
  return (
    <PressableScale
      onPress={onTap}
      disabled={disabled}
      scaleTo={0.92}
      accessibilityRole="button"
      accessibilityLabel={`Sumar una repetición. Llevas ${count}`}
      style={{
        alignSelf: "center",
        width: 210,
        height: 210,
        borderRadius: 105,
        backgroundColor: colors.primary,
        alignItems: "center",
        justifyContent: "center",
        opacity: disabled ? 0.5 : 1,
      }}
    >
      <Text style={{ color: colors.onPrimary, fontSize: 72, fontWeight: "800" }}>{count}</Text>
      <Text style={{ color: colors.onPrimary, fontSize: 13, opacity: 0.85 }}>Toca para sumar</Text>
    </PressableScale>
  );
}

/** Botones −/+ pequeños para corregir un valor. */
export function Adjust({ value, onChange, min = 0 }: { value: number; onChange: (v: number) => void; min?: number }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={styles.adjustRow}>
      <PressableScale style={styles.adjustBtn} onPress={() => onChange(Math.max(min, value - 1))} accessibilityRole="button" accessibilityLabel="Restar uno">
        <Text style={styles.adjustText}>−1</Text>
      </PressableScale>
      <PressableScale style={styles.adjustBtn} onPress={() => onChange(value + 1)} accessibilityRole="button" accessibilityLabel="Sumar uno">
        <Text style={styles.adjustText}>+1</Text>
      </PressableScale>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  center: { alignItems: "center" as const, paddingVertical: 30 },
  caption: { fontSize: 15, color: colors.textMuted, fontWeight: "600" as const },
  huge: { fontSize: 110, fontWeight: "800" as const, color: colors.primary },
  clock: { fontSize: 56, fontWeight: "800" as const, color: colors.text, fontVariant: ["tabular-nums" as const] },
  adjustRow: { flexDirection: "row" as const, justifyContent: "center" as const, gap: 14 },
  adjustBtn: { minWidth: 64, paddingVertical: 12, borderRadius: 14, backgroundColor: colors.cardAlt, alignItems: "center" as const },
  adjustText: { fontSize: 16, fontWeight: "700" as const, color: colors.primary },
});
