import { Image } from "expo-image";
import { useKeepAwake } from "expo-keep-awake";
import { router, useLocalSearchParams } from "expo-router";
import * as Speech from "expo-speech";
import { useEffect, useMemo, useRef, useState } from "react";
import { ScrollView, Share, Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import AnimatedBar from "../components/ui/AnimatedBar";
import { useConfirm, useToast } from "../components/ui/Feedback";
import GradientButton from "../components/ui/GradientButton";
import PressableScale from "../components/ui/PressableScale";
import { ScreenSkeleton } from "../components/ui/Skeleton";
import { formatClock, useCountdown } from "../components/ui/useCountdown";
import { extractErrorMessage } from "../services/api";
import { CARE_MODES, CareConfig, loadCareMode } from "../services/careMode";
import { Feel, SessionResult, closeSession } from "../services/progressService";
import { Routine, fetchExerciseDetail, fetchRoutine, regenerateRoutine, setExerciseDone } from "../services/routineService";
import { buildCooldown, buildWarmup } from "../services/warmup";
import { Theme, useThemedStyles } from "../theme";

type Phase = "loading" | "check" | "work" | "feel" | "result";
type Safety = "ok" | "mild" | "pain";
type Mode = "idle" | "running" | "rest";

type Step =
  | { kind: "timed"; id: string; section: "warmup" | "cooldown"; title: string; seconds: number; tip: string }
  | {
      kind: "sets";
      id: string;
      recId: string;
      title: string;
      sets: number;
      reps: number | null;
      seconds: number | null;
      rest: number;
      image: string | null;
      tips: string[];
      muscle: string;
    };

const SECTION_LABEL = { warmup: "Calentamiento", work: "Entrenamiento", cooldown: "Enfriamiento" } as const;

function nowMs(): number {
  return Date.now();
}

function minutesSince(timestamp: number): number {
  return Math.max(1, Math.round((Date.now() - timestamp) / 60000));
}

/** "3 series × 10 reps" / "2 series × 45 seg" → números. */
function parseDetail(label: string): { sets: number; reps: number | null; seconds: number | null } {
  const m = /(\d+)\s*series?\s*[×x]\s*(\d+)\s*(reps|seg)/i.exec(label);
  if (!m) return { sets: 3, reps: 10, seconds: null };
  return { sets: Number(m[1]), reps: m[3].toLowerCase() === "reps" ? Number(m[2]) : null, seconds: m[3].toLowerCase() === "seg" ? Number(m[2]) : null };
}

export default function WorkoutScreen() {
  const styles = useThemedStyles(createStyles);
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const confirm = useConfirm();
  const params = useLocalSearchParams<{ day?: string }>();
  useKeepAwake();

  const [phase, setPhase] = useState<Phase>("loading");
  const [routine, setRoutine] = useState<Routine | null>(null);
  const [care, setCare] = useState<CareConfig>(CARE_MODES.standard);
  const [exercises, setExercises] = useState<Step[]>([]);
  const [groups, setGroups] = useState<string[]>([]);
  const [steps, setSteps] = useState<Step[]>([]);
  const [safety, setSafety] = useState<Safety>("ok");
  const [stepIndex, setStepIndex] = useState(0);
  const [setIndex, setSetIndex] = useState(0);
  const [mode, setMode] = useState<Mode>("idle");
  const [voice, setVoice] = useState(true);
  const [doneCount, setDoneCount] = useState(0);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<SessionResult | null>(null);
  const startedAt = useRef(0);
  const voiceRef = useRef(voice);
  const finishHandler = useRef<() => void>(() => {});
  useEffect(() => {
    voiceRef.current = voice;
  }, [voice]);

  function say(text: string) {
    if (!voiceRef.current) return;
    try {
      Speech.stop();
      Speech.speak(text, { language: "es-ES", rate: 0.95 });
    } catch {
      // sin voz disponible
    }
  }

  const cd = useCountdown(() => finishHandler.current());

  // Carga rutina, modo de cuidado y detalle de los ejercicios del día.
  useEffect(() => {
    (async () => {
      try {
        const [r, c] = await Promise.all([fetchRoutine(), loadCareMode()]);
        const requested = params.day != null ? Number(params.day) : -1;
        const dayIdx = r.days[requested] ? requested : Math.max(0, r.days.findIndex((d) => d.exercises.some((e) => !e.done)));
        const day = r.days[dayIdx] ?? r.days[0];
        const list = (day?.exercises ?? []).filter((e) => !e.done);
        const details = await Promise.all(list.map((e) => fetchExerciseDetail(e.exerciseRecommendationId).catch(() => null)));
        const built: Step[] = list.map((e, i) => {
          const parsed = parseDetail(e.detailLabel);
          const restTip = details[i]?.tips.map((t) => /Descansa (\d+) s/.exec(t)).find(Boolean);
          return {
            kind: "sets",
            id: e.exerciseRecommendationId,
            recId: e.exerciseRecommendationId,
            title: e.name,
            sets: parsed.sets,
            reps: parsed.reps,
            seconds: parsed.seconds,
            rest: restTip ? Number(restTip[1]) : 60,
            image: details[i]?.imageUrls[0] ?? e.imageUrl,
            tips: details[i]?.tips.filter((t) => !/Descansa/.test(t)).slice(0, 2) ?? [],
            muscle: e.muscleGroupTag,
          };
        });
        setRoutine(r);
        setCare(c);
        setExercises(built);
        setGroups([...new Set(list.map((e) => e.muscleGroupTag))]);
        setPhase(built.length === 0 ? "result" : "check");
      } catch (error) {
        toast.error(extractErrorMessage(error));
        router.back();
      }
    })();
    return () => {
      try {
        Speech.stop();
      } catch {
        // nada
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** Arma la lista de pasos según el chequeo de seguridad y el modo de cuidado. */
  function beginWorkout(choice: Safety) {
    setSafety(choice);
    const delta = care.setsDelta + (choice === "mild" ? 1 : 0);
    const adjusted: Step[] = exercises.map((s) =>
      s.kind === "sets" ? { ...s, sets: Math.max(1, s.sets - delta), rest: s.rest + care.restBonus } : s,
    );
    const warm: Step[] = buildWarmup(groups).map((m, i) => ({ kind: "timed", id: `w${i}`, section: "warmup", title: m.title, seconds: m.seconds, tip: m.tip }));
    const cool: Step[] = buildCooldown(groups).map((m, i) => ({ kind: "timed", id: `c${i}`, section: "cooldown", title: m.title, seconds: m.seconds, tip: m.tip }));
    const all = choice === "pain" ? [...warm, ...cool] : [...warm, ...adjusted, ...cool];
    setSteps(all);
    setStepIndex(0);
    setSetIndex(0);
    setMode("idle");
    startedAt.current = nowMs();
    setPhase("work");
    say(`Vamos. Empezamos con ${all[0].title}.`);
  }

  const step = steps[stepIndex];
  const section: keyof typeof SECTION_LABEL = step ? (step.kind === "sets" ? "work" : step.section) : "work";
  const totalWork = steps.length;
  const overallPct = totalWork > 0 ? Math.round((stepIndex / totalWork) * 100) : 0;

  function goNext() {
    cd.stop();
    setMode("idle");
    setSetIndex(0);
    if (stepIndex + 1 >= steps.length) {
      finishWorkout();
      return;
    }
    const next = steps[stepIndex + 1];
    setStepIndex(stepIndex + 1);
    say(next.kind === "sets" ? `Siguiente ejercicio: ${next.title}.` : `Siguiente: ${next.title}.`);
  }

  function finishWorkout() {
    if (doneCount === 0) {
      setPhase("result");
      return;
    }
    setPhase("feel");
    say("Sesión terminada. ¿Cómo te sentiste?");
  }

  function finishSet() {
    if (!step || step.kind !== "sets") return;
    if (setIndex + 1 < step.sets) {
      setSetIndex(setIndex + 1);
      setMode("rest");
      cd.start(step.rest);
      say(`Buen trabajo. Descansa ${step.rest} segundos.`);
      return;
    }
    // Última serie: el ejercicio queda marcado como hecho en el checklist de la rutina.
    setDoneCount((n) => n + 1);
    setExerciseDone(step.recId, true).catch(() => {});
    goNext();
  }

  function onTimerFinished() {
    if (!step) return;
    if (mode === "rest") {
      setMode("idle");
      say("Descanso terminado. Siguiente serie.");
    } else if (mode === "running") {
      if (step.kind === "timed") goNext();
      else finishSet();
    }
  }

  function startTimed(seconds: number) {
    setMode("running");
    cd.start(seconds);
    say(`${step?.title}. ${seconds} segundos.`);
  }

  // El manejador siempre ve el estado más reciente (paso, serie, modo).
  useEffect(() => {
    finishHandler.current = onTimerFinished;
  });

  // Aviso hablado a los 10 segundos finales.
  useEffect(() => {
    if (cd.running && cd.remaining === 10 && cd.total > 10) say("Diez segundos.");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cd.remaining]);

  async function quit() {
    const ok = await confirm({
      title: "¿Salir del entrenamiento?",
      message: doneCount > 0 ? "Lo que ya marcaste como hecho queda guardado en tu rutina." : undefined,
      confirmText: "Salir",
      destructive: true,
    });
    if (ok) {
      cd.stop();
      router.back();
    }
  }

  async function submitFeel(feel: Feel) {
    setSaving(true);
    try {
      const minutes = minutesSince(startedAt.current);
      const res = await closeSession({
        durationMin: minutes,
        feel,
        completedCount: doneCount,
        totalCount: exercises.length,
        activityName: routine?.activityName,
      });
      setResult(res);
      setPhase("result");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  async function regenerate() {
    try {
      await regenerateRoutine("refresh");
      toast.success("Rutina regenerada con tu nuevo nivel.");
      router.replace("/routine");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    }
  }

  const f = care.largeText ? 1.25 : 1;
  const bigText = useMemo(() => ({ fontSize: 22 * f }), [f]);

  if (phase === "loading") return <ScreenSkeleton />;

  // ───────────── Chequeo de seguridad ─────────────
  if (phase === "check") {
    return (
      <ScrollView style={styles.bg} contentContainerStyle={[styles.pad, { paddingTop: insets.top + 20 }]}>
        <Animated.View entering={FadeInDown.duration(350)}>
          <Text style={styles.kicker}>ANTES DE EMPEZAR</Text>
          <Text style={[styles.title, bigText]}>¿Sientes alguna molestia o dolor hoy?</Text>
          <Text style={styles.sub}>Tu respuesta ajusta la sesión para cuidarte.</Text>

          {care.notes.length > 0 ? (
            <View style={styles.noteBox}>
              <Text style={styles.noteTitle}>{care.icon} Modo {care.label}</Text>
              {care.notes.map((n) => (
                <Text key={n} style={styles.noteText}>
                  • {n}
                </Text>
              ))}
            </View>
          ) : null}

          <PressableScale style={styles.choice} onPress={() => beginWorkout("ok")}>
            <Text style={styles.choiceIcon}>😀</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.choiceTitle}>Me siento bien</Text>
              <Text style={styles.choiceSub}>Sesión completa, tal como está planeada.</Text>
            </View>
          </PressableScale>
          <PressableScale style={styles.choice} onPress={() => beginWorkout("mild")}>
            <Text style={styles.choiceIcon}>😐</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.choiceTitle}>Molestia leve o cansancio</Text>
              <Text style={styles.choiceSub}>Una serie menos por ejercicio y más descanso. Para si la molestia aumenta.</Text>
            </View>
          </PressableScale>
          <PressableScale style={styles.choice} onPress={() => beginWorkout("pain")}>
            <Text style={styles.choiceIcon}>🤕</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.choiceTitle}>Tengo dolor</Text>
              <Text style={styles.choiceSub}>Solo movilidad suave. Si el dolor persiste, consulta a un profesional de la salud.</Text>
            </View>
          </PressableScale>
          <GradientButton title="Cancelar" variant="ghost" style={{ marginTop: 14 }} onPress={() => router.back()} />
        </Animated.View>
      </ScrollView>
    );
  }

  // ───────────── Cierre: cómo se sintió ─────────────
  if (phase === "feel") {
    return (
      <ScrollView style={styles.bg} contentContainerStyle={[styles.pad, { paddingTop: insets.top + 30 }]}>
        <Animated.View entering={FadeInDown.duration(350)}>
          <Text style={{ fontSize: 56, textAlign: "center" }}>🎉</Text>
          <Text style={[styles.title, bigText, { textAlign: "center" }]}>¡Sesión completada!</Text>
          <Text style={[styles.sub, { textAlign: "center" }]}>¿Qué tal estuvo el esfuerzo? Con esto ajustamos tus próximas rutinas.</Text>
          {(
            [
              { feel: "easy", icon: "😅", title: "Muy fácil", sub: "Puedo con más" },
              { feel: "right", icon: "💪", title: "Justo", sub: "Un buen reto" },
              { feel: "hard", icon: "🥵", title: "Muy difícil", sub: "Me costó demasiado" },
            ] as const
          ).map((o) => (
            <PressableScale key={o.feel} style={styles.choice} disabled={saving} onPress={() => submitFeel(o.feel)}>
              <Text style={styles.choiceIcon}>{o.icon}</Text>
              <View style={{ flex: 1 }}>
                <Text style={styles.choiceTitle}>{o.title}</Text>
                <Text style={styles.choiceSub}>{o.sub}</Text>
              </View>
            </PressableScale>
          ))}
        </Animated.View>
      </ScrollView>
    );
  }

  // ───────────── Resultado ─────────────
  if (phase === "result") {
    return (
      <ScrollView style={styles.bg} contentContainerStyle={[styles.pad, { paddingTop: insets.top + 30 }]}>
        <Animated.View entering={FadeInDown.duration(350)}>
          <Text style={{ fontSize: 56, textAlign: "center" }}>{result ? "🔥" : "🌿"}</Text>
          <Text style={[styles.title, bigText, { textAlign: "center" }]}>
            {result ? `${result.currentStreak} ${result.currentStreak === 1 ? "sesión seguida" : "sesiones seguidas"}` : safety === "pain" ? "Cuidarte también es entrenar" : "Nada pendiente hoy"}
          </Text>
          {result ? (
            <>
              <Text style={[styles.sub, { textAlign: "center" }]}>
                Esta semana: {result.sessionsThisWeek} · Total: {result.totalSessions} · Mejor racha: {result.longestStreak}
              </Text>
              {result.newAchievements.map((a) => (
                <View key={a.key} style={styles.achievement}>
                  <Text style={{ fontSize: 28 }}>{a.icon}</Text>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.choiceTitle}>¡Logro desbloqueado: {a.title}!</Text>
                    <Text style={styles.choiceSub}>{a.description}</Text>
                  </View>
                </View>
              ))}
              {result.adjustment ? (
                <View style={styles.noteBox}>
                  <Text style={styles.noteTitle}>{result.adjustment === "harder" ? "⬆️ Subimos tu nivel" : "⬇️ Bajamos tu nivel"}</Text>
                  <Text style={styles.noteText}>
                    {result.adjustment === "harder"
                      ? "Tus últimas sesiones te parecieron fáciles. Tu próxima rutina será más exigente."
                      : "Tus últimas sesiones te parecieron muy duras. Tu próxima rutina será más suave."}
                  </Text>
                  <GradientButton title="Regenerar con el nuevo nivel" style={{ marginTop: 10 }} onPress={regenerate} />
                </View>
              ) : null}
              <GradientButton
                title="📣 Compartir mi logro"
                variant="ghost"
                style={{ marginTop: 12 }}
                onPress={() =>
                  Share.share({
                    message: `Completé mi sesión de ${routine?.activityName ?? "entrenamiento"} en Sportacus 💪 Llevo ${result.currentStreak} sesiones seguidas y ${result.totalSessions} en total.`,
                  }).catch(() => {})
                }
              />
            </>
          ) : (
            <Text style={[styles.sub, { textAlign: "center" }]}>
              {safety === "pain" ? "Hiciste movilidad suave. Si el dolor persiste, consulta a un profesional de la salud." : "No hay ejercicios pendientes en este día."}
            </Text>
          )}
          <GradientButton title="Volver a mi rutina" style={{ marginTop: 16 }} onPress={() => router.replace("/routine")} />
        </Animated.View>
      </ScrollView>
    );
  }

  // ───────────── Entrenamiento ─────────────
  if (!step) return <ScreenSkeleton />;
  const isSets = step.kind === "sets";

  return (
    <View style={[styles.bg, { paddingTop: insets.top + 8 }]}>
      <View style={styles.topBar}>
        <PressableScale onPress={quit} style={styles.iconBtn}>
          <Text style={styles.iconBtnText}>✕</Text>
        </PressableScale>
        <View style={{ flex: 1 }}>
          <Text style={styles.sectionLabel}>{SECTION_LABEL[section]}</Text>
          <AnimatedBar pct={overallPct} height={6} style={{ marginTop: 4 }} />
        </View>
        <PressableScale onPress={() => setVoice((v) => !v)} style={styles.iconBtn}>
          <Text style={styles.iconBtnText}>{voice ? "🔊" : "🔇"}</Text>
        </PressableScale>
      </View>

      <ScrollView contentContainerStyle={[styles.pad, { paddingBottom: insets.bottom + 24 }]}>
        {isSets && step.image ? <Image source={{ uri: step.image }} style={styles.image} contentFit="contain" cachePolicy="disk" /> : null}

        <Text style={[styles.title, { fontSize: 26 * f }]}>{step.title}</Text>
        {isSets ? (
          <>
            <Text style={[styles.setLine, { fontSize: 18 * f }]}>
              Serie {setIndex + 1} de {step.sets} · {step.reps != null ? `${step.reps} repeticiones` : `${step.seconds} segundos`}
            </Text>
            {step.tips.map((t) => (
              <Text key={t} style={styles.tip}>
                💡 {t}
              </Text>
            ))}
          </>
        ) : (
          <Text style={[styles.setLine, { fontSize: 16 * f }]}>{step.tip}</Text>
        )}

        {mode === "rest" ? (
          <View style={styles.restBox}>
            <Text style={styles.restLabel}>DESCANSO</Text>
            <Text style={[styles.clock, { fontSize: 64 * f }]}>{formatClock(cd.remaining)}</Text>
            <GradientButton title="Saltar descanso" variant="ghost" onPress={() => { cd.stop(); setMode("idle"); }} />
          </View>
        ) : mode === "running" ? (
          <View style={styles.restBox}>
            <Text style={styles.restLabel}>EN CURSO</Text>
            <Text style={[styles.clock, { fontSize: 64 * f }]}>{formatClock(cd.remaining)}</Text>
            <GradientButton title={isSets ? "Terminé la serie" : "Siguiente"} variant="ghost" onPress={() => (isSets ? (cd.stop(), finishSet()) : goNext())} />
          </View>
        ) : step.kind === "timed" ? (
          <GradientButton title={`▶ Iniciar (${step.seconds} s)`} style={styles.mainBtn} onPress={() => startTimed(step.seconds)} />
        ) : step.seconds != null ? (
          <GradientButton title={`▶ Iniciar serie (${step.seconds} s)`} style={styles.mainBtn} onPress={() => startTimed(step.seconds as number)} />
        ) : (
          <GradientButton title="✓ Serie hecha" style={styles.mainBtn} onPress={finishSet} />
        )}

        <GradientButton title="Saltar ›" variant="ghost" style={{ marginTop: 10 }} onPress={goNext} />
      </ScrollView>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  pad: { padding: 18, gap: 8 },
  kicker: { color: colors.primary, fontSize: 12, fontWeight: "700" as const, letterSpacing: 1, marginBottom: 6 },
  title: { color: colors.text, fontSize: 22, fontWeight: "800" as const },
  sub: { color: colors.textMuted, fontSize: 14, marginTop: 6, marginBottom: 14, lineHeight: 20 },
  noteBox: { backgroundColor: colors.warnSoft, borderRadius: 14, padding: 14, gap: 4, marginBottom: 12 },
  noteTitle: { color: colors.warn, fontWeight: "700" as const, fontSize: 14 },
  noteText: { color: colors.text, fontSize: 13, lineHeight: 19 },
  choice: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, backgroundColor: colors.card, borderRadius: 16, padding: 16, marginTop: 10, borderWidth: 1, borderColor: colors.border },
  choiceIcon: { fontSize: 30 },
  choiceTitle: { color: colors.text, fontSize: 15, fontWeight: "700" as const },
  choiceSub: { color: colors.textMuted, fontSize: 12, marginTop: 2, lineHeight: 17 },
  achievement: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, backgroundColor: colors.primarySoft, borderRadius: 14, padding: 14, marginTop: 10 },
  topBar: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, paddingHorizontal: 16 },
  iconBtn: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.card, alignItems: "center" as const, justifyContent: "center" as const },
  iconBtnText: { fontSize: 16, color: colors.text },
  sectionLabel: { color: colors.textMuted, fontSize: 12, fontWeight: "700" as const, letterSpacing: 0.5 },
  image: { width: "100%" as const, height: 200, borderRadius: 16, backgroundColor: colors.card, marginBottom: 8 },
  setLine: { color: colors.primary, fontWeight: "700" as const, marginTop: 4 },
  tip: { color: colors.textMuted, fontSize: 13, lineHeight: 19, marginTop: 4 },
  restBox: { alignItems: "center" as const, gap: 10, marginTop: 18, backgroundColor: colors.card, borderRadius: 18, padding: 18 },
  restLabel: { color: colors.textMuted, fontWeight: "700" as const, letterSpacing: 1, fontSize: 12 },
  clock: { color: colors.primary, fontWeight: "800" as const },
  mainBtn: { marginTop: 20 },
});
