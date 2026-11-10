import { router } from "expo-router";
import { useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, Text, TextInput, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import NumberField from "../components/session/NumberField";
import RpePicker from "../components/session/checks/RpePicker";
import SessionResultView from "../components/session/SessionResultView";
import { Card } from "../components/ui/Card";
import Chip from "../components/ui/Chip";
import { useToast } from "../components/ui/Feedback";
import GradientButton from "../components/ui/GradientButton";
import ScreenHeader from "../components/ui/ScreenHeader";
import { todayIso } from "../constants/labels";
import { useWorld } from "../context/WorldContext";
import { extractErrorMessage } from "../services/api";
import { SessionResult } from "../services/types";
import { logSession } from "../services/worldService";
import { Theme, useThemedStyles, useTheme } from "../theme";

const ACTIVITIES = [
  { label: "Caminar", icon: "🚶" },
  { label: "Correr", icon: "🏃" },
  { label: "Bici", icon: "🚴" },
  { label: "Bailar", icon: "💃" },
  { label: "Nadar", icon: "🏊" },
  { label: "Fútbol u otro deporte", icon: "⚽" },
  { label: "Yoga o estiramientos", icon: "🧘" },
  { label: "Fuerza", icon: "🏋️" },
  { label: "Tareas activas", icon: "🧹" },
  { label: "Otra", icon: "✨" },
] as const;

const QUICK_MINUTES = [10, 20, 30, 45, 60, 90];
const NOTES_MAX = 300;

function yesterdayIso(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Registro libre de actividad (cuenta en Mi Base): tipo, minutos, esfuerzo, día y notas. */
export default function LogActivityScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const { invalidate } = useWorld();
  const [activity, setActivity] = useState<string | null>(null);
  const [other, setOther] = useState("");
  const [minutes, setMinutes] = useState<number | undefined>(undefined);
  const [rpe, setRpe] = useState<number | undefined>(undefined);
  const [day, setDay] = useState<"hoy" | "ayer">("hoy");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<SessionResult | null>(null);

  function close() {
    if (router.canGoBack()) router.back();
    else router.replace("/home");
  }

  async function submit() {
    if (!activity) return setError("Elige qué actividad hiciste.");
    if (!minutes || minutes < 1 || minutes > 600) return setError("Indica cuántos minutos (entre 1 y 600).");
    setError(null);
    const title = activity === "Otra" && other.trim() ? other.trim() : activity;
    setSaving(true);
    try {
      const saved = await logSession(
        {
          planSessionId: null,
          title,
          performedOn: day === "hoy" ? todayIso() : yesterdayIso(),
          durationMin: Math.round(minutes),
          rpe,
          notes: notes.trim() || undefined,
          blocks: [],
        },
        "base",
      );
      invalidate();
      setResult(saved);
    } catch (e) {
      toast.error(extractErrorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  if (result) {
    return (
      <View style={styles.bg}>
        <ScreenHeader title="Actividad registrada" subtitle="Suma en Mi Base" back={false} />
        <ScrollView contentContainerStyle={[styles.body, { paddingBottom: insets.bottom + 24 }]}>
          <SessionResultView result={result} />
          <GradientButton title="Listo" onPress={close} />
        </ScrollView>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView style={styles.bg} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <ScreenHeader title="Registrar actividad" subtitle="Todo movimiento cuenta. Se guarda en Mi Base." />
      <ScrollView contentContainerStyle={[styles.body, { paddingBottom: insets.bottom + 24 }]} keyboardShouldPersistTaps="handled">
        <Card style={{ gap: 10 }}>
          <Text style={styles.label}>¿Qué hiciste?</Text>
          <View style={styles.chips}>
            {ACTIVITIES.map((a) => (
              <Chip
                key={a.label}
                label={`${a.icon} ${a.label}`}
                selected={activity === a.label}
                onPress={() => {
                  setActivity(a.label);
                  setError(null);
                }}
              />
            ))}
          </View>
          {activity === "Otra" ? (
            <TextInput
              style={styles.input}
              placeholder="¿Cuál? (opcional)"
              placeholderTextColor={colors.textFaint}
              value={other}
              onChangeText={setOther}
              maxLength={60}
              accessibilityLabel="Nombre de la actividad"
            />
          ) : null}
        </Card>

        <Card style={{ gap: 10 }}>
          <Text style={styles.label}>¿Cuántos minutos?</Text>
          <View style={styles.chips}>
            {QUICK_MINUTES.map((m) => (
              <Chip key={m} label={`${m} min`} selected={minutes === m} onPress={() => setMinutes(m)} />
            ))}
          </View>
          <NumberField label="Otro valor" unit="min" value={minutes} decimal={false} onChange={setMinutes} />
        </Card>

        <RpePicker value={rpe} onChange={setRpe} title="¿Qué tan intensa fue? (opcional)" />

        <Card style={{ gap: 10 }}>
          <Text style={styles.label}>¿Cuándo?</Text>
          <View style={styles.chips}>
            <Chip label="Hoy" selected={day === "hoy"} onPress={() => setDay("hoy")} />
            <Chip label="Ayer" selected={day === "ayer"} onPress={() => setDay("ayer")} />
          </View>
          <Text style={styles.label}>Notas (opcional)</Text>
          <TextInput
            style={[styles.input, { minHeight: 80, textAlignVertical: "top" }]}
            multiline
            placeholder="¿Cómo te sentiste? ¿Con quién fuiste?"
            placeholderTextColor={colors.textFaint}
            value={notes}
            onChangeText={setNotes}
            maxLength={NOTES_MAX}
            accessibilityLabel="Notas"
          />
        </Card>

        {error ? (
          <Text style={styles.error} accessibilityRole="alert">
            {error}
          </Text>
        ) : null}
        <GradientButton title="Guardar actividad" onPress={submit} loading={saving} />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  body: { padding: 16, gap: 12, maxWidth: 640, width: "100%" as const, alignSelf: "center" as const },
  label: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  chips: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 8 },
  input: {
    backgroundColor: colors.inputBg,
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: colors.border,
    padding: 12,
    fontSize: 14,
    color: colors.text,
  },
  error: { color: colors.danger, fontSize: 13, fontWeight: "600" as const, textAlign: "center" as const },
});
