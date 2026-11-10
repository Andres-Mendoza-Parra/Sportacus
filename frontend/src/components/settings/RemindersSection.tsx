import React, { useEffect, useState } from "react";
import { Switch, Text, View } from "react-native";
import Animated, { FadeInDown, LinearTransition } from "react-native-reanimated";

import { extractErrorMessage } from "../../services/api";
import { track } from "../../services/analytics";
import {
  DEFAULT_REMINDERS,
  IS_EXPO_GO,
  REMINDERS_SUPPORTED,
  ReminderPrefs,
  applyReminders,
  ensureNotificationPermission,
  loadReminderPrefs,
  planWeekdaysToReminder,
  saveReminderPrefs,
} from "../../services/reminders";
import { fetchPlan } from "../../services/worldService";
import { Theme, useThemedStyles, useTheme } from "../../theme";
import { useToast } from "../ui/Feedback";
import GradientButton from "../ui/GradientButton";
import PressableScale from "../ui/PressableScale";
import { SettingsSection, createStyles as sectionStyles } from "./SettingsSection";

/** Días en la convención de expo-notifications (1 = domingo). */
const WEEKDAYS = [
  { value: 2, label: "L" },
  { value: 3, label: "M" },
  { value: 4, label: "X" },
  { value: 5, label: "J" },
  { value: 6, label: "V" },
  { value: 7, label: "S" },
  { value: 1, label: "D" },
];

function pad(n: number) {
  return String(n).padStart(2, "0");
}

/** Recordatorios locales: días, hora y atajo para usar los días del plan del mundo activo. */
export default function RemindersSection({ delay }: { delay: number }) {
  const s = useThemedStyles(sectionStyles);
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const [reminders, setReminders] = useState<ReminderPrefs>(DEFAULT_REMINDERS);
  const [syncing, setSyncing] = useState(false);

  useEffect(() => {
    loadReminderPrefs().then(setReminders);
  }, []);

  async function update(next: ReminderPrefs) {
    setReminders(next);
    await saveReminderPrefs(next);
    try {
      await applyReminders(next);
    } catch (error) {
      toast.error(`No se pudieron programar los recordatorios: ${extractErrorMessage(error)}`);
    }
  }

  async function toggle(enabled: boolean) {
    if (!REMINDERS_SUPPORTED) {
      toast.info("Los recordatorios funcionan en la app móvil. Aquí solo guardamos tu preferencia.");
      await update({ ...reminders, enabled });
      return;
    }
    if (enabled && !(await ensureNotificationPermission())) {
      toast.error("Activa los permisos de notificaciones en los ajustes del sistema para recibir recordatorios.");
      return;
    }
    await update({ ...reminders, enabled });
    if (enabled) track("reminder_enabled");
    toast.success(enabled ? "Recordatorios activados 🔔" : "Recordatorios desactivados");
  }

  function toggleWeekday(day: number) {
    const has = reminders.weekdays.includes(day);
    update({ ...reminders, weekdays: has ? reminders.weekdays.filter((d) => d !== day) : [...reminders.weekdays, day] });
  }

  function shiftTime(deltaMinutes: number) {
    const total = (reminders.hour * 60 + reminders.minute + deltaMinutes + 1440) % 1440;
    update({ ...reminders, hour: Math.floor(total / 60), minute: total % 60 });
  }

  async function usePlanDays() {
    setSyncing(true);
    try {
      const plan = await fetchPlan();
      const days = planWeekdaysToReminder(plan.sessions.filter((x) => x.weekIndex === plan.currentWeek).map((x) => x.weekday));
      if (days.length === 0) {
        toast.info("Tu plan no tiene días asignados todavía.");
        return;
      }
      await update({ ...reminders, weekdays: days });
      toast.success("Usamos los días de tu plan");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSyncing(false);
    }
  }

  return (
    <SettingsSection title="Notificaciones" delay={delay}>
      <View style={styles.switchRow}>
        <View style={{ flex: 1 }}>
          <Text style={s.rowTitle}>Recordatorios de entrenamiento</Text>
          <Text style={s.rowSub}>
            {REMINDERS_SUPPORTED
              ? "Te avisamos los días y la hora que elijas."
              : IS_EXPO_GO
                ? "No disponible en Expo Go: requiere un development build (aquí se guarda tu preferencia)."
                : "Disponible en la app móvil (aquí se guarda tu preferencia)."}
          </Text>
        </View>
        <Switch
          value={reminders.enabled}
          onValueChange={toggle}
          trackColor={{ false: colors.track, true: colors.primary }}
          thumbColor="#fff"
          accessibilityLabel="Activar recordatorios"
        />
      </View>

      {reminders.enabled ? (
        <Animated.View entering={FadeInDown.duration(300)} layout={LinearTransition}>
          <Text style={[s.label, { marginTop: 14 }]}>Días</Text>
          <View style={styles.daysRow}>
            {WEEKDAYS.map((d) => {
              const on = reminders.weekdays.includes(d.value);
              return (
                <PressableScale
                  key={d.value}
                  style={[styles.dayChip, on && styles.dayChipActive]}
                  onPress={() => toggleWeekday(d.value)}
                  scaleTo={0.9}
                  accessibilityRole="button"
                  accessibilityState={{ selected: on }}
                >
                  <Text style={[styles.dayChipText, on && { color: "#fff" }]}>{d.label}</Text>
                </PressableScale>
              );
            })}
          </View>
          <GradientButton title="🗓️ Usar los días de mi plan" variant="ghost" onPress={usePlanDays} loading={syncing} style={{ marginTop: 10 }} />

          <Text style={[s.label, { marginTop: 14 }]}>Hora</Text>
          <View style={styles.timeRow}>
            <Step label="Restar una hora" text="−" onPress={() => shiftTime(-60)} />
            <Text style={styles.timeText}>{pad(reminders.hour)}</Text>
            <Step label="Sumar una hora" text="+" onPress={() => shiftTime(60)} />
            <Text style={styles.timeText}>:</Text>
            <Step label="Restar cinco minutos" text="−" onPress={() => shiftTime(-5)} />
            <Text style={styles.timeText}>{pad(reminders.minute)}</Text>
            <Step label="Sumar cinco minutos" text="+" onPress={() => shiftTime(5)} />
          </View>
        </Animated.View>
      ) : null}
    </SettingsSection>
  );
}

function Step({ label, text, onPress }: { label: string; text: string; onPress: () => void }) {
  const styles = useThemedStyles(createStyles);
  return (
    <PressableScale style={styles.stepBtn} onPress={onPress} accessibilityRole="button" accessibilityLabel={label}>
      <Text style={styles.stepBtnText}>{text}</Text>
    </PressableScale>
  );
}

const createStyles = ({ colors }: Theme) => ({
  switchRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  daysRow: { flexDirection: "row" as const, gap: 6, flexWrap: "wrap" as const },
  dayChip: { width: 38, height: 38, borderRadius: 19, backgroundColor: colors.cardAlt, alignItems: "center" as const, justifyContent: "center" as const },
  dayChipActive: { backgroundColor: colors.primary },
  dayChipText: { fontSize: 13, fontWeight: "700" as const, color: colors.textMuted },
  timeRow: { flexDirection: "row" as const, alignItems: "center" as const, justifyContent: "center" as const, gap: 8 },
  stepBtn: { width: 36, height: 36, borderRadius: 18, backgroundColor: colors.primarySoft, alignItems: "center" as const, justifyContent: "center" as const },
  stepBtnText: { fontSize: 20, color: colors.primary, fontWeight: "700" as const },
  timeText: { fontSize: 26, fontWeight: "700" as const, color: colors.text, minWidth: 34, textAlign: "center" as const },
});
