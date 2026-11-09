import React from "react";
import { Text, View } from "react-native";
import Animated, { FadeInDown, ZoomIn } from "react-native-reanimated";

import { AssessmentResult } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import { Card } from "../ui/Card";
import GradientButton from "../ui/GradientButton";
import { formatValue } from "./shared";

/** Resultado del test: valor, clasificación, novedades y desbloqueos. */
export default function AssessmentResultView({ result, onDone }: { result: AssessmentResult; onDone: () => void }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={{ gap: 14 }}>
      <Animated.View entering={ZoomIn.springify().damping(14)} style={styles.hero}>
        <Text style={styles.emoji}>🏅</Text>
        <Text style={styles.value}>{formatValue(result.value, result.unit)}</Text>
        {result.classification ? <Text style={styles.classification}>{result.classification}</Text> : null}
        {result.firstTime ? <Text style={styles.first}>¡Primera vez! Este será tu punto de partida.</Text> : null}
      </Animated.View>

      {result.unlocks.map((u, i) => (
        <Animated.View key={`${u.progressionKey}-${u.toStep}`} entering={FadeInDown.delay(150 + i * 80)}>
          <Card style={styles.unlock}>
            <Text style={styles.unlockTitle}>🔓 ¡Desbloqueaste {u.stepName}!</Text>
            <Text style={styles.unlockSub}>{u.progressionName}: escalón {u.fromStep + 1} → {u.toStep + 1}</Text>
          </Card>
        </Animated.View>
      ))}

      {result.highlights.length > 0 ? (
        <Card delay={250}>
          {result.highlights.map((h, i) => (
            <Text key={i} style={styles.highlight}>
              ✨ {h}
            </Text>
          ))}
        </Card>
      ) : null}

      <GradientButton title="Volver a los tests" onPress={onDone} />
    </View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  hero: { backgroundColor: colors.card, borderRadius: 22, padding: 24, alignItems: "center" as const, ...shadow },
  emoji: { fontSize: 48 },
  value: { fontSize: 42, fontWeight: "800" as const, color: colors.text, marginTop: 6 },
  classification: { fontSize: 20, fontWeight: "700" as const, color: colors.primary, marginTop: 4 },
  first: { fontSize: 13, color: colors.textMuted, marginTop: 10, textAlign: "center" as const },
  unlock: { backgroundColor: colors.primarySoft },
  unlockTitle: { fontSize: 15, fontWeight: "700" as const, color: colors.primary },
  unlockSub: { fontSize: 12, color: colors.textMuted, marginTop: 3 },
  highlight: { fontSize: 13, color: colors.text, lineHeight: 20, marginBottom: 4 },
});
