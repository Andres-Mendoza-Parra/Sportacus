import { router, useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { ScrollView, Switch, Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { useToast } from "../components/ui/Feedback";
import GradientButton from "../components/ui/GradientButton";
import GradientHeader from "../components/ui/GradientHeader";
import PressableScale from "../components/ui/PressableScale";
import { ScreenSkeleton } from "../components/ui/Skeleton";
import { extractErrorMessage } from "../services/api";
import {
  CALENDAR_SUPPORTED,
  PlanEntry,
  PlanPrefs,
  WEEKDAY_LABELS,
  applyPlanReminders,
  enablePlanReminders,
  loadPlan,
  savePlan,
  syncToCalendar,
} from "../services/plan";
import { REMINDERS_SUPPORTED } from "../services/reminders";
import { Routine, fetchRoutine } from "../services/routineService";
import { Theme, useThemedStyles, useTheme } from "../theme";

function pad(n: number) {
  return String(n).padStart(2, "0");
}

export default function PlanScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const [routine, setRoutine] = useState<Routine | null>(null);
  const [plan, setPlan] = useState<PlanPrefs | null>(null);
  const [syncing, setSyncing] = useState(false);

  useFocusEffect(
    useCallback(() => {
      (async () => {
        try {
          const r = await fetchRoutine();
          setRoutine(r);
          setPlan(await loadPlan(r));
        } catch (error) {
          toast.error(extractErrorMessage(error));
        }
      })();
    }, [toast]),
  );

  if (!routine || !plan) return <ScreenSkeleton />;

  /** Guarda el plan y, si los recordatorios están activos, los reprograma con los cambios. */
  async function commit(next: PlanPrefs) {
    setPlan(next);
    await savePlan(next).catch(() => {});
    if (next.remindersEnabled && routine) await applyPlanReminders(next, routine).catch(() => {});
  }

  function patchEntry(dayIndex: number, patch: Partial<PlanEntry>) {
    if (!plan) return;
    commit({ ...plan, entries: plan.entries.map((e) => (e.dayIndex === dayIndex ? { ...e, ...patch } : e)) });
  }

  function shiftTime(entry: PlanEntry, deltaMin: number) {
    const total = (entry.hour * 60 + entry.minute + deltaMin + 1440) % 1440;
    patchEntry(entry.dayIndex, { hour: Math.floor(total / 60), minute: total % 60 });
  }

  async function toggleReminders(value: boolean) {
    if (!plan) return;
    if (value && !(await enablePlanReminders())) {
      toast.error(REMINDERS_SUPPORTED ? "Activa los permisos de notificaciones para recibir avisos." : "Los recordatorios no funcionan en web ni en Expo Go; usa una development build.");
      return;
    }
    await commit({ ...plan, remindersEnabled: value });
    if (value) toast.success("Recordatorios del plan activados");
  }

  async function addToCalendar() {
    if (!plan || !routine) return;
    setSyncing(true);
    try {
      const ids = await syncToCalendar(plan, routine);
      await commit({ ...plan, calendarEventIds: ids });
      toast.success(`Añadí ${ids.length} ${ids.length === 1 ? "sesión" : "sesiones"} a tu calendario (12 semanas).`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : extractErrorMessage(error));
    } finally {
      setSyncing(false);
    }
  }

  return (
    <ScrollView style={styles.bg} showsVerticalScrollIndicator={false}>
      <GradientHeader>
        <View style={styles.headTop}>
          <PressableScale style={styles.backBtn} onPress={() => (router.canGoBack() ? router.back() : router.replace("/routine"))}>
            <Text style={styles.backText}>←</Text>
          </PressableScale>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>Mi plan semanal</Text>
            <Text style={styles.subtitle}>Elige cuándo entrenas cada día de tu rutina</Text>
          </View>
        </View>
      </GradientHeader>

      <View style={{ padding: 16, gap: 12 }}>
        {plan.entries.map((entry, i) => {
          const day = routine.days[entry.dayIndex];
          const muscles = [...new Set((day?.exercises ?? []).map((e) => e.muscleGroupTag))].join(" · ");
          return (
            <Animated.View key={entry.dayIndex} entering={FadeInDown.delay(i * 60).duration(350)} style={styles.card}>
              <View style={styles.rowBetween}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.cardTitle}>Sesión {entry.dayIndex + 1} · {day?.dayLabel}</Text>
                  <Text style={styles.muted} numberOfLines={1}>{muscles || "Sin ejercicios"} · {day?.exercises.length ?? 0} ejercicios</Text>
                </View>
                <PressableScale style={styles.goBtn} onPress={() => router.push({ pathname: "/workout", params: { day: String(entry.dayIndex) } })}>
                  <Text style={styles.goText}>▶ Entrenar</Text>
                </PressableScale>
              </View>

              <View style={styles.weekRow}>
                {WEEKDAY_LABELS.map((w) => (
                  <PressableScale key={w.value} style={[styles.dayChip, entry.weekday === w.value && styles.dayChipActive]} onPress={() => patchEntry(entry.dayIndex, { weekday: w.value })} scaleTo={0.9}>
                    <Text style={[styles.dayChipText, entry.weekday === w.value && { color: "#fff" }]}>{w.short}</Text>
                  </PressableScale>
                ))}
              </View>

              <View style={styles.timeRow}>
                <PressableScale style={styles.stepBtn} onPress={() => shiftTime(entry, -15)}>
                  <Text style={styles.stepText}>−</Text>
                </PressableScale>
                <Text style={styles.time}>{pad(entry.hour)}:{pad(entry.minute)}</Text>
                <PressableScale style={styles.stepBtn} onPress={() => shiftTime(entry, 15)}>
                  <Text style={styles.stepText}>+</Text>
                </PressableScale>
                <Text style={styles.muted}>  de 15 en 15 min</Text>
              </View>
            </Animated.View>
          );
        })}

        <View style={styles.card}>
          <View style={styles.rowBetween}>
            <View style={{ flex: 1, paddingRight: 10 }}>
              <Text style={styles.cardTitle}>🔔 Recordatorios del plan</Text>
              <Text style={styles.muted}>
                {REMINDERS_SUPPORTED
                  ? "Te avisamos el día y la hora de cada sesión, con los ejercicios del día. Reemplaza el recordatorio genérico de Ajustes."
                  : "No disponibles en web ni en Expo Go; funcionan en una development build."}
              </Text>
            </View>
            <Switch value={plan.remindersEnabled} onValueChange={toggleReminders} trackColor={{ true: colors.primary }} disabled={!REMINDERS_SUPPORTED} />
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>📅 Calendario del teléfono</Text>
          <Text style={styles.muted}>
            {CALENDAR_SUPPORTED
              ? "Crea eventos semanales (12 semanas) que se sincronizan con Google Calendar u otras cuentas de tu teléfono. Si vuelves a sincronizar, reemplazo los anteriores."
              : "Disponible en una development build para Android o iOS (no en web ni en Expo Go)."}
          </Text>
          <GradientButton title="Añadir mi plan al calendario" onPress={addToCalendar} loading={syncing} disabled={!CALENDAR_SUPPORTED} style={{ marginTop: 10 }} />
        </View>
      </View>
    </ScrollView>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  headTop: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  backBtn: { width: 38, height: 38, borderRadius: 19, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center" as const, justifyContent: "center" as const },
  backText: { color: "#fff", fontSize: 18 },
  title: { color: "#fff", fontSize: 22, fontWeight: "800" as const },
  subtitle: { color: "rgba(255,255,255,0.8)", fontSize: 13 },
  card: { backgroundColor: colors.card, borderRadius: 18, padding: 16, borderWidth: 1, borderColor: colors.border },
  cardTitle: { color: colors.text, fontSize: 15, fontWeight: "700" as const },
  muted: { color: colors.textMuted, fontSize: 12, marginTop: 3, lineHeight: 17 },
  rowBetween: { flexDirection: "row" as const, alignItems: "center" as const, justifyContent: "space-between" as const, gap: 8 },
  goBtn: { backgroundColor: colors.primarySoft, paddingVertical: 8, paddingHorizontal: 12, borderRadius: 12 },
  goText: { color: colors.primary, fontWeight: "700" as const, fontSize: 12 },
  weekRow: { flexDirection: "row" as const, gap: 6, marginTop: 14 },
  dayChip: { flex: 1, paddingVertical: 9, borderRadius: 10, alignItems: "center" as const, backgroundColor: colors.cardAlt },
  dayChipActive: { backgroundColor: colors.primary },
  dayChipText: { color: colors.textMuted, fontWeight: "700" as const, fontSize: 13 },
  timeRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, marginTop: 12 },
  stepBtn: { width: 36, height: 36, borderRadius: 18, backgroundColor: colors.cardAlt, alignItems: "center" as const, justifyContent: "center" as const },
  stepText: { color: colors.text, fontSize: 18, fontWeight: "700" as const },
  time: { color: colors.text, fontSize: 20, fontWeight: "800" as const, minWidth: 64, textAlign: "center" as const },
});
