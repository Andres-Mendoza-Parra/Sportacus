import React, { useMemo, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from "react-native";
import Animated, { FadeIn, FadeInRight, FadeOutLeft } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { AnswerValue, Answers, FormSpec, Question, QuestionMeta } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import { useConfirm, useToast } from "../ui/Feedback";
import GradientButton from "../ui/GradientButton";
import PressableScale from "../ui/PressableScale";
import WizardHeader from "./WizardHeader";
import QuestionInput from "./QuestionInput";
import { initialAnswers, isAnswered, isRequired, payloadFor, rangeDefault, toggleMulti, validate, visibleQuestions } from "./formLogic";

export interface FormWizardProps {
  spec: FormSpec;
  /** Texto del botón en la última pregunta (por defecto "Guardar"). */
  submitLabel?: string;
  /** Recibe solo las respuestas de preguntas visibles. Debe mostrar sus propios errores; si lanza, el wizard sigue abierto. */
  onSubmit: (answers: Answers) => Promise<void>;
  /** Salir (botón de la cabecera o "Atrás" en la primera pregunta). Sin él no hay botón de salida. */
  onExit?: () => void;
  exitIcon?: "back" | "close";
  /** Pide confirmación al salir con cambios (por defecto sí). */
  confirmExit?: boolean;
  /** Modo sin presión: ninguna pregunta es obligatoria y se puede enviar con lo que haya. */
  optional?: boolean;
  /** Datos por pregunta (para qué se usa, estado anterior) del Perfil 360. */
  meta?: Record<string, QuestionMeta>;
  /** Activa "Prefiero no decir": se llama con el id de la pregunta antes de pasar a la siguiente. */
  onDecline?: (questionId: string) => Promise<void>;
  /**
   * Inscripción rápida: primero las obligatorias; las opcionales quedan plegadas bajo
   * "Afinar más (opcional)" en la última pregunta obligatoria.
   */
  deferOptional?: boolean;
}

const STATUS_NOTES: Record<string, string> = {
  no_decir: "Antes elegiste no decirlo. Puedes responder ahora si quieres.",
  omitida: "La omitiste antes. Sin problema si la vuelves a omitir.",
  pospuesta: "La dejaste para más tarde.",
};

/**
 * Asistente genérico de formularios (FormSpec del backend): una pregunta por pantalla,
 * con showIf, obligatorias, máximo de elecciones y opción excluyente "ninguno/ninguna".
 */
export default function FormWizard({
  spec,
  submitLabel = "Guardar",
  onSubmit,
  onExit,
  exitIcon = "back",
  confirmExit = true,
  optional = false,
  meta,
  onDecline,
  deferOptional = false,
}: FormWizardProps) {
  const styles = useThemedStyles(createStyles);
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const confirm = useConfirm();

  // Con deferOptional las obligatorias van primero (orden estable dentro de cada grupo).
  const ordered = useMemo<FormSpec>(() => {
    if (!deferOptional) return spec;
    return { ...spec, questions: [...spec.questions.filter((q) => q.required), ...spec.questions.filter((q) => !q.required)] };
  }, [spec, deferOptional]);
  const hasRequired = useMemo(() => spec.questions.some((q) => q.required), [spec]);

  const [answers, setAnswers] = useState<Answers>(() => initialAnswers(spec));
  const [currentId, setCurrentId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [declining, setDeclining] = useState(false);
  const [declined, setDeclined] = useState<string[]>([]);
  const [expanded, setExpanded] = useState(!deferOptional || !hasRequired);
  const [showWhy, setShowWhy] = useState(false);

  function listFor(a: Answers, open = expanded): Question[] {
    const all = visibleQuestions(ordered, a);
    return open ? all : all.filter((q) => q.required);
  }

  const visible = useMemo(() => {
    const all = visibleQuestions(ordered, answers);
    return expanded ? all : all.filter((q) => q.required);
  }, [ordered, answers, expanded]);
  const pendingOptional = useMemo(
    () => (expanded ? 0 : visibleQuestions(ordered, answers).filter((q) => !q.required).length),
    [ordered, answers, expanded],
  );
  const index = Math.max(0, visible.findIndex((q) => q.id === currentId));
  const question = visible[index];
  const isLast = index === visible.length - 1;

  if (!question) return null;
  const value = answers[question.id];
  const required = isRequired(question, optional);
  const info = meta?.[question.id];

  function setValue(next: AnswerValue | undefined) {
    setAnswers((prev) => {
      const copy = { ...prev };
      if (next === undefined) delete copy[question.id];
      else copy[question.id] = next;
      return copy;
    });
    setDeclined((prev) => prev.filter((id) => id !== question.id));
    setDirty(true);
    setError(null);
  }

  function toggle(id: string) {
    const current = Array.isArray(value) ? value : [];
    const next = toggleMulti(question, current, id);
    if (next === null) {
      toast.info(`Puedes elegir hasta ${question.max} opciones.`);
      return;
    }
    setValue(next);
  }

  function goTo(i: number) {
    const q = visible[i];
    if (q) {
      setCurrentId(q.id);
      setError(null);
      setShowWhy(false);
    }
  }

  async function submit(merged: Answers, skipIds: string[]) {
    const payload = payloadFor(ordered, merged);
    for (const id of skipIds) delete payload[id];
    setSaving(true);
    try {
      await onSubmit(payload);
      setDirty(false);
    } catch {
      // quien llama muestra el error (toast); el asistente sigue abierto para reintentar
    } finally {
      setSaving(false);
    }
  }

  /** Avanza desde la pregunta actual con las respuestas `merged`; en la última, revisa y envía. */
  async function advance(merged: Answers, skipIds: string[]) {
    const nowVisible = listFor(merged);
    const pos = nowVisible.findIndex((q) => q.id === question.id);
    if (pos < nowVisible.length - 1) {
      setCurrentId(nowVisible[pos + 1].id);
      setShowWhy(false);
      return;
    }
    // Revisión final: ninguna obligatoria visible puede quedar vacía.
    const missing = nowVisible.find((q) => validate(q, merged[q.id], optional) !== null);
    if (missing) {
      setCurrentId(missing.id);
      setError(validate(missing, merged[missing.id], optional));
      return;
    }
    await submit(merged, skipIds);
  }

  async function handleNext() {
    let effective = value;
    // En un deslizador intacto, continuar confirma el valor que se ve en pantalla.
    if (question.type === "range" && !isAnswered(effective)) {
      effective = rangeDefault(question);
      setValue(effective);
    }
    const message = validate(question, effective, optional);
    if (message) {
      setError(message);
      return;
    }
    const merged: Answers = effective === undefined ? answers : { ...answers, [question.id]: effective };
    await advance(merged, declined.filter((id) => id !== question.id));
  }

  /** Omitir sin responder (solo preguntas no obligatorias). */
  async function handleSkip() {
    const merged = { ...answers };
    delete merged[question.id];
    setAnswers(merged);
    setError(null);
    await advance(merged, declined);
  }

  async function handleDecline() {
    if (!onDecline) return;
    setDeclining(true);
    try {
      await onDecline(question.id);
    } catch {
      setDeclining(false);
      return; // quien llama muestra el error
    }
    setDeclining(false);
    const merged = { ...answers };
    delete merged[question.id];
    const nextDeclined = [...declined.filter((id) => id !== question.id), question.id];
    setAnswers(merged);
    setDeclined(nextDeclined);
    setError(null);
    await advance(merged, nextDeclined);
  }

  function expandOptional() {
    const all = listFor(answers, true);
    const firstOptional = all.find((q) => !q.required);
    setExpanded(true);
    if (firstOptional) setCurrentId(firstOptional.id);
    setError(null);
    setShowWhy(false);
  }

  async function handleExit() {
    if (!onExit) return;
    if (dirty && confirmExit) {
      const ok = await confirm({
        title: "¿Salir sin guardar?",
        message: optional ? "Lo que respondiste en este cuestionario no se guardará." : "Tus respuestas de este formulario se perderán.",
        confirmText: "Salir",
        destructive: true,
      });
      if (!ok) return;
    }
    onExit();
  }

  function handleBack() {
    if (index > 0) goTo(index - 1);
    else handleExit();
  }

  const busy = saving || declining;
  const statusNote = info?.status ? STATUS_NOTES[info.status] : undefined;

  return (
    <KeyboardAvoidingView style={styles.bg} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <WizardHeader
        title={spec.title}
        subtitle={index === 0 ? spec.subtitle : null}
        index={index}
        questions={visible}
        answers={answers}
        exitIcon={onExit ? exitIcon : null}
        onExit={handleExit}
        onJump={(i) => i < index && goTo(i)}
      />

      <ScrollView style={{ flex: 1 }} contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
        <Animated.View key={question.id} entering={FadeInRight.duration(260)} exiting={FadeOutLeft.duration(140)}>
          <Text style={styles.prompt}>
            {question.prompt}
            {required ? null : <Text style={styles.optional}>  (opcional)</Text>}
          </Text>
          {question.hint ? <Text style={styles.hint}>{question.hint}</Text> : null}
          {info?.why ? (
            <PressableScale
              onPress={() => setShowWhy((s) => !s)}
              accessibilityRole="button"
              accessibilityState={{ expanded: showWhy }}
              style={styles.whyBtn}
            >
              <Text style={styles.whyTitle}>{showWhy ? "▾" : "▸"} ¿Por qué lo preguntamos?</Text>
              {showWhy ? <Text style={styles.whyBody}>{info.why}</Text> : null}
            </PressableScale>
          ) : null}
          {statusNote ? <Text style={styles.status}>{statusNote}</Text> : null}
          {!question.hint && !info?.why && !statusNote ? <View style={{ height: 14 }} /> : <View style={{ height: 6 }} />}
          <QuestionInput question={question} value={value} onChange={setValue} onToggle={toggle} />
          {error ? (
            <Animated.Text entering={FadeIn.duration(150)} style={styles.error} accessibilityRole="alert">
              {error}
            </Animated.Text>
          ) : null}
          {onDecline ? (
            <PressableScale onPress={handleDecline} disabled={busy} accessibilityRole="button" style={styles.declineBtn}>
              <Text style={styles.declineText}>{declining ? "Guardando…" : "🤐 Prefiero no decir"}</Text>
            </PressableScale>
          ) : null}
          {isLast && pendingOptional > 0 ? (
            <View style={styles.refine}>
              <Text style={styles.refineTitle}>¿Quieres afinar más?</Text>
              <Text style={styles.refineBody}>
                {`Con esto ya armamos tu plan. Hay ${pendingOptional} ${pendingOptional === 1 ? "pregunta opcional" : "preguntas opcionales"} para ajustarlo mejor; puedes responderlas ahora o más adelante.`}
              </Text>
              <GradientButton title="Afinar más (opcional)" variant="ghost" onPress={expandOptional} disabled={busy} style={{ marginTop: 10 }} />
            </View>
          ) : null}
        </Animated.View>
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: 16 + insets.bottom }]}>
        {index > 0 || onExit ? (
          <GradientButton title="Atrás" variant="ghost" onPress={handleBack} disabled={busy} style={{ flex: 1 }} />
        ) : null}
        {!required && !isLast ? (
          <GradientButton title="Omitir" variant="ghost" onPress={handleSkip} disabled={busy} style={{ flex: 1 }} />
        ) : null}
        <GradientButton title={isLast ? submitLabel : "Continuar"} onPress={handleNext} loading={saving} disabled={declining} style={{ flex: 1.6 }} />
      </View>
    </KeyboardAvoidingView>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  body: { padding: 16, paddingTop: 22, paddingBottom: 24, maxWidth: 640, width: "100%" as const, alignSelf: "center" as const },
  prompt: { fontSize: 19, fontWeight: "700" as const, color: colors.text, marginBottom: 6, lineHeight: 26 },
  optional: { fontSize: 13, fontWeight: "400" as const, color: colors.textFaint },
  hint: { fontSize: 13, color: colors.textMuted, marginBottom: 8, lineHeight: 19 },
  whyBtn: { alignSelf: "stretch" as const, paddingVertical: 8, paddingHorizontal: 12, borderRadius: 12, backgroundColor: colors.infoSoft, marginBottom: 4 },
  whyTitle: { fontSize: 12, fontWeight: "700" as const, color: colors.info },
  whyBody: { fontSize: 13, color: colors.text, lineHeight: 19, marginTop: 4 },
  status: { fontSize: 12, color: colors.textMuted, fontStyle: "italic" as const, marginTop: 4 },
  error: { color: colors.danger, fontSize: 13, marginTop: 12, fontWeight: "600" as const },
  declineBtn: { alignSelf: "center" as const, paddingVertical: 10, paddingHorizontal: 14, marginTop: 14 },
  declineText: { color: colors.textMuted, fontSize: 13, fontWeight: "600" as const },
  refine: { marginTop: 18, padding: 14, borderRadius: 16, backgroundColor: colors.primarySoft },
  refineTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  refineBody: { fontSize: 12, color: colors.textMuted, lineHeight: 18, marginTop: 4 },
  footer: { flexDirection: "row" as const, gap: 10, padding: 16, backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border },
});
