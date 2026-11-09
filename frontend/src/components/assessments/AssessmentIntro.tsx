import React from "react";
import { Text, View } from "react-native";

import { formatDuration } from "../../constants/labels";
import { AssessmentView } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import { Card } from "../ui/Card";
import GradientButton from "../ui/GradientButton";
import NoticeBox from "../ui/NoticeBox";
import { formatValue, openSource } from "./shared";

/** Primera fase del test: qué mide, cómo se hace, aviso de seguridad y fuente. */
export default function AssessmentIntro({ item, onStart }: { item: AssessmentView; onStart: () => void }) {
  const styles = useThemedStyles(createStyles);
  const d = item.definition;

  return (
    <View style={{ gap: 14 }}>
      <Card delay={0}>
        <Text style={styles.desc}>{d.description}</Text>
        <View style={styles.metaRow}>
          {d.durationS ? <Text style={styles.meta}>⏱️ {formatDuration(d.durationS)}</Text> : null}
          {d.unit ? <Text style={styles.meta}>📏 Se mide en {d.unit}</Text> : null}
          {d.retestDays ? <Text style={styles.meta}>🔁 Repetir cada {d.retestDays} días</Text> : null}
        </View>
        {item.lastValue != null ? (
          <Text style={styles.last}>
            Tu último resultado: {formatValue(item.lastValue, d.unit)}
            {item.lastClassification ? ` (${item.lastClassification})` : ""}
          </Text>
        ) : null}
      </Card>

      {d.instructions.length > 0 ? (
        <Card delay={60}>
          <Text style={styles.title}>Cómo se hace</Text>
          {d.instructions.map((step, i) => (
            <View key={i} style={styles.step}>
              <View style={styles.stepNum}>
                <Text style={styles.stepNumText}>{i + 1}</Text>
              </View>
              <Text style={styles.stepText}>{step}</Text>
            </View>
          ))}
        </Card>
      ) : null}

      {d.safetyNote ? <NoticeBox tone="warn" title="Por tu seguridad" body={d.safetyNote} /> : null}

      {item.sourceName ? (
        <Text
          style={[styles.source, item.sourceUrl ? styles.link : null]}
          onPress={item.sourceUrl ? () => openSource(item.sourceUrl as string) : undefined}
          accessibilityRole={item.sourceUrl ? "link" : "text"}
        >
          Fuente: {item.sourceName}
        </Text>
      ) : null}

      <GradientButton title="Empezar el test" onPress={onStart} />
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  desc: { fontSize: 14, color: colors.text, lineHeight: 21 },
  metaRow: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 12, marginTop: 10 },
  meta: { fontSize: 12, color: colors.textMuted },
  last: { fontSize: 12, color: colors.primary, fontWeight: "600" as const, marginTop: 10 },
  title: { fontSize: 15, fontWeight: "700" as const, color: colors.text, marginBottom: 10 },
  step: { flexDirection: "row" as const, gap: 10, marginBottom: 10, alignItems: "flex-start" as const },
  stepNum: { width: 24, height: 24, borderRadius: 12, backgroundColor: colors.primarySoft, alignItems: "center" as const, justifyContent: "center" as const },
  stepNumText: { fontSize: 12, fontWeight: "700" as const, color: colors.primary },
  stepText: { flex: 1, fontSize: 13, color: colors.text, lineHeight: 19 },
  source: { fontSize: 11, color: colors.textFaint, textAlign: "center" as const },
  link: { textDecorationLine: "underline" as const },
});
