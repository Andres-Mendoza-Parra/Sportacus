import { Image } from "expo-image";
import { router, useFocusEffect } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, Text, View } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import Animated, { FadeIn, FadeInDown, FadeOut, useAnimatedStyle, useSharedValue, withTiming } from "react-native-reanimated";

import AnimatedBar from "../../components/ui/AnimatedBar";
import CheckBox from "../../components/ui/CheckBox";
import { useConfirm, useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import GradientHeader from "../../components/ui/GradientHeader";
import { useAnimatedNumber } from "../../components/ui/motion";
import PressableScale from "../../components/ui/PressableScale";
import Sheet from "../../components/ui/Sheet";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { extractErrorMessage } from "../../services/api";
import { isServedFromCache } from "../../services/cache";
import {
  Reaction,
  Routine,
  RoutineExercise,
  RoutineOption,
  fetchRoutine,
  fetchRoutineOptions,
  prefetchRoutineDetails,
  regenerateRoutine,
  selectRoutine,
  sendExerciseFeedback,
  setExerciseDone,
  swapExercise,
} from "../../services/routineService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

type Busy = null | "regenerate" | "another" | "select" | "swap";

export default function RoutineScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const confirm = useConfirm();

  const [routine, setRoutine] = useState<Routine | null>(null);
  const [dayIndex, setDayIndex] = useState(0);
  const [busy, setBusy] = useState<Busy>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [optionsOpen, setOptionsOpen] = useState(false);
  const [options, setOptions] = useState<RoutineOption[] | null>(null);
  const [breakdownOpen, setBreakdownOpen] = useState(false);
  const [offline, setOffline] = useState(false);

  const load = useCallback(async () => {
    try {
      const fresh = await fetchRoutine();
      setRoutine(fresh);
      setOffline(isServedFromCache("routine"));
      // Se descargan los detalles en segundo plano para poder entrenar sin conexión.
      if (!isServedFromCache("routine")) prefetchRoutineDetails(fresh);
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setIsRefreshing(false);
    }
  }, [toast]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const affinity = Math.round(useAnimatedNumber(routine?.affinityScore ?? 0));

  if (!routine) return <ScreenSkeleton cards={4} />;

  const day = routine.days[Math.min(dayIndex, Math.max(0, routine.days.length - 1))];

  /** Ejecuta una operación que devuelve una rutina nueva y la muestra con feedback. */
  async function run(kind: Exclude<Busy, null>, action: () => Promise<Routine>, successMessage: string) {
    setBusy(kind);
    try {
      const next = await action();
      setRoutine(next);
      setDayIndex(0);
      setOptions(null);
      toast.success(successMessage);
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  async function openOptions() {
    setOptionsOpen(true);
    if (options) return;
    try {
      setOptions(await fetchRoutineOptions());
    } catch (error) {
      setOptionsOpen(false);
      toast.error(extractErrorMessage(error));
    }
  }

  /** Marca/desmarca un ejercicio del checklist: optimista, con rollback si el servidor falla. */
  async function handleToggleDone(ex: RoutineExercise) {
    const id = ex.exerciseRecommendationId;
    const nextDone = !ex.done;
    setRoutine((prev) => (prev ? withDone(prev, id, nextDone) : prev));
    try {
      const result = await setExerciseDone(id, nextDone);
      setRoutine((prev) =>
        prev
          ? {
              ...withDone(prev, id, result.done),
              doneCount: result.totalCount > 0 ? result.doneCount : countDone(withDone(prev, id, result.done)),
              totalCount: result.totalCount > 0 ? result.totalCount : prev.totalCount,
            }
          : prev,
      );
    } catch (error) {
      setRoutine((prev) => (prev ? withDone(prev, id, ex.done) : prev));
      toast.error(extractErrorMessage(error));
    }
  }

  async function handleSwapRequest(ex: RoutineExercise) {
    if (ex.done) {
      const ok = await confirm({
        title: "¿Cambiar un ejercicio ya hecho?",
        message: `«${ex.name}» está marcado como hecho. Si lo cambias, el nuevo ejercicio empieza sin marcar.`,
        confirmText: "Cambiar",
      });
      if (!ok) return;
    }
    await handleSwap(ex);
  }

  async function handleSwap(ex: RoutineExercise) {
    setBusy("swap");
    try {
      const next = await swapExercise(ex.exerciseRecommendationId);
      setRoutine(next);
      toast.success(`Cambiamos «${ex.name}» por una alternativa similar.`);
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  async function handleReaction(ex: RoutineExercise, reaction: Reaction) {
    const next: Reaction | null = ex.reaction === reaction ? null : reaction;
    // Actualización optimista del icono; si falla se revierte.
    setRoutine((prev) => (prev ? patchExercise(prev, ex.exerciseRecommendationId, { reaction: next }) : prev));
    if (next === null) return;
    try {
      await sendExerciseFeedback(ex.exerciseRecommendationId, next);
      if (next === "dislike") {
        toast.info("Anotado: lo tendremos en cuenta en tus próximas rutinas.", {
          actionLabel: "Cambiar ahora",
          onAction: () => handleSwapRequest(ex),
        });
      } else {
        toast.success("¡Genial! Priorizaremos ejercicios como este.");
      }
    } catch (error) {
      setRoutine((prev) => (prev ? patchExercise(prev, ex.exerciseRecommendationId, { reaction: ex.reaction }) : prev));
      toast.error(extractErrorMessage(error));
    }
  }

  const isBusy = busy !== null;

  return (
    <View style={{ flex: 1 }}>
      <ScrollView
        style={styles.bg}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={isRefreshing}
            onRefresh={() => {
              setIsRefreshing(true);
              load();
            }}
            tintColor={colors.primary}
          />
        }
      >
        <GradientHeader>
          <Animated.View entering={FadeInDown.duration(400)}>
            <Text style={styles.title}>Tu rutina recomendada</Text>
            <Text style={styles.activity}>{routine.activityName}</Text>
            <View style={styles.badges}>
              {routine.durationMin ? <Badge text={`⏱ ${routine.durationMin} min`} /> : null}
              <Badge text={`🔥 ${routine.intensityLabel}`} />
              <Badge text={`🎯 ${routine.goalLabel}`} />
            </View>

            <PressableScale style={styles.strengthChip} onPress={() => router.push("/questionnaires")}>
              <Text style={styles.strengthText}>🎚️ Afinado al {routine.profileStrengthPct}%</Text>
              <Text style={styles.strengthCta}>
                {routine.profileStrengthPct < 100 ? "Completa tu perfil ›" : "Perfil completo ✓"}
              </Text>
            </PressableScale>

            <PressableScale style={styles.startBtn} onPress={() => router.push({ pathname: "/workout", params: { day: String(dayIndex) } })}>
              <Text style={styles.startBtnText}>▶ Entrenar ahora</Text>
              <Text style={styles.startBtnSub}>Sesión guiada con calentamiento, descansos y voz</Text>
            </PressableScale>

            <View style={styles.actionsRow}>
              <HeaderButton label="📚 Biblioteca" disabled={isBusy} onPress={() => router.push("/library")} />
              <HeaderButton label="📅 Plan" disabled={isBusy} onPress={() => router.push("/plan")} />
            </View>

            <View style={styles.actionsRow}>
              <HeaderButton label="🛠 Taller de rutina" disabled={isBusy} onPress={() => router.push("/workshop")} />
            </View>

            <View style={styles.actionsRow}>
              <HeaderButton
                label="🔄 Regenerar"
                loading={busy === "regenerate"}
                disabled={isBusy}
                onPress={() => run("regenerate", () => regenerateRoutine("refresh"), "Rutina regenerada con tus datos más recientes.")}
              />
              <HeaderButton label="✨ Otras opciones" disabled={isBusy} onPress={openOptions} />
            </View>
          </Animated.View>
        </GradientHeader>

        {offline ? <Text style={styles.offlineBanner}>📴 Sin conexión: mostrando tu última rutina guardada. Los cambios no se guardarán hasta reconectar.</Text> : null}

        <ChecklistSummary done={routine.doneCount} total={routine.totalCount} />

        <View style={styles.dayTabs}>
          {routine.days.map((d, idx) => (
            <PressableScale
              key={d.dayLabel + idx}
              style={[styles.dayTab, idx === dayIndex && styles.dayTabActive]}
              onPress={() => setDayIndex(idx)}
              scaleTo={0.94}
            >
              <Text style={[styles.dayTabText, idx === dayIndex && styles.dayTabTextActive]}>{d.dayLabel}</Text>
            </PressableScale>
          ))}
        </View>

        <PressableScale onPress={() => setBreakdownOpen(true)} scaleTo={0.985}>
        <Animated.View entering={FadeInDown.delay(100).duration(400)} style={styles.scoreCard}>
          <View style={styles.scoreCircle}>
            <Text style={styles.scoreCircleText}>{affinity}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.scoreTitle}>Score de afinidad</Text>
            <AnimatedBar pct={routine.affinityScore} height={5} style={{ marginVertical: 6 }} />
            <Text style={styles.scoreSub}>{routine.rationale}</Text>
            {routine.breakdown.length > 0 ? <Text style={styles.scoreMore}>Ver por qué ›</Text> : null}
          </View>
        </Animated.View>
        </PressableScale>

        <View style={styles.exerciseList}>
          {!day || day.exercises.length === 0 ? (
            <Text style={styles.emptyText}>No hay ejercicios asignados este día todavía.</Text>
          ) : (
            day.exercises.map((ex, idx) => (
              <Animated.View key={ex.exerciseRecommendationId} entering={FadeInDown.delay(150 + idx * 70).duration(380)}>
                <ExerciseCard
                  ex={ex}
                  disabled={isBusy}
                  onOpen={() => router.push(`/routine/${ex.exerciseRecommendationId}`)}
                  onToggleDone={() => handleToggleDone(ex)}
                  onReaction={(r) => handleReaction(ex, r)}
                  onSwap={() => handleSwapRequest(ex)}
                />
              </Animated.View>
            ))
          )}
        </View>

        <View style={{ height: 32 }} />
      </ScrollView>

      {busy ? (
        <Animated.View entering={FadeIn.duration(150)} exiting={FadeOut.duration(150)} style={styles.busyOverlay}>
          <View style={styles.busyBox}>
            <ActivityIndicator color={colors.primary} size="large" />
            <Text style={styles.busyText}>
              {busy === "swap" ? "Buscando una alternativa…" : busy === "select" ? "Armando tu nueva ruta…" : "Recalculando tu rutina…"}
            </Text>
          </View>
        </Animated.View>
      ) : null}

      <Sheet visible={breakdownOpen} title="¿Por qué esta rutina?" onClose={() => setBreakdownOpen(false)}>
        <ScrollView contentContainerStyle={{ padding: 16, paddingTop: 4, gap: 10 }}>
          <Text style={styles.scoreSub}>Así se compone tu afinidad con {routine.activityName}. Cada factor suma o resta puntos según tu perfil.</Text>
          {routine.breakdown.length === 0 ? (
            <Text style={styles.scoreSub}>Aún no hay desglose para esta rutina. Regenérala para verlo.</Text>
          ) : (
            routine.breakdown.map((f) => (
              <View key={f.key}>
                <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                  <Text style={styles.factorLabel}>{f.label}</Text>
                  <Text style={[styles.factorPoints, f.points < 0 && { color: colors.danger }]}>
                    {f.points > 0 ? "+" : ""}
                    {f.points}
                  </Text>
                </View>
                <AnimatedBar pct={Math.min(100, Math.abs(f.points) * 3)} height={5} color={f.points < 0 ? colors.danger : undefined} style={{ marginTop: 4 }} />
              </View>
            ))
          )}
          <Text style={styles.scoreSub}>Mientras más completo esté tu perfil, más preciso es el cálculo.</Text>
        </ScrollView>
      </Sheet>

      <Sheet visible={optionsOpen} title="Otras opciones para ti" onClose={() => setOptionsOpen(false)}>
        <ScrollView contentContainerStyle={{ padding: 16, paddingTop: 4, gap: 10 }}>
          {!options ? (
            <ActivityIndicator color={colors.primary} style={{ marginVertical: 24 }} />
          ) : (
            options.map((opt) => (
              <View key={opt.activityId} style={[styles.optionCard, opt.selected && styles.optionCardSelected]}>
                <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
                  <Text style={styles.optionName}>{opt.name}</Text>
                  <View style={styles.optionScore}>
                    <Text style={styles.optionScoreText}>{Math.round(opt.score)}</Text>
                  </View>
                </View>
                <Text style={styles.optionRationale} numberOfLines={3}>
                  {opt.rationale || opt.description}
                </Text>
                <Text style={styles.optionMeta}>
                  {[opt.intensityLabel, opt.durationMin ? `${opt.durationMin} min` : null, opt.environment].filter(Boolean).join(" · ")}
                </Text>
                {opt.selected ? (
                  <Text style={styles.optionCurrent}>✓ Tu ruta actual</Text>
                ) : (
                  <GradientButton
                    title="Elegir esta ruta"
                    style={{ marginTop: 10 }}
                    onPress={() => {
                      setOptionsOpen(false);
                      run("select", () => selectRoutine(opt.activityId), `Ahora entrenas con: ${opt.name}`);
                    }}
                  />
                )}
              </View>
            ))
          )}
          {options ? (
            <GradientButton
              title="🎲 Probar otra ruta distinta"
              variant="ghost"
              onPress={() => {
                setOptionsOpen(false);
                run("another", () => regenerateRoutine("another"), "Te proponemos una ruta diferente.");
              }}
            />
          ) : null}
        </ScrollView>
      </Sheet>
    </View>
  );
}

function patchExercise(routine: Routine, id: string, patch: Partial<RoutineExercise>): Routine {
  return {
    ...routine,
    days: routine.days.map((d) => ({
      ...d,
      exercises: d.exercises.map((e) => (e.exerciseRecommendationId === id ? { ...e, ...patch } : e)),
    })),
  };
}

function withDone(routine: Routine, id: string, done: boolean): Routine {
  const patched = patchExercise(routine, id, { done });
  return { ...patched, doneCount: countDone(patched) };
}

function countDone(routine: Routine): number {
  return routine.days.reduce((n, d) => n + d.exercises.filter((e) => e.done).length, 0);
}

/** Resumen del checklist: "X de Y hechos" con barra animada y estado de celebración al completar todo. */
function ChecklistSummary({ done, total }: { done: number; total: number }) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const complete = total > 0 && done >= total;
  const pct = total > 0 ? Math.round((done / total) * 100) : 0;

  if (total === 0) return null;

  if (complete) {
    return (
      <Animated.View entering={FadeInDown.duration(380)} style={styles.summaryWrap}>
        <LinearGradient colors={colors.buttonGradient} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.celebrate}>
          <Text style={styles.celebrateEmoji}>🎉</Text>
          <View style={{ flex: 1 }}>
            <Text style={styles.celebrateTitle}>¡Rutina completada!</Text>
            <Text style={styles.celebrateSub}>Hiciste los {total} ejercicios. ¡Gran trabajo!</Text>
          </View>
        </LinearGradient>
      </Animated.View>
    );
  }

  return (
    <Animated.View entering={FadeInDown.duration(380)} style={[styles.summaryWrap, styles.summaryCard]}>
      <View style={{ flexDirection: "row", alignItems: "baseline", justifyContent: "space-between" }}>
        <Text style={styles.summaryTitle}>✅ Checklist de hoy</Text>
        <Text style={styles.summaryCount}>
          {done} de {total} hechos
        </Text>
      </View>
      <AnimatedBar pct={pct} height={8} style={{ marginTop: 10 }} />
    </Animated.View>
  );
}

function Badge({ text }: { text: string }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={styles.badge}>
      <Text style={styles.badgeText}>{text}</Text>
    </View>
  );
}

function HeaderButton({
  label,
  onPress,
  disabled,
  loading,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
}) {
  const styles = useThemedStyles(createStyles);
  return (
    <PressableScale style={[styles.headerBtn, disabled && { opacity: 0.6 }]} onPress={onPress} disabled={disabled}>
      {loading ? <ActivityIndicator color="#fff" size="small" /> : <Text style={styles.headerBtnText}>{label}</Text>}
    </PressableScale>
  );
}

function ExerciseCard({
  ex,
  disabled,
  onOpen,
  onToggleDone,
  onReaction,
  onSwap,
}: {
  ex: RoutineExercise;
  disabled: boolean;
  onOpen: () => void;
  onToggleDone: () => void;
  onReaction: (r: Reaction) => void;
  onSwap: () => void;
}) {
  const styles = useThemedStyles(createStyles);
  const doneAnim = useSharedValue(ex.done ? 1 : 0);

  useEffect(() => {
    doneAnim.value = withTiming(ex.done ? 1 : 0, { duration: 320 });
  }, [ex.done, doneAnim]);

  // Hecho: contenido atenuado y velo de acento sobre la tarjeta.
  const contentStyle = useAnimatedStyle(() => ({ opacity: 1 - doneAnim.value * 0.5 }));
  const tintStyle = useAnimatedStyle(() => ({ opacity: doneAnim.value }));

  return (
    <PressableScale style={styles.exCard} onPress={onOpen}>
      <Animated.View pointerEvents="none" style={[styles.exTint, tintStyle]} />
      <View style={styles.exCheckCol}>
        <CheckBox checked={ex.done} onToggle={onToggleDone} size={32} />
      </View>
      <Animated.View style={[styles.exThumb, contentStyle]}>
        {ex.imageUrl ? (
          <Image source={{ uri: ex.imageUrl }} style={styles.exImage} contentFit="cover" transition={250} />
        ) : (
          <Text style={styles.exThumbIcon}>{ex.icon}</Text>
        )}
      </Animated.View>
      <View style={styles.exInfo}>
        <Animated.View style={contentStyle}>
          <Text style={[styles.exName, ex.done && styles.exNameDone]} numberOfLines={2}>
            {ex.name}
          </Text>
          <Text style={styles.exDetail}>{ex.detailLabel}</Text>
          <View style={styles.exTags}>
            <View style={styles.exTag}>
              <Text style={styles.exTagText}>{ex.muscleGroupTag}</Text>
            </View>
            <View style={[styles.exTag, styles.exTagWarn]}>
              <Text style={[styles.exTagText, styles.exTagTextWarn]}>{ex.difficultyTag}</Text>
            </View>
            {ex.done ? (
              <View style={[styles.exTag, styles.exTagDone]}>
                <Text style={[styles.exTagText, styles.exTagTextDone]}>✓ Hecho</Text>
              </View>
            ) : null}
          </View>
        </Animated.View>
        <View style={styles.exActions}>
          <Pressable
            hitSlop={6}
            disabled={disabled}
            onPress={() => onReaction("like")}
            style={[styles.miniBtn, ex.reaction === "like" && styles.miniBtnOn]}
          >
            <Text style={styles.miniBtnText}>👍</Text>
          </Pressable>
          <Pressable
            hitSlop={6}
            disabled={disabled}
            onPress={() => onReaction("dislike")}
            style={[styles.miniBtn, ex.reaction === "dislike" && styles.miniBtnOff]}
          >
            <Text style={styles.miniBtnText}>👎</Text>
          </Pressable>
          <Pressable hitSlop={6} disabled={disabled} onPress={onSwap} style={[styles.miniBtn, styles.swapBtn]}>
            <Text style={styles.swapText}>🔄 Cambiar</Text>
          </Pressable>
        </View>
      </View>
    </PressableScale>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  startBtn: { marginTop: 14, backgroundColor: "#fff", borderRadius: 16, paddingVertical: 13, paddingHorizontal: 16 },
  startBtnText: { color: "#1a1a1a", fontSize: 16, fontWeight: "800" as const },
  startBtnSub: { color: "#555", fontSize: 11, marginTop: 2 },
  offlineBanner: { marginHorizontal: 16, marginTop: 12, color: colors.warn, fontSize: 12, lineHeight: 17 },
  scoreMore: { color: colors.primary, fontSize: 12, fontWeight: "700" as const, marginTop: 6 },
  factorLabel: { color: colors.text, fontSize: 13, fontWeight: "600" as const },
  factorPoints: { color: colors.success, fontSize: 13, fontWeight: "800" as const },
  bg: { flex: 1, backgroundColor: colors.bg },
  title: { color: "rgba(255,255,255,0.8)", fontSize: 13 },
  activity: { color: "#fff", fontSize: 22, fontWeight: "700" as const, marginTop: 2 },
  badges: { flexDirection: "row" as const, gap: 8, marginTop: 10, flexWrap: "wrap" as const },
  badge: { backgroundColor: "rgba(255,255,255,0.18)", borderRadius: 10, paddingHorizontal: 10, paddingVertical: 5 },
  badgeText: { color: "#fff", fontSize: 12 },
  strengthChip: {
    marginTop: 14,
    backgroundColor: "rgba(255,255,255,0.18)",
    borderRadius: 14,
    paddingVertical: 10,
    paddingHorizontal: 14,
    flexDirection: "row" as const,
    justifyContent: "space-between" as const,
    alignItems: "center" as const,
  },
  strengthText: { color: "#fff", fontSize: 13, fontWeight: "700" as const },
  strengthCta: { color: "rgba(255,255,255,0.9)", fontSize: 12 },
  actionsRow: { flexDirection: "row" as const, gap: 10, marginTop: 12 },
  headerBtn: {
    flex: 1,
    backgroundColor: "rgba(255,255,255,0.22)",
    borderRadius: 14,
    paddingVertical: 11,
    alignItems: "center" as const,
    justifyContent: "center" as const,
    minHeight: 42,
  },
  headerBtnText: { color: "#fff", fontSize: 13, fontWeight: "700" as const },
  dayTabs: { flexDirection: "row" as const, gap: 8, padding: 14, paddingHorizontal: 16 },
  dayTab: {
    paddingVertical: 8,
    paddingHorizontal: 18,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.card,
  },
  dayTabActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  dayTabText: { fontSize: 12, fontWeight: "600" as const, color: colors.textMuted },
  dayTabTextActive: { color: "#fff" },
  scoreCard: {
    marginHorizontal: 16,
    backgroundColor: colors.card,
    borderRadius: 18,
    padding: 14,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 14,
    ...shadow,
  },
  scoreCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.primary,
    alignItems: "center" as const,
    justifyContent: "center" as const,
  },
  scoreCircleText: { color: "#fff", fontSize: 19, fontWeight: "700" as const },
  scoreTitle: { fontSize: 13, fontWeight: "700" as const, color: colors.text },
  scoreSub: { fontSize: 12, color: colors.textMuted, lineHeight: 17 },
  exerciseList: { padding: 16, gap: 12 },
  emptyText: { textAlign: "center" as const, color: colors.textFaint, padding: 24 },
  exCard: { backgroundColor: colors.card, borderRadius: 18, flexDirection: "row" as const, overflow: "hidden" as const, ...shadow },
  exTint: { position: "absolute" as const, top: 0, left: 0, right: 0, bottom: 0, backgroundColor: colors.primarySoft },
  exCheckCol: { width: 52, alignItems: "center" as const, justifyContent: "center" as const },
  exNameDone: { textDecorationLine: "line-through" as const, color: colors.textMuted },
  summaryWrap: { marginHorizontal: 16, marginTop: 14 },
  summaryCard: { backgroundColor: colors.card, borderRadius: 18, padding: 14, ...shadow },
  summaryTitle: { fontSize: 13, fontWeight: "700" as const, color: colors.text },
  summaryCount: { fontSize: 13, fontWeight: "700" as const, color: colors.primary },
  celebrate: { borderRadius: 18, padding: 16, flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  celebrateEmoji: { fontSize: 34 },
  celebrateTitle: { color: "#fff", fontSize: 17, fontWeight: "800" as const },
  celebrateSub: { color: "rgba(255,255,255,0.9)", fontSize: 12, marginTop: 2 },
  exThumb: {
    width: 84,
    backgroundColor: colors.primarySoft,
    alignItems: "center" as const,
    justifyContent: "center" as const,
  },
  exImage: { width: "100%" as const, height: "100%" as const },
  exThumbIcon: { fontSize: 36 },
  exInfo: { flex: 1, padding: 12 },
  exName: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  exDetail: { fontSize: 12, color: colors.textMuted, marginTop: 3 },
  exTags: { flexDirection: "row" as const, gap: 5, marginTop: 8, flexWrap: "wrap" as const },
  exTag: { backgroundColor: colors.primarySoft, borderRadius: 6, paddingHorizontal: 8, paddingVertical: 3 },
  exTagText: { fontSize: 11, fontWeight: "600" as const, color: colors.success },
  exTagWarn: { backgroundColor: colors.warnSoft },
  exTagTextWarn: { color: colors.warn },
  exTagDone: { backgroundColor: colors.infoSoft },
  exTagTextDone: { color: colors.info },
  exActions: { flexDirection: "row" as const, gap: 8, marginTop: 10, alignItems: "center" as const },
  miniBtn: {
    paddingVertical: 5,
    paddingHorizontal: 10,
    borderRadius: 10,
    backgroundColor: colors.cardAlt,
    borderWidth: 1,
    borderColor: "transparent",
  },
  miniBtnOn: { backgroundColor: colors.successSoft, borderColor: colors.primary },
  miniBtnOff: { backgroundColor: colors.dangerSoft, borderColor: colors.danger },
  miniBtnText: { fontSize: 14 },
  swapBtn: { marginLeft: "auto" as const },
  swapText: { fontSize: 12, fontWeight: "600" as const, color: colors.textMuted },
  busyOverlay: {
    position: "absolute" as const,
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: colors.overlay,
    alignItems: "center" as const,
    justifyContent: "center" as const,
  },
  busyBox: { backgroundColor: colors.card, borderRadius: 20, padding: 24, alignItems: "center" as const, gap: 12, ...shadow },
  busyText: { color: colors.text, fontSize: 14, fontWeight: "600" as const },
  optionCard: { backgroundColor: colors.card, borderRadius: 16, padding: 14, borderWidth: 1.5, borderColor: "transparent", ...shadow },
  optionCardSelected: { borderColor: colors.primary },
  optionName: { flex: 1, fontSize: 15, fontWeight: "700" as const, color: colors.text },
  optionScore: { backgroundColor: colors.primary, borderRadius: 12, paddingHorizontal: 10, paddingVertical: 4 },
  optionScoreText: { color: "#fff", fontWeight: "700" as const, fontSize: 13 },
  optionRationale: { fontSize: 12, color: colors.textMuted, marginTop: 6, lineHeight: 17 },
  optionMeta: { fontSize: 11, color: colors.textFaint, marginTop: 6 },
  optionCurrent: { color: colors.primary, fontWeight: "700" as const, marginTop: 10, fontSize: 13 },
});
