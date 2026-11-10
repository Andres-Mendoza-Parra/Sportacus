import { router } from "expo-router";
import React, { useState } from "react";
import { ActivityIndicator, Text, View } from "react-native";
import Animated, { FadeIn } from "react-native-reanimated";

import { useWorld } from "../../context/WorldContext";
import { useActionRoute } from "../../hooks/useActionRoute";
import { extractErrorMessage } from "../../services/api";
import { answerQuestion, skipQuestion } from "../../services/profile360Service";
import { AnswerValue, Completeness, NextItem, SkipMode } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import { isAnswered, rangeDefault, toggleMulti, validate } from "../forms/formLogic";
import QuestionInput from "../forms/QuestionInput";
import { useToast } from "../ui/Feedback";
import GradientButton from "../ui/GradientButton";
import PressableScale from "../ui/PressableScale";

/** Ruta de una tarjeta especial (consentimiento, chequeo de salud, afinar mundo). */
export function nextItemRoute(item: NextItem): string {
  if (item.route) return item.route;
  switch (item.kind) {
    case "consent":
      return item.questionnaireKey ? `/profile360/${item.questionnaireKey}` : `/profile360?area=${item.areaKey}`;
    case "health":
      return "/health";
    default:
      return item.questionnaireKey ? `/profile360/${item.questionnaireKey}` : "/profile360";
  }
}

const SPECIAL_DEFAULTS: Record<string, { icon: string; title: string; body: string; cta: string }> = {
  consent: { icon: "🔒", title: "Esta área es privada", body: "Si quieres contarnos, primero te explicamos cómo cuidamos tus datos.", cta: "Ver cómo lo cuidamos" },
  health: { icon: "🩺", title: "Tu chequeo de salud", body: "Dos minutos para cuidarte mejor. Mientras tanto entrenas en modo prudente.", cta: "Hacer el chequeo" },
  refine_world: { icon: "🎯", title: "Afina tu plan", body: "Unas preguntas más y tu plan encaja mejor contigo.", cta: "Afinar" },
};

interface Props {
  item: NextItem;
  /** Se llama con lo siguiente que propone el servidor (o null si no hay más) tras responder u omitir. */
  onDone: (next: NextItem | null, completeness?: Completeness) => void;
}

/**
 * Tarjeta de "siguiente mejor pregunta": responde en línea (POST /profile/answers) u omite,
 * pospone o "prefiere no decir". Las tarjetas especiales llevan a su pantalla.
 */
export default function NextItemCard({ item, onDone }: Props) {
  const styles = useThemedStyles(createStyles);
  const toast = useToast();
  const open = useActionRoute();
  const { invalidate } = useWorld();
  const [value, setValue] = useState<AnswerValue | undefined>(undefined);
  const [busy, setBusy] = useState<"save" | SkipMode | null>(null);
  const [showWhy, setShowWhy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const question = item.kind === "question" ? item.question : null;

  async function skip(mode: SkipMode) {
    setBusy(mode);
    try {
      const next = await skipQuestion(item.id, mode);
      onDone(next[0] ?? null);
    } catch (e) {
      toast.error(extractErrorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  async function save() {
    if (!question) return;
    let effective = value;
    if (question.type === "range" && !isAnswered(effective)) effective = rangeDefault(question);
    if (!isAnswered(effective)) {
      setError("Elige o escribe una respuesta, o usa “Omitir”.");
      return;
    }
    const message = validate(question, effective, true);
    if (message || effective === undefined) {
      setError(message);
      return;
    }
    setBusy("save");
    try {
      const result = await answerQuestion(question.id, typeof effective === "string" ? effective.trim() : effective);
      toast.success(result.highlights[0] ?? `¡Gracias! Tu perfil va en ${Math.round(result.completeness.pct)}%.`);
      onDone(result.next[0] ?? null, result.completeness);
      // Una respuesta puede cambiar el plan (plan dinámico): se avisa a las pestañas sin recargar esta tarjeta.
      setTimeout(invalidate, 1200);
    } catch (e) {
      toast.error(extractErrorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  // ── Tarjetas especiales: consentimiento, salud, afinar mundo ──
  if (!question) {
    const d = SPECIAL_DEFAULTS[item.kind] ?? SPECIAL_DEFAULTS.refine_world;
    return (
      <View style={styles.special}>
        <PressableScale onPress={() => open(nextItemRoute(item))} accessibilityRole="button" style={styles.specialRow}>
          <View style={styles.iconWrap}>
            <Text style={{ fontSize: 20 }}>{item.areaIcon ?? d.icon}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.specialTitle}>{item.title ?? d.title}</Text>
            <Text style={styles.specialBody}>{item.body ?? d.body}</Text>
            <Text style={styles.cta}>{`${d.cta} →`}</Text>
          </View>
        </PressableScale>
        <View style={styles.skipRow}>
          <SkipLink label="Más tarde" busy={busy === "posponer"} disabled={busy !== null} onPress={() => skip("posponer")} />
          <SkipLink label="No me interesa" busy={busy === "omitir"} disabled={busy !== null} onPress={() => skip("omitir")} />
        </View>
      </View>
    );
  }

  // ── Pregunta en línea ──
  function toggle(id: string) {
    if (!question) return;
    const current = Array.isArray(value) ? value : [];
    const next = toggleMulti(question, current, id);
    if (next === null) {
      toast.info(`Puedes elegir hasta ${question.max} opciones.`);
      return;
    }
    setValue(next);
    setError(null);
  }

  const why = item.why;
  return (
    <Animated.View key={question.id} entering={FadeIn.duration(250)} style={{ gap: 10 }}>
      <Text style={styles.area}>{`${item.areaIcon ?? "🧩"} ${item.areaName}`}</Text>
      <Text style={styles.prompt}>{question.prompt}</Text>
      {question.hint ? <Text style={styles.hint}>{question.hint}</Text> : null}
      {why ? (
        <PressableScale onPress={() => setShowWhy((s) => !s)} accessibilityRole="button" accessibilityState={{ expanded: showWhy }} style={styles.whyBtn}>
          <Text style={styles.whyTitle}>{showWhy ? "▾" : "▸"} ¿Por qué lo preguntamos?</Text>
          {showWhy ? <Text style={styles.whyBody}>{why}</Text> : null}
        </PressableScale>
      ) : null}
      <QuestionInput
        question={question}
        value={value}
        onChange={(v) => {
          setValue(v);
          setError(null);
        }}
        onToggle={toggle}
      />
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <GradientButton title="Guardar respuesta" onPress={save} loading={busy === "save"} disabled={busy !== null && busy !== "save"} />
      <View style={styles.skipRow}>
        <SkipLink label="Omitir" busy={busy === "omitir"} disabled={busy !== null} onPress={() => skip("omitir")} />
        <SkipLink label="Más tarde" busy={busy === "posponer"} disabled={busy !== null} onPress={() => skip("posponer")} />
        <SkipLink label="Prefiero no decir" busy={busy === "no_decir"} disabled={busy !== null} onPress={() => skip("no_decir")} />
      </View>
      {item.questionnaireKey ? (
        <PressableScale
          onPress={() => router.push({ pathname: "/profile360/[key]", params: { key: item.questionnaireKey ?? "" } })}
          accessibilityRole="button"
          style={{ alignSelf: "center", minHeight: 44, justifyContent: "center", paddingHorizontal: 8 }}
        >
          <Text style={styles.more}>Responder más sobre {item.areaName.toLowerCase()} →</Text>
        </PressableScale>
      ) : null}
    </Animated.View>
  );
}

function SkipLink({ label, busy, disabled, onPress }: { label: string; busy: boolean; disabled: boolean; onPress: () => void }) {
  const styles = useThemedStyles(createStyles);
  return (
    <PressableScale onPress={onPress} disabled={disabled} accessibilityRole="button" style={styles.skip}>
      {busy ? <ActivityIndicator size="small" /> : <Text style={styles.skipText}>{label}</Text>}
    </PressableScale>
  );
}

const createStyles = ({ colors }: Theme) => ({
  area: { fontSize: 11, fontWeight: "700" as const, color: colors.textFaint, textTransform: "uppercase" as const, letterSpacing: 0.6 },
  prompt: { fontSize: 16, fontWeight: "700" as const, color: colors.text, lineHeight: 22 },
  hint: { fontSize: 12, color: colors.textMuted, lineHeight: 17 },
  whyBtn: { minHeight: 44, justifyContent: "center" as const, paddingVertical: 7, paddingHorizontal: 10, borderRadius: 10, backgroundColor: colors.infoSoft },
  whyTitle: { fontSize: 12, fontWeight: "700" as const, color: colors.info },
  whyBody: { fontSize: 13, color: colors.text, lineHeight: 19, marginTop: 4 },
  error: { fontSize: 12, color: colors.danger, fontWeight: "600" as const },
  skipRow: { flexDirection: "row" as const, flexWrap: "wrap" as const, justifyContent: "center" as const, gap: 4 },
  skip: { minHeight: 44, justifyContent: "center" as const, paddingVertical: 8, paddingHorizontal: 12, minWidth: 64, alignItems: "center" as const },
  skipText: { fontSize: 12, fontWeight: "600" as const, color: colors.textMuted },
  more: { fontSize: 12, fontWeight: "700" as const, color: colors.primary },
  special: { gap: 6 },
  specialRow: { flexDirection: "row" as const, gap: 12, padding: 12, borderRadius: 14, backgroundColor: colors.cardAlt },
  iconWrap: { width: 42, height: 42, borderRadius: 13, backgroundColor: colors.primarySoft, alignItems: "center" as const, justifyContent: "center" as const },
  specialTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  specialBody: { fontSize: 13, color: colors.textMuted, lineHeight: 18, marginTop: 2 },
  cta: { fontSize: 12, fontWeight: "700" as const, color: colors.primary, marginTop: 6 },
});
