import { router, useFocusEffect } from "expo-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { ScrollView, Text, TextInput, Vibration, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { useToast } from "../components/ui/Feedback";
import GradientButton from "../components/ui/GradientButton";
import GradientHeader from "../components/ui/GradientHeader";
import PressableScale from "../components/ui/PressableScale";
import { formatClock, useCountdown } from "../components/ui/useCountdown";
import { extractErrorMessage } from "../services/api";
import { MetricSeries, fetchProgress, saveMetric } from "../services/progressService";
import { invalidateUserContext } from "../services/contextBus";
import { Theme, useThemedStyles, useTheme } from "../theme";

type TestType = "count30" | "stopwatch" | "manual";

interface TestDef {
  key: string;
  label: string;
  icon: string;
  unit: string;
  type: TestType;
  description: string;
  steps: string[];
  /** Tope para pruebas de tiempo (s). */
  maxSeconds?: number;
}

const TESTS: TestDef[] = [
  {
    key: "test_sentadillas_30s",
    label: "Sentadillas en 30 s",
    icon: "🦵",
    unit: "reps",
    type: "count30",
    description: "Fuerza y resistencia de piernas.",
    steps: ["Pies al ancho de hombros, baja hasta que los muslos queden paralelos al suelo.", "Cuenta solo las repeticiones completas durante 30 segundos."],
  },
  {
    key: "test_flexiones_30s",
    label: "Flexiones en 30 s",
    icon: "💪",
    unit: "reps",
    type: "count30",
    description: "Fuerza de empuje del tren superior.",
    steps: ["Puedes apoyar las rodillas si lo necesitas, siempre igual en cada prueba.", "Baja el pecho cerca del suelo y sube con el cuerpo alineado."],
  },
  {
    key: "test_plancha_s",
    label: "Plancha",
    icon: "🧘",
    unit: "s",
    type: "stopwatch",
    maxSeconds: 180,
    description: "Resistencia del core.",
    steps: ["Apoya antebrazos y puntas de los pies, cuerpo en línea recta.", "Detén el cronómetro cuando la cadera se hunda o suba o ya no puedas más."],
  },
  {
    key: "test_equilibrio_s",
    label: "Equilibrio a una pierna",
    icon: "⚖️",
    unit: "s",
    type: "stopwatch",
    maxSeconds: 60,
    description: "Estabilidad y control.",
    steps: ["Apóyate cerca de una pared por seguridad, ojos abiertos.", "Levanta un pie y detén el cronómetro cuando lo vuelvas a apoyar. Máximo 60 s."],
  },
  {
    key: "test_flexibilidad_cm",
    label: "Flexibilidad (sentarse y alcanzar)",
    icon: "🤸",
    unit: "cm",
    type: "manual",
    description: "Flexibilidad de espalda baja e isquiotibiales.",
    steps: ["Sentado con las piernas estiradas, alcanza lo más lejos que puedas sin rebotar.", "Mide desde la punta de los pies: positivo si pasas los pies, negativo si no llegas."],
  },
];

export default function TestsScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const [series, setSeries] = useState<Map<string, MetricSeries>>(new Map());
  const [active, setActive] = useState<TestDef | null>(null);

  const load = useCallback(async () => {
    try {
      const p = await fetchProgress();
      setSeries(new Map(p.series.map((s) => [s.key, s])));
    } catch (error) {
      toast.error(extractErrorMessage(error));
    }
  }, [toast]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  async function save(def: TestDef, value: number) {
    try {
      await saveMetric(def.key, value, def.unit);
      invalidateUserContext();
      toast.success("Resultado guardado");
      setActive(null);
      await load();
    } catch (error) {
      toast.error(extractErrorMessage(error));
    }
  }

  return (
    <ScrollView style={styles.bg} showsVerticalScrollIndicator={false}>
      <GradientHeader>
        <View style={styles.headTop}>
          <PressableScale style={styles.backBtn} onPress={() => (active ? setActive(null) : router.canGoBack() ? router.back() : router.replace("/progress"))}>
            <Text style={styles.backText}>←</Text>
          </PressableScale>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>Evaluación física</Text>
            <Text style={styles.subtitle}>Mide tu nivel con pruebas guiadas</Text>
          </View>
        </View>
      </GradientHeader>

      {active ? (
        <TestRunner key={active.key} def={active} onSave={(v) => save(active, v)} onCancel={() => setActive(null)} />
      ) : (
        <View style={{ padding: 16, gap: 12 }}>
          <Text style={styles.hint}>Repite las pruebas cada 4–6 semanas, en condiciones parecidas, para ver tu mejora real. Calienta antes y detente si sientes dolor.</Text>
          {TESTS.map((t, i) => {
            const s = series.get(t.key);
            const last = s?.points[s.points.length - 1];
            const prev = s && s.points.length > 1 ? s.points[s.points.length - 2] : null;
            const diff = last && prev ? Math.round((last.value - prev.value) * 10) / 10 : null;
            return (
              <Animated.View key={t.key} entering={FadeInDown.delay(i * 60).duration(350)}>
                <PressableScale style={styles.card} onPress={() => setActive(t)}>
                  <Text style={{ fontSize: 30 }}>{t.icon}</Text>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.cardTitle}>{t.label}</Text>
                    <Text style={styles.muted}>{t.description}</Text>
                    {last ? (
                      <Text style={styles.result}>
                        Último: {last.value} {t.unit} ({last.date})
                        {diff != null ? (
                          <Text style={{ color: diff >= 0 ? colors.success : colors.danger }}>  {diff > 0 ? "+" : ""}{diff}</Text>
                        ) : null}
                      </Text>
                    ) : (
                      <Text style={styles.muted}>Aún sin resultado</Text>
                    )}
                  </View>
                  <Text style={styles.arrow}>›</Text>
                </PressableScale>
              </Animated.View>
            );
          })}
        </View>
      )}
    </ScrollView>
  );
}

/** Ejecuta una prueba: cuenta repeticiones en 30 s, cronometra hasta que el usuario pare, o pide un valor. */
function TestRunner({ def, onSave, onCancel }: { def: TestDef; onSave: (value: number) => void; onCancel: () => void }) {
  const styles = useThemedStyles(createStyles);
  const [count, setCount] = useState(0);
  const [phase, setPhase] = useState<"intro" | "running" | "done">("intro");
  const [elapsed, setElapsed] = useState(0);
  const [manual, setManual] = useState("");
  const startedAt = useRef(0);
  const tick = useRef<ReturnType<typeof setInterval> | null>(null);
  const cd = useCountdown(() => setPhase("done"));

  useEffect(
    () => () => {
      if (tick.current) clearInterval(tick.current);
    },
    [],
  );

  function begin() {
    setPhase("running");
    if (def.type === "count30") {
      cd.start(30);
    } else {
      startedAt.current = Date.now();
      tick.current = setInterval(() => {
        const s = Math.floor((Date.now() - startedAt.current) / 1000);
        setElapsed(s);
        if (def.maxSeconds && s >= def.maxSeconds) stopWatch();
      }, 250);
    }
  }

  function stopWatch() {
    if (tick.current) clearInterval(tick.current);
    tick.current = null;
    setElapsed(Math.min(def.maxSeconds ?? Infinity, Math.floor((Date.now() - startedAt.current) / 1000)));
    setPhase("done");
    try {
      Vibration.vibrate(200);
    } catch {
      // sin vibración
    }
  }

  const value = def.type === "count30" ? count : def.type === "stopwatch" ? elapsed : Number(manual.replace(",", "."));
  const valid = def.type === "manual" ? manual.trim() !== "" && Number.isFinite(value) && Math.abs(value) < 200 : true;

  return (
    <View style={{ padding: 16, gap: 12 }}>
      <Text style={styles.cardTitle}>{def.icon} {def.label}</Text>
      {def.steps.map((s) => (
        <Text key={s} style={styles.muted}>• {s}</Text>
      ))}

      {phase === "intro" && def.type !== "manual" ? <GradientButton title="▶ Empezar" onPress={begin} /> : null}

      {phase === "running" && def.type === "count30" ? (
        <View style={styles.runBox}>
          <Text style={styles.clock}>{formatClock(cd.remaining)}</Text>
          <Text style={styles.big}>{count}</Text>
          <GradientButton title="+1 repetición" onPress={() => setCount((c) => c + 1)} />
          <GradientButton title="−1" variant="ghost" onPress={() => setCount((c) => Math.max(0, c - 1))} />
        </View>
      ) : null}

      {phase === "running" && def.type === "stopwatch" ? (
        <View style={styles.runBox}>
          <Text style={styles.big}>{formatClock(elapsed)}</Text>
          <GradientButton title="■ Detener" onPress={stopWatch} />
        </View>
      ) : null}

      {def.type === "manual" && phase !== "done" ? (
        <View style={styles.runBox}>
          <TextInput
            style={styles.input}
            keyboardType="numbers-and-punctuation"
            value={manual}
            onChangeText={setManual}
            placeholder="Ej: 12 o -5"
            placeholderTextColor="#999"
          />
        </View>
      ) : null}

      {phase === "done" || def.type === "manual" ? (
        <View style={styles.runBox}>
          {phase === "done" ? (
            <Text style={styles.big}>
              {value} <Text style={styles.muted}>{def.unit}</Text>
            </Text>
          ) : null}
          <GradientButton title="Guardar resultado" disabled={!valid} onPress={() => onSave(value)} />
        </View>
      ) : null}

      <GradientButton title="Cancelar" variant="ghost" onPress={onCancel} />
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  headTop: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  backBtn: { width: 38, height: 38, borderRadius: 19, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center" as const, justifyContent: "center" as const },
  backText: { color: "#fff", fontSize: 18 },
  title: { color: "#fff", fontSize: 22, fontWeight: "800" as const },
  subtitle: { color: "rgba(255,255,255,0.8)", fontSize: 13 },
  hint: { color: colors.textMuted, fontSize: 13, lineHeight: 19 },
  card: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, backgroundColor: colors.card, borderRadius: 16, padding: 14, borderWidth: 1, borderColor: colors.border },
  cardTitle: { color: colors.text, fontSize: 16, fontWeight: "700" as const },
  muted: { color: colors.textMuted, fontSize: 12, lineHeight: 17 },
  result: { color: colors.text, fontSize: 12, marginTop: 4 },
  arrow: { color: colors.textFaint, fontSize: 22 },
  runBox: { alignItems: "center" as const, gap: 10, backgroundColor: colors.card, borderRadius: 16, padding: 16 },
  clock: { color: colors.textMuted, fontSize: 22, fontWeight: "700" as const },
  big: { color: colors.primary, fontSize: 56, fontWeight: "800" as const },
  input: { width: "100%" as const, borderWidth: 1, borderColor: colors.border, borderRadius: 12, padding: 12, fontSize: 18, color: colors.text, backgroundColor: colors.inputBg, textAlign: "center" as const },
});
