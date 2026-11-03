import Slider from "@react-native-community/slider";
import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useMemo, useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import Animated, { FadeIn, FadeInRight, FadeOutLeft } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useConfirm, useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import GradientHeader from "../../components/ui/GradientHeader";
import PressableScale from "../../components/ui/PressableScale";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { extractErrorMessage } from "../../services/api";
import {
  Question,
  QuestionnaireDetail,
  fetchQuestionnaireDetail,
  saveQuestionnaireAnswers,
} from "../../services/questionnaireService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

type Answers = Record<string, unknown>;

function isAnswered(value: unknown): boolean {
  if (value === undefined || value === null) return false;
  if (typeof value === "string") return value.trim().length > 0;
  return true; // incluye arreglos (aunque estén vacíos: el usuario eligió "nada") y números
}

export default function QuestionnaireDetailScreen() {
  // onb=1: onboarding tras el registro; flow = cuestionarios que faltan por recorrer (separados por coma).
  const { key, onb, flow } = useLocalSearchParams<{ key: string; onb?: string; flow?: string }>();
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const confirm = useConfirm();
  const insets = useSafeAreaInsets();

  const [detail, setDetail] = useState<QuestionnaireDetail | null>(null);
  const [answers, setAnswers] = useState<Answers>({});
  const [dirty, setDirty] = useState(false);
  const [stepIndex, setStepIndex] = useState(0);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (!key) return;
    (async () => {
      try {
        const data = await fetchQuestionnaireDetail(key);
        setDetail(data);
        setAnswers({ ...data.answers });
      } catch (error) {
        toast.error(extractErrorMessage(error));
        leave();
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  const question: Question | null = useMemo(() => detail?.questions[stepIndex] ?? null, [detail, stepIndex]);

  /** Siguiente paso del onboarding: el próximo cuestionario en la cadena, o elegir módulo al terminar. */
  function goToNextOnboardingStep() {
    const remaining = (flow ?? "").split(",").filter(Boolean);
    if (remaining.length === 0) {
      router.replace("/onboarding/module");
      return;
    }
    const [next, ...rest] = remaining;
    router.replace({ pathname: "/questionnaires/[key]", params: { key: next, onb: "1", flow: rest.join(",") } });
  }

  // Omitir (✕) una plantilla durante el onboarding NO saca de la cadena: cada campo es opcional,
  // pero elegir módulo al final no lo es.
  function leave() {
    if (onb === "1") {
      goToNextOnboardingStep();
      return;
    }
    if (router.canGoBack()) router.back();
    else router.replace("/questionnaires");
  }

  /** Tras guardar: en el onboarding sigue con el siguiente cuestionario; al terminar, elige módulo. */
  function advance() {
    if (onb !== "1") {
      leave();
      return;
    }
    goToNextOnboardingStep();
  }

  if (!detail || !question) return <ScreenSkeleton cards={3} />;

  const total = detail.questions.length;
  const answeredCount = detail.questions.filter((q) => isAnswered(answers[q.id])).length;
  const isLast = stepIndex === total - 1;

  function setAnswer(value: unknown) {
    setAnswers((prev) => ({ ...prev, [question!.id]: value }));
    setDirty(true);
  }

  function toggleTag(value: string) {
    const current = Array.isArray(answers[question!.id]) ? [...(answers[question!.id] as string[])] : [];
    const idx = current.indexOf(value);
    if (idx === -1) current.push(value);
    else current.splice(idx, 1);
    setAnswer(current);
  }

  async function save(): Promise<boolean> {
    const payload: Answers = {};
    for (const q of detail!.questions) {
      const v = answers[q.id];
      if (isAnswered(v)) payload[q.id] = typeof v === "string" ? v.trim() : v;
    }
    if (Object.keys(payload).length === 0) {
      toast.info("Responde al menos una pregunta para guardar.");
      return false;
    }
    setIsSaving(true);
    try {
      const result = await saveQuestionnaireAnswers(detail!.key, payload);
      setDirty(false);
      const strength = result.profileStrengthPct !== null ? ` Tu perfil está afinado al ${result.profileStrengthPct}%.` : "";
      toast.success(`¡Datos guardados!${strength}`, {
        actionLabel: result.routeInvalidated ? "Ver mi rutina" : undefined,
        onAction: () => router.replace("/routine"),
        durationMs: 6000,
      });
      return true;
    } catch (error) {
      toast.error(extractErrorMessage(error));
      return false;
    } finally {
      setIsSaving(false);
    }
  }

  async function handleNext() {
    // En una pregunta de rango intacta, "Continuar" confirma el valor que se ve en pantalla.
    if (question!.type === "range" && !isAnswered(answers[question!.id])) {
      setAnswer(rangeDefault(question!));
    }
    if (!isLast) {
      setStepIndex((i) => i + 1);
      return;
    }
    if (await save()) advance();
  }

  async function handleBack() {
    if (stepIndex > 0) {
      setStepIndex((i) => i - 1);
      return;
    }
    await handleExit();
  }

  async function handleExit() {
    if (dirty) {
      const discard = await confirm({
        title: "¿Salir sin guardar?",
        message: "Tienes respuestas sin guardar. Si sales ahora se perderán.",
        confirmText: "Salir",
        destructive: true,
      });
      if (!discard) return;
    }
    leave();
  }

  async function handleSaveAndExit() {
    if (await save()) advance();
  }

  const value = answers[question.id];

  return (
    <KeyboardAvoidingView style={styles.bg} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <GradientHeader style={{ paddingBottom: 16 }}>
        <View style={styles.headRow}>
          <PressableScale style={styles.iconBtn} onPress={handleExit}>
            <Text style={styles.iconBtnText}>✕</Text>
          </PressableScale>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>{detail.title}</Text>
            <Text style={styles.step}>
              Pregunta {stepIndex + 1} de {total} · {answeredCount} respondidas
            </Text>
          </View>
          <PressableScale style={styles.saveBtn} onPress={handleSaveAndExit} disabled={isSaving || !dirty}>
            <Text style={[styles.saveBtnText, !dirty && { opacity: 0.5 }]}>Guardar</Text>
          </PressableScale>
        </View>

        <View style={styles.dots}>
          {detail.questions.map((q, i) => (
            <Pressable key={q.id} onPress={() => setStepIndex(i)} style={{ flex: 1, paddingVertical: 6 }} hitSlop={4}>
              <View
                style={[
                  styles.dot,
                  isAnswered(answers[q.id]) && styles.dotAnswered,
                  i === stepIndex && styles.dotCurrent,
                ]}
              />
            </Pressable>
          ))}
        </View>
      </GradientHeader>

      <ScrollView style={{ flex: 1 }} contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
        <Animated.View key={question.id} entering={FadeInRight.duration(280)} exiting={FadeOutLeft.duration(150)}>
          <Text style={styles.prompt}>{question.prompt}</Text>
          {question.hint ? <Text style={styles.hint}>{question.hint}</Text> : null}

          {question.type === "options" && (
            <View style={{ gap: 10 }}>
              {(question.options ?? []).map((opt, idx) => {
                const selected = value === opt.id;
                return (
                  <Animated.View key={opt.id} entering={FadeIn.delay(idx * 40).duration(250)}>
                    <PressableScale style={[styles.option, selected && styles.optionSelected]} onPress={() => setAnswer(opt.id)}>
                      <View style={[styles.optionIconWrap, selected && styles.optionIconWrapSelected]}>
                        <Text style={styles.optionIcon}>{opt.icon ?? "•"}</Text>
                      </View>
                      <View style={{ flex: 1 }}>
                        <Text style={styles.optionText}>{opt.text}</Text>
                        {opt.sub ? <Text style={styles.optionSub}>{opt.sub}</Text> : null}
                      </View>
                      {selected ? <Text style={styles.check}>✓</Text> : null}
                    </PressableScale>
                  </Animated.View>
                );
              })}
            </View>
          )}

          {question.type === "range" && (
            <RangeInput
              min={question.min ?? 0}
              max={question.max ?? 100}
              unit={question.unit ?? ""}
              value={typeof value === "number" ? value : rangeDefault(question)}
              touched={isAnswered(value)}
              onChange={(v) => setAnswer(v)}
            />
          )}

          {question.type === "number" && (
            <View style={styles.inputCard}>
              <TextInput
                style={styles.numberInput}
                keyboardType="numeric"
                placeholder="0"
                placeholderTextColor={colors.textFaint}
                value={value === undefined || value === null ? "" : String(value)}
                onChangeText={(t) => {
                  const cleaned = t.replace(",", ".");
                  if (cleaned === "") return setAnswer(undefined);
                  const n = Number(cleaned);
                  if (!Number.isNaN(n)) setAnswer(n);
                }}
              />
              {question.unit ? <Text style={styles.unit}>{question.unit}</Text> : null}
            </View>
          )}

          {question.type === "text" && (
            <TextInput
              style={styles.textInput}
              multiline
              placeholder="Escribe aquí…"
              placeholderTextColor={colors.textFaint}
              value={typeof value === "string" ? value : ""}
              onChangeText={(t) => setAnswer(t)}
              maxLength={500}
            />
          )}

          {question.type === "tags" && (
            <View style={styles.tagsWrap}>
              {(question.tags ?? []).map((tag) => {
                const selected = Array.isArray(value) && (value as string[]).includes(tag);
                return (
                  <PressableScale key={tag} style={[styles.tag, selected && styles.tagSelected]} onPress={() => toggleTag(tag)} scaleTo={0.94}>
                    <Text style={[styles.tagText, selected && styles.tagTextSelected]}>{selected ? "✓ " : ""}{tag}</Text>
                  </PressableScale>
                );
              })}
            </View>
          )}

          {question.type === "tags_catalog" && (
            <View style={styles.tagsWrap}>
              {(question.options ?? []).map((opt) => {
                const selected = Array.isArray(value) && (value as string[]).includes(opt.id);
                return (
                  <PressableScale key={opt.id} style={[styles.tag, selected && styles.tagSelected]} onPress={() => toggleTag(opt.id)} scaleTo={0.94}>
                    <Text style={[styles.tagText, selected && styles.tagTextSelected]}>
                      {selected ? "✓ " : ""}
                      {opt.icon} {opt.text}
                    </Text>
                  </PressableScale>
                );
              })}
              {(question.options ?? []).length === 0 && <Text style={styles.hint}>Tu catálogo todavía está vacío.</Text>}
            </View>
          )}

          {!KNOWN_TYPES.includes(question.type) && (
            <Text style={styles.hint}>Esta pregunta aún no se puede mostrar en esta versión de la app. Puedes omitirla.</Text>
          )}
        </Animated.View>
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: 16 + insets.bottom }]}>
        <GradientButton title="Atrás" variant="ghost" onPress={handleBack} disabled={isSaving} style={{ flex: 1 }} />
        {!isLast ? (
          <GradientButton title="Omitir" variant="ghost" onPress={() => setStepIndex((i) => i + 1)} disabled={isSaving} style={{ flex: 1 }} />
        ) : null}
        <GradientButton title={isLast ? "Guardar" : "Continuar"} onPress={handleNext} loading={isSaving} style={{ flex: 1.6 }} />
      </View>
    </KeyboardAvoidingView>
  );
}

const KNOWN_TYPES = ["options", "range", "tags", "tags_catalog", "number", "text"];

function rangeDefault(q: Question): number {
  const min = q.min ?? 0;
  const max = q.max ?? 100;
  return Math.round((min + max) / 2);
}

function RangeInput({
  min,
  max,
  unit,
  value,
  touched,
  onChange,
}: {
  min: number;
  max: number;
  unit: string;
  value: number;
  touched: boolean;
  onChange: (v: number) => void;
}) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  return (
    <View style={styles.rangeWrap}>
      <Text style={[styles.rangeVal, !touched && { opacity: 0.5 }]}>
        {value} <Text style={styles.rangeUnit}>{unit}</Text>
      </Text>
      <Slider
        style={styles.rangeSlider}
        minimumValue={min}
        maximumValue={max}
        step={1}
        value={value}
        onValueChange={onChange}
        minimumTrackTintColor={colors.primary}
        maximumTrackTintColor={colors.track}
        thumbTintColor={colors.primary}
      />
      <Text style={styles.rangeMinMax}>
        {min} – {max} {unit}
      </Text>
    </View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  headRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  iconBtn: { width: 36, height: 36, borderRadius: 12, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center" as const, justifyContent: "center" as const },
  iconBtnText: { color: "#fff", fontSize: 16 },
  title: { color: "#fff", fontSize: 17, fontWeight: "700" as const },
  step: { color: "rgba(255,255,255,0.8)", fontSize: 12, marginTop: 1 },
  saveBtn: { backgroundColor: "rgba(255,255,255,0.22)", borderRadius: 12, paddingHorizontal: 14, paddingVertical: 9 },
  saveBtnText: { color: "#fff", fontWeight: "700" as const, fontSize: 13 },
  dots: { flexDirection: "row" as const, gap: 5, marginTop: 12 },
  dot: { height: 5, borderRadius: 3, backgroundColor: "rgba(255,255,255,0.25)" },
  dotAnswered: { backgroundColor: "rgba(255,255,255,0.75)" },
  dotCurrent: { backgroundColor: "#fff", height: 7, marginTop: -1 },
  body: { padding: 16, paddingTop: 22, paddingBottom: 24 },
  prompt: { fontSize: 19, fontWeight: "700" as const, color: colors.text, marginBottom: 6, lineHeight: 26 },
  hint: { fontSize: 13, color: colors.textMuted, marginBottom: 18, lineHeight: 19 },
  option: {
    padding: 13,
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: 16,
    backgroundColor: colors.card,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 12,
  },
  optionSelected: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  optionIconWrap: { width: 40, height: 40, borderRadius: 13, backgroundColor: colors.cardAlt, alignItems: "center" as const, justifyContent: "center" as const },
  optionIconWrapSelected: { backgroundColor: colors.card },
  optionIcon: { fontSize: 19 },
  optionText: { fontSize: 14, fontWeight: "600" as const, color: colors.text },
  optionSub: { fontSize: 12, color: colors.textMuted, marginTop: 1 },
  check: { color: colors.primary, fontSize: 18, fontWeight: "700" as const },
  tagsWrap: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 8 },
  tag: { paddingHorizontal: 14, paddingVertical: 10, borderWidth: 1.5, borderColor: colors.border, borderRadius: 14, backgroundColor: colors.card },
  tagSelected: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  tagText: { fontSize: 13, color: colors.textMuted, fontWeight: "600" as const },
  tagTextSelected: { color: colors.success },
  rangeWrap: { backgroundColor: colors.card, borderRadius: 18, padding: 20, alignItems: "center" as const, ...shadow },
  rangeVal: { fontSize: 36, fontWeight: "700" as const, color: colors.primary, marginBottom: 8 },
  rangeUnit: { fontSize: 15, color: colors.textMuted, fontWeight: "400" as const },
  rangeSlider: { width: "100%" as const, height: 40 },
  rangeMinMax: { fontSize: 11, color: colors.textFaint, marginTop: 8 },
  inputCard: { flexDirection: "row" as const, alignItems: "center" as const, gap: 10, backgroundColor: colors.card, borderRadius: 18, padding: 16, ...shadow },
  numberInput: { flex: 1, fontSize: 32, fontWeight: "700" as const, color: colors.primary },
  unit: { fontSize: 15, color: colors.textMuted },
  textInput: {
    minHeight: 110,
    textAlignVertical: "top" as const,
    backgroundColor: colors.card,
    borderRadius: 16,
    padding: 14,
    fontSize: 14,
    color: colors.text,
    borderWidth: 1.5,
    borderColor: colors.border,
  },
  footer: { flexDirection: "row" as const, gap: 10, padding: 16, backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border },
});
