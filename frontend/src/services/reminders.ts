import AsyncStorage from "@react-native-async-storage/async-storage";
import Constants, { ExecutionEnvironment } from "expo-constants";
import { Platform } from "react-native";

/**
 * Recordatorios locales de entrenamiento (no requieren servidor).
 * weekdays usa la convención de expo-notifications: 1 = domingo … 7 = sábado.
 * En web las notificaciones programadas no existen: se guardan las preferencias pero no se agenda nada.
 */
export interface ReminderPrefs {
  enabled: boolean;
  weekdays: number[];
  hour: number;
  minute: number;
}

export const DEFAULT_REMINDERS: ReminderPrefs = { enabled: false, weekdays: [2, 4, 6], hour: 18, minute: 0 };
/** Expo Go no incluye el módulo nativo de notificaciones: importarlo allí lanza un error no capturado. */
export const IS_EXPO_GO = Constants.executionEnvironment === ExecutionEnvironment.StoreClient;
export const REMINDERS_SUPPORTED = Platform.OS !== "web" && !IS_EXPO_GO;

const KEY = "sportacus_reminders";
const CHANNEL_ID = "workout-reminders";

export async function loadReminderPrefs(): Promise<ReminderPrefs> {
  try {
    const raw = await AsyncStorage.getItem(KEY);
    return raw ? { ...DEFAULT_REMINDERS, ...JSON.parse(raw) } : DEFAULT_REMINDERS;
  } catch {
    return DEFAULT_REMINDERS;
  }
}

export async function saveReminderPrefs(prefs: ReminderPrefs): Promise<void> {
  await AsyncStorage.setItem(KEY, JSON.stringify(prefs));
}

/** Pide permiso (si hace falta). Devuelve true si se puede notificar. */
export async function ensureNotificationPermission(): Promise<boolean> {
  if (!REMINDERS_SUPPORTED) return false;
  const Notifications = await import("expo-notifications");
  const current = await Notifications.getPermissionsAsync();
  if (current.granted) return true;
  const requested = await Notifications.requestPermissionsAsync();
  return requested.granted;
}

/** Reprograma todos los recordatorios según las preferencias (cancela los anteriores). */
export async function applyReminders(prefs: ReminderPrefs): Promise<void> {
  if (!REMINDERS_SUPPORTED) return;
  const Notifications = await import("expo-notifications");

  await Notifications.cancelAllScheduledNotificationsAsync();
  if (!prefs.enabled || prefs.weekdays.length === 0) return;

  if (Platform.OS === "android") {
    await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
      name: "Recordatorios de entrenamiento",
      importance: Notifications.AndroidImportance.DEFAULT,
    });
  }

  for (const weekday of prefs.weekdays) {
    await Notifications.scheduleNotificationAsync({
      content: {
        title: "¡Hora de entrenar! 💪",
        body: "Tu rutina de hoy te espera en Sportacus.",
      },
      trigger: {
        type: Notifications.SchedulableTriggerInputTypes.WEEKLY,
        weekday,
        hour: prefs.hour,
        minute: prefs.minute,
        channelId: CHANNEL_ID,
      },
    });
  }
}

/** Configura cómo se muestran las notificaciones con la app abierta (se llama una vez al iniciar). */
export async function initNotificationHandler(): Promise<void> {
  if (!REMINDERS_SUPPORTED) return;
  const Notifications = await import("expo-notifications");
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: false,
      shouldSetBadge: false,
    }),
  });
}
