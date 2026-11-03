import React from "react";
import { Text, View } from "react-native";

import { Theme, useThemedStyles } from "../../theme";
import AnimatedBar from "./AnimatedBar";
import PressableScale from "./PressableScale";
import { formatClock, useCountdown } from "./useCountdown";

const PRESETS = [30, 60, 90, 120];

interface Props {
  /** Descanso sugerido para el ejercicio (resalta ese botón). */
  suggested?: number;
}

/** Temporizador de descanso entre series: elige la duración y arranca la cuenta regresiva. */
export default function RestTimer({ suggested = 60 }: Props) {
  const styles = useThemedStyles(createStyles);
  const { remaining, total, running, start, stop } = useCountdown();
  const pct = total > 0 ? Math.round(((total - remaining) / total) * 100) : 0;

  return (
    <View style={styles.card}>
      <Text style={styles.title}>⏱️ Descanso entre series</Text>
      {running ? (
        <>
          <Text style={styles.clock}>{formatClock(remaining)}</Text>
          <AnimatedBar pct={pct} height={6} style={{ marginTop: 8 }} />
          <PressableScale style={[styles.btn, styles.btnGhost]} onPress={stop}>
            <Text style={styles.btnGhostText}>Cancelar</Text>
          </PressableScale>
        </>
      ) : (
        <View style={styles.row}>
          {PRESETS.map((s) => (
            <PressableScale key={s} style={[styles.preset, s === suggested && styles.presetSuggested]} onPress={() => start(s)}>
              <Text style={[styles.presetText, s === suggested && styles.presetTextSuggested]}>{s} s</Text>
            </PressableScale>
          ))}
        </View>
      )}
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  card: { backgroundColor: colors.card, borderRadius: 16, padding: 14, borderWidth: 1, borderColor: colors.border },
  title: { color: colors.text, fontSize: 14, fontWeight: "700" as const },
  row: { flexDirection: "row" as const, gap: 8, marginTop: 10 },
  preset: { flex: 1, paddingVertical: 10, borderRadius: 12, alignItems: "center" as const, backgroundColor: colors.cardAlt },
  presetSuggested: { backgroundColor: colors.primarySoft, borderWidth: 1, borderColor: colors.primary },
  presetText: { color: colors.textMuted, fontWeight: "600" as const, fontSize: 13 },
  presetTextSuggested: { color: colors.primary },
  clock: { color: colors.primary, fontSize: 40, fontWeight: "800" as const, textAlign: "center" as const, marginTop: 6 },
  btn: { marginTop: 10, paddingVertical: 10, borderRadius: 12, alignItems: "center" as const },
  btnGhost: { backgroundColor: colors.cardAlt },
  btnGhostText: { color: colors.textMuted, fontWeight: "600" as const },
});
