import React, { useMemo, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from "react-native";
import Animated, { FadeIn, FadeInRight, FadeOutLeft } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { AnswerValue, Answers, FormSpec } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import { useConfirm, useToast } from "../ui/Feedback";
import GradientButton from "../ui/GradientButton";
import WizardHeader from "./WizardHeader";
import QuestionInput from "./QuestionInput";
import { initialAnswers, isAnswered, payloadFor, rangeDefault, toggleMulti, validate, visibleQuestions } from "./formLogic";

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
}

/**
 * Asistente genérico de formularios (FormSpec del backend): una pregunta por pantalla,
 * con showIf, obligatorias, máximo de elecciones y opción excluyente "ninguno/ninguna".
 */
export default function FormWizard({ spec, submitLabel = "Guardar", onSubmit, onExit, exitIcon = "back", confirmExit = true }: FormWizardProps) {
  const styles = useThemedStyles(createStyles);
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const confirm = useConfirm();

  const [answers, setAnswers] = useState<Answers>(() => initialAnswers(spec));
  const [currentId, setCurrentId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);

  const visible = useMemo(() => visibleQuestions(spec, answers), [spec, answers]);
  const index = Math.max(0, visible.findIndex((q) => q.id === currentId));
  const question = visible[index];
  const isLast = index === visible.length - 1;

  if (!question) return null;
  const value = answers[question.id];

  function setValue(next: AnswerValue | undefined) {
    setAnswers((prev) => {
      const copy = { ...prev };
      if (next === undefined) delete copy[question.id];
      else copy[question.id] = next;
      return copy;
    });
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
    }
  }

  async function handleNext() {
    let effective = value;
    // En un deslizador intacto, continuar confirma el valor que se ve en pantalla.
    if (question.type === "range" && !isAnswered(effective)) {
      effective = rangeDefault(question);
      setValue(effective);
    }
    const message = validate(question, effective);
    if (message) {
      setError(message);
      return;
    }
    const merged: Answers = effective === undefined ? answers : { ...answers, [question.id]: effective };
    const nowVisible = visibleQuestions(spec, merged);
    const pos = nowVisible.findIndex((q) => q.id === question.id);
    if (pos < nowVisible.length - 1) {
      setCurrentId(nowVisible[pos + 1].id);
      return;
    }
    // Revisión final: ninguna obligatoria visible puede quedar vacía.
    const missing = nowVisible.find((q) => validate(q, merged[q.id]) !== null);
    if (missing) {
      setCurrentId(missing.id);
      setError(validate(missing, merged[missing.id]));
      return;
    }
    setSaving(true);
    try {
      await onSubmit(payloadFor(spec, merged));
      setDirty(false);
    } catch {
      // quien llama muestra el error (toast); el asistente sigue abierto para reintentar
    } finally {
      setSaving(false);
    }
  }

  async function handleExit() {
    if (!onExit) return;
    if (dirty && confirmExit) {
      const ok = await confirm({
        title: "¿Salir sin guardar?",
        message: "Tus respuestas de este formulario se perderán.",
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
            {question.required ? null : <Text style={styles.optional}>  (opcional)</Text>}
          </Text>
          {question.hint ? <Text style={styles.hint}>{question.hint}</Text> : <View style={{ height: 14 }} />}
          <QuestionInput question={question} value={value} onChange={setValue} onToggle={toggle} />
          {error ? (
            <Animated.Text entering={FadeIn.duration(150)} style={styles.error} accessibilityRole="alert">
              {error}
            </Animated.Text>
          ) : null}
        </Animated.View>
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: 16 + insets.bottom }]}>
        {index > 0 || onExit ? (
          <GradientButton title="Atrás" variant="ghost" onPress={handleBack} disabled={saving} style={{ flex: 1 }} />
        ) : null}
        {!question.required && !isLast ? (
          <GradientButton title="Omitir" variant="ghost" onPress={() => goTo(index + 1)} disabled={saving} style={{ flex: 1 }} />
        ) : null}
        <GradientButton title={isLast ? submitLabel : "Continuar"} onPress={handleNext} loading={saving} style={{ flex: 1.6 }} />
      </View>
    </KeyboardAvoidingView>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  body: { padding: 16, paddingTop: 22, paddingBottom: 24, maxWidth: 640, width: "100%" as const, alignSelf: "center" as const },
  prompt: { fontSize: 19, fontWeight: "700" as const, color: colors.text, marginBottom: 6, lineHeight: 26 },
  optional: { fontSize: 13, fontWeight: "400" as const, color: colors.textFaint },
  hint: { fontSize: 13, color: colors.textMuted, marginBottom: 18, lineHeight: 19 },
  error: { color: colors.danger, fontSize: 13, marginTop: 12, fontWeight: "600" as const },
  footer: { flexDirection: "row" as const, gap: 10, padding: 16, backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border },
});
