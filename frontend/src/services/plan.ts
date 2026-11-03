import { Platform } from "react-native";

import { loadRawPref, savePref } from "./prefs";
import { IS_EXPO_GO, REMINDERS_SUPPORTED, ensureNotificationPermission, saveReminderPrefs, loadReminderPrefs } from "./reminders";
import { Routine } from "./routineService";

/** Una sesión del plan: qué día de la rutina se entrena, en qué día de la semana y a qué hora. */
export interface PlanEntry {
  dayIndex: number;
  /** 1 = domingo … 7 = sábado (convención de expo-notifications). */
  weekday: number;
  hour: number;
  minute: number;
}

export interface PlanPrefs {
  remindersEnabled: boolean;
  entries: PlanEntry[];
  /** Ids de eventos creados en el calendario del dispositivo (para reemplazarlos al volver a sincronizar). */
  calendarEventIds: string[];
}

const KEY = "weekly_plan";
const CHANNEL_ID = "workout-reminders";
const WEEKDAY_FROM_LABEL: Record<string, number> = { Dom: 1, Lun: 2, Mar: 3, "Mié": 4, Jue: 5, Vie: 6, "Sáb": 7 };

export const WEEKDAY_LABELS: { value: number; short: string; long: string }[] = [
  { value: 2, short: "L", long: "Lunes" },
  { value: 3, short: "M", long: "Martes" },
  { value: 4, short: "X", long: "Miércoles" },
  { value: 5, short: "J", long: "Jueves" },
  { value: 6, short: "V", long: "Viernes" },
  { value: 7, short: "S", long: "Sábado" },
  { value: 1, short: "D", long: "Domingo" },
];

export const CALENDAR_SUPPORTED = Platform.OS !== "web" && !IS_EXPO_GO;

/** Plan guardado, completado con valores por defecto para los días de la rutina actual. */
export async function loadPlan(routine: Routine): Promise<PlanPrefs> {
  const stored = await loadRawPref<PlanPrefs>(KEY, { remindersEnabled: false, entries: [], calendarEventIds: [] });
  const entries = routine.days.map((day, dayIndex) => {
    const existing = stored.entries.find((e) => e.dayIndex === dayIndex);
    return existing ?? { dayIndex, weekday: WEEKDAY_FROM_LABEL[day.dayLabel] ?? 2, hour: 18, minute: 0 };
  });
  return { ...stored, entries };
}

export async function savePlan(plan: PlanPrefs): Promise<void> {
  await savePref(KEY, plan);
}

function summary(routine: Routine, entry: PlanEntry): string {
  const day = routine.days[entry.dayIndex];
  const names = (day?.exercises ?? []).slice(0, 3).map((e) => e.name).join(", ");
  const minutes = routine.durationMin ? `~${routine.durationMin} min` : "";
  return [names, minutes].filter(Boolean).join(" · ");
}

/** Reprograma los recordatorios con el contenido real de cada día del plan. */
export async function applyPlanReminders(plan: PlanPrefs, routine: Routine): Promise<void> {
  if (!REMINDERS_SUPPORTED) return;
  const Notifications = await import("expo-notifications");
  await Notifications.cancelAllScheduledNotificationsAsync();
  if (!plan.remindersEnabled) return;

  if (Platform.OS === "android") {
    await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
      name: "Recordatorios de entrenamiento",
      importance: Notifications.AndroidImportance.DEFAULT,
    });
  }
  for (const entry of plan.entries) {
    await Notifications.scheduleNotificationAsync({
      content: {
        title: `Hoy te toca ${routine.days[entry.dayIndex]?.dayLabel ?? "entrenar"} 💪`,
        body: summary(routine, entry) || "Tu rutina de hoy te espera en Sportacus.",
      },
      trigger: {
        type: Notifications.SchedulableTriggerInputTypes.WEEKLY,
        weekday: entry.weekday,
        hour: entry.hour,
        minute: entry.minute,
        channelId: CHANNEL_ID,
      },
    });
  }
  // El plan reemplaza al recordatorio genérico de Ajustes: se refleja allí para no mostrar dos configuraciones.
  const generic = await loadReminderPrefs();
  if (generic.enabled) await saveReminderPrefs({ ...generic, enabled: false });
}

export async function enablePlanReminders(): Promise<boolean> {
  return ensureNotificationPermission();
}

function nextOccurrence(entry: PlanEntry): Date {
  const now = new Date();
  const start = new Date(now);
  const diff = (entry.weekday - 1 - now.getDay() + 7) % 7;
  start.setDate(now.getDate() + diff);
  start.setHours(entry.hour, entry.minute, 0, 0);
  if (start.getTime() <= now.getTime()) start.setDate(start.getDate() + 7);
  return start;
}

/**
 * Crea un evento semanal recurrente por sesión en el calendario del dispositivo
 * (que se sincroniza con Google Calendar u otras cuentas del teléfono).
 * Devuelve los ids creados; lanza un Error con mensaje legible si no se puede.
 */
export async function syncToCalendar(plan: PlanPrefs, routine: Routine): Promise<string[]> {
  if (!CALENDAR_SUPPORTED) {
    throw new Error(
      Platform.OS === "web"
        ? "El calendario no está disponible en la versión web."
        : "El calendario requiere una development build (no funciona en Expo Go).",
    );
  }
  const Calendar = await import("expo-calendar");
  const { granted } = await Calendar.requestCalendarPermissions();
  if (!granted) throw new Error("Necesito permiso para acceder a tu calendario.");

  const calendars = await Calendar.getCalendars(Calendar.EntityTypes.EVENT);
  const writable = calendars.find((c) => c.allowsModifications && c.isPrimary) ?? calendars.find((c) => c.allowsModifications);
  if (!writable) throw new Error("No encontré un calendario donde se pueda escribir.");

  // Reemplaza los eventos de una sincronización anterior para no duplicar.
  if (plan.calendarEventIds.length > 0) {
    try {
      const legacy = await import("expo-calendar/legacy");
      for (const id of plan.calendarEventIds) {
        await legacy.deleteEventAsync(id, { futureEvents: true }).catch(() => {});
      }
    } catch {
      // si no se pudo limpiar, se crean los nuevos igualmente
    }
  }

  const minutes = routine.durationMin ?? 45;
  const ids: string[] = [];
  for (const entry of plan.entries) {
    const start = nextOccurrence(entry);
    const event = await writable.createEvent({
      title: `Entrenamiento: ${routine.activityName}`,
      notes: summary(routine, entry),
      startDate: start,
      endDate: new Date(start.getTime() + minutes * 60_000),
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      alarms: [{ relativeOffset: -30 }],
      recurrenceRule: { frequency: Calendar.Frequency.WEEKLY, interval: 1, occurrence: 12 },
    });
    ids.push(event.id);
  }
  return ids;
}
