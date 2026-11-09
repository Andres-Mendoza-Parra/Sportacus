import React from "react";
import { Pressable, Text, View } from "react-native";

import { Answers, Question } from "../../services/types";
import { useThemedStyles } from "../../theme";
import GradientHeader from "../ui/GradientHeader";
import PressableScale from "../ui/PressableScale";
import { isAnswered } from "./formLogic";

interface Props {
  title: string;
  subtitle?: string | null;
  index: number;
  questions: Question[];
  answers: Answers;
  /** null = sin botón de salida. */
  exitIcon: "back" | "close" | null;
  onExit: () => void;
  onJump: (index: number) => void;
}

/** Cabecera del asistente: salida, título, contador y puntos de progreso (tocables hacia atrás). */
export default function WizardHeader({ title, subtitle, index, questions, answers, exitIcon, onExit, onJump }: Props) {
  const styles = useThemedStyles(createStyles);
  const answered = questions.filter((q) => isAnswered(answers[q.id])).length;
  return (
    <GradientHeader style={{ paddingBottom: 14 }}>
      <View style={styles.row}>
        {exitIcon ? (
          <PressableScale
            style={styles.iconBtn}
            onPress={onExit}
            accessibilityRole="button"
            accessibilityLabel={exitIcon === "close" ? "Cerrar formulario" : "Volver"}
          >
            <Text style={styles.iconText}>{exitIcon === "close" ? "✕" : "←"}</Text>
          </PressableScale>
        ) : null}
        <View style={{ flex: 1 }}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.step}>{`Pregunta ${index + 1} de ${questions.length} · ${answered} respondidas`}</Text>
        </View>
      </View>
      {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      <View style={styles.dots}>
        {questions.map((q, i) => (
          <Pressable
            key={q.id}
            onPress={() => onJump(i)}
            style={{ flex: 1, paddingVertical: 6 }}
            hitSlop={4}
            accessibilityLabel={`Ir a la pregunta ${i + 1}`}
          >
            <View style={[styles.dot, isAnswered(answers[q.id]) && styles.dotAnswered, i === index && styles.dotCurrent]} />
          </Pressable>
        ))}
      </View>
    </GradientHeader>
  );
}

const createStyles = () => ({
  row: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  iconBtn: { width: 36, height: 36, borderRadius: 12, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center" as const, justifyContent: "center" as const },
  iconText: { color: "#fff", fontSize: 16 },
  title: { color: "#fff", fontSize: 17, fontWeight: "700" as const },
  step: { color: "rgba(255,255,255,0.8)", fontSize: 12, marginTop: 1 },
  subtitle: { color: "rgba(255,255,255,0.88)", fontSize: 13, marginTop: 10, lineHeight: 18 },
  dots: { flexDirection: "row" as const, gap: 5, marginTop: 10 },
  dot: { height: 5, borderRadius: 3, backgroundColor: "rgba(255,255,255,0.25)" },
  dotAnswered: { backgroundColor: "rgba(255,255,255,0.75)" },
  dotCurrent: { backgroundColor: "#fff", height: 7, marginTop: -1 },
});
