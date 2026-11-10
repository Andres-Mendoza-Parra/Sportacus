import Slider from "@react-native-community/slider";
import { router } from "expo-router";
import { useState } from "react";
import { Pressable, ScrollView, Text, View } from "react-native";
import Animated, { FadeInDown, ZoomIn } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { initialAnswers, payloadFor, rangeDefault, toggleMulti } from "../components/forms/formLogic";
import HealthConsentGate from "../components/health/HealthConsentGate";
import QuestionInput from "../components/forms/QuestionInput";
import ReadinessCard from "../components/profile360/ReadinessCard";
import { Card } from "../components/ui/Card";
import { useToast } from "../components/ui/Feedback";
import GradientButton from "../components/ui/GradientButton";
import NoticeBox from "../components/ui/NoticeBox";
import ScreenHeader from "../components/ui/ScreenHeader";
import { ScreenSkeleton } from "../components/ui/Skeleton";
import { ErrorState } from "../components/ui/States";
import { useWorld } from "../context/WorldContext";
import { useLoader } from "../hooks/useLoader";
import { extractErrorMessage } from "../services/api";
import { fetchCheckin, saveCheckin } from "../services/profile360Service";
import { AnswerValue, Answers, CheckinResult, CheckinView, Question } from "../services/types";
import { Theme, useThemedStyles, useTheme } from "../theme";

/** Etiquetas de los extremos de cada deslizador del check-in (ids del contrato). */
const ENDS: Record<string, [string, string]> = {
  ck_sueno_calidad: ["Muy mal", "Excelente"],
  ck_energia: ["Sin energía", "A tope"],
  ck_animo: ["Bajo", "Muy bien"],
  ck_estres: ["En calma", "Mucho estrés"],
  ck_dolor: ["Nada", "Mucho"],
};

/** El dolor arranca en 0 (sin dolor): nunca se asume dolor que no se reportó. */
const ZERO_DEFAULT = new Set(["ck_dolor"]);

function sliderStart(q: Question): number {
  return ZERO_DEFAULT.has(q.id) ? (q.min ?? 0) : rangeDefault(q);
}

/** Check-in diario con el consentimiento de datos de salud por delante (si falta). */
export default function CheckinRoute() {
  return (
    <HealthConsentGate title="Check-in de hoy">
      <CheckinScreen />
    </HealthConsentGate>
  );
}

/** Check-in diario: 6 deslizadores rápidos → cómo estás hoy y cuánto conviene entrenar. */
function CheckinScreen() {
  const { colors } = useTheme();
  const view = useLoader(fetchCheckin);
  const [editing, setEditing] = useState(false);

  if (view.loading && !view.data) return <ScreenSkeleton cards={5} />;
  const data = view.data;
  if (!data) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <ScreenHeader title="Check-in de hoy" />
        <ErrorState message={view.error ?? "No pudimos cargar el check-in."} onRetry={view.reload} />
      </View>
    );
  }

  if (data.done && data.readiness && !editing) {
    return <ResultView result={{ readiness: data.readiness, highlights: [] }} onUpdate={() => setEditing(true)} already />;
  }
  return <CheckinForm key={data.form.key} view={data} />;
}

function CheckinForm({ view }: { view: CheckinView }) {
  const styles = useThemedStyles(createStyles);
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const { invalidate } = useWorld();
  // Solo las respuestas guardadas hoy o las que se toquen: un deslizador sin tocar no se envía.
  const [answers, setAnswers] = useState<Answers>(() => initialAnswers(view.form));
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<CheckinResult | null>(null);

  function set(q: Question, v: AnswerValue | undefined) {
    setAnswers((prev) => {
      const copy = { ...prev };
      if (v === undefined) delete copy[q.id];
      else copy[q.id] = v;
      return copy;
    });
  }

  async function submit() {
    setSaving(true);
    try {
      const payload = payloadFor(view.form, answers);
      if (Object.keys(payload).length === 0) {
        toast.info("Mueve al menos un deslizador para contarnos cómo estás. Lo que no toques queda sin responder.");
        return;
      }
      const saved = await saveCheckin(payload);
      invalidate();
      setResult(saved);
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  if (result) return <ResultView result={result} />;

  return (
    <View style={styles.bg}>
      <ScreenHeader title={view.form.title || "Check-in de hoy"} subtitle={view.form.subtitle ?? "20 segundos para ajustar tu día"} />
      <ScrollView contentContainerStyle={[styles.body, { paddingBottom: insets.bottom + 24 }]}>
        {view.form.questions.map((q, i) => (
          <Animated.View key={q.id} entering={FadeInDown.delay(i * 50).duration(300)}>
            <Card style={{ gap: 6 }}>
              <Text style={styles.prompt}>{q.prompt}</Text>
              {q.type === "range" ? (
                <CompactSlider
                  question={q}
                  value={typeof answers[q.id] === "number" ? (answers[q.id] as number) : sliderStart(q)}
                  touched={answers[q.id] !== undefined}
                  onChange={(v) => set(q, v)}
                  onClear={answers[q.id] !== undefined ? () => set(q, undefined) : undefined}
                />
              ) : (
                <QuestionInput
                  question={q}
                  value={answers[q.id]}
                  onChange={(v) => set(q, v)}
                  onToggle={(id) => {
                    const current = Array.isArray(answers[q.id]) ? (answers[q.id] as string[]) : [];
                    const next = toggleMulti(q, current, id);
                    if (next) set(q, next);
                  }}
                />
              )}
            </Card>
          </Animated.View>
        ))}
        <GradientButton title="Ver cómo estoy hoy" onPress={submit} loading={saving} style={{ marginTop: 6 }} />
        <Text style={styles.foot}>
          Solo enviamos lo que respondas: lo que no toques queda «sin responder». Es un registro de bienestar, no una evaluación médica.
        </Text>
      </ScrollView>
    </View>
  );
}

interface SliderProps {
  question: Question;
  value: number;
  touched: boolean;
  onChange: (v: number) => void;
  onClear?: () => void;
}

function CompactSlider({ question, value, touched, onChange, onClear }: SliderProps) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const min = question.min ?? 0;
  const max = question.max ?? 10;
  const step = question.step && question.step > 0 ? question.step : 1;
  const ends = ENDS[question.id];
  return (
    <View>
      {touched ? (
        <Text style={styles.value}>
          {value}
          <Text style={styles.unit}>{question.unit ? ` ${question.unit}` : ` / ${max}`}</Text>
        </Text>
      ) : (
        <Text style={styles.unanswered}>Sin responder · desliza para contestar</Text>
      )}
      <Slider
        style={{ width: "100%", height: 36 }}
        minimumValue={min}
        maximumValue={max}
        step={step}
        value={value}
        onValueChange={(v) => onChange(Math.round(v / step) * step)}
        minimumTrackTintColor={colors.primary}
        maximumTrackTintColor={colors.track}
        thumbTintColor={colors.primary}
        accessibilityLabel={touched ? `${question.prompt}: ${value}` : `${question.prompt}: sin responder`}
      />
      <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
        <Text style={styles.end}>{ends ? ends[0] : `${min}`}</Text>
        <Text style={styles.end}>{ends ? ends[1] : `${max}`}</Text>
      </View>
      {onClear ? (
        <Pressable onPress={onClear} accessibilityRole="button" accessibilityLabel={`Dejar sin responder: ${question.prompt}`} style={styles.clear}>
          <Text style={styles.clearText}>Dejar sin responder</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

function ResultView({ result, onUpdate, already }: { result: CheckinResult; onUpdate?: () => void; already?: boolean }) {
  const styles = useThemedStyles(createStyles);
  const insets = useSafeAreaInsets();

  function close() {
    if (router.canGoBack()) router.back();
    else router.replace("/home");
  }

  return (
    <View style={styles.bg}>
      <ScreenHeader title="Cómo estás hoy" subtitle={already ? "Ya hiciste tu check-in de hoy" : "¡Gracias por contarnos!"} />
      <ScrollView contentContainerStyle={[styles.body, { paddingBottom: insets.bottom + 24 }]}>
        {result.readiness ? (
          <Animated.View entering={ZoomIn.springify().damping(15)}>
            <Card>
              <ReadinessCard readiness={result.readiness} />
            </Card>
          </Animated.View>
        ) : (
          <NoticeBox tone="success" body="Guardamos tu check-in. Lo tendremos en cuenta en tu sesión de hoy." />
        )}
        {result.highlights.map((h) => (
          <NoticeBox key={h} tone="info" icon="✨" body={h} />
        ))}
        <GradientButton title="Listo" onPress={close} />
        {onUpdate ? <GradientButton title="Actualizar mi check-in" variant="ghost" onPress={onUpdate} /> : null}
      </ScrollView>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  body: { padding: 16, gap: 12, maxWidth: 640, width: "100%" as const, alignSelf: "center" as const },
  prompt: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  value: { fontSize: 24, fontWeight: "800" as const, color: colors.primary, textAlign: "center" as const },
  unit: { fontSize: 13, fontWeight: "400" as const, color: colors.textMuted },
  end: { fontSize: 11, color: colors.textFaint },
  unanswered: { fontSize: 14, fontWeight: "600" as const, color: colors.textMuted, textAlign: "center" as const, paddingVertical: 6 },
  clear: { alignSelf: "center" as const, minHeight: 44, justifyContent: "center" as const, paddingHorizontal: 12 },
  clearText: { fontSize: 12, fontWeight: "600" as const, color: colors.textMuted, textDecorationLine: "underline" as const },
  foot: { fontSize: 12, color: colors.textFaint, textAlign: "center" as const, lineHeight: 17 },
});
