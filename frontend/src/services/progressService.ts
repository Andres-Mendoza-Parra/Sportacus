import { api } from "./api";
import { cachedRequest } from "./cache";
import { fetchDashboard } from "./dashboardService";
import { fetchProfile } from "./profileService";

export interface AchievementItem {
  key: string;
  title: string;
  description: string;
  icon: string;
  achieved: boolean;
  achievedAt: string | null;
  current: number;
  target: number;
}

export interface ChallengeItem {
  key: string;
  title: string;
  description: string;
  current: number;
  target: number;
  completed: boolean;
}

export interface WeekPoint {
  label: string;
  sessions: number;
}

export interface SessionItem {
  date: string;
  type: string | null;
  durationMin: number | null;
  rpe: number | null;
}

export interface MetricPoint {
  date: string;
  value: number;
}

export interface MetricSeries {
  key: string;
  unit: string | null;
  points: MetricPoint[];
}

export interface ProgressSummary {
  currentStreak: number;
  longestStreak: number;
  totalSessions: number;
  sessionsThisWeek: number;
  streakWeeks: number;
  avgRpe: number | null;
  weeks: WeekPoint[];
  recent: SessionItem[];
  series: MetricSeries[];
  achievements: AchievementItem[];
  challenges: ChallengeItem[];
  lastTestDate: string | null;
  testsDue: boolean;
}

export type Feel = "easy" | "right" | "hard";

export interface SessionInput {
  durationMin?: number;
  feel?: Feel;
  completedCount?: number;
  totalCount?: number;
  activityName?: string;
}

export interface SessionResult {
  currentStreak: number;
  longestStreak: number;
  totalSessions: number;
  sessionsThisWeek: number;
  newAchievements: AchievementItem[];
  /** "harder" | "easier" cuando el esfuerzo reportado cambia el nivel de la próxima rutina. */
  adjustment: "harder" | "easier" | null;
}

export async function fetchProgress(): Promise<ProgressSummary> {
  return cachedRequest("progress", async () => {
    const { data } = await api.get<ProgressSummary>("/progress");
    return data;
  });
}

export async function closeSession(input: SessionInput): Promise<SessionResult> {
  const { data } = await api.post<SessionResult>("/progress/sessions", input);
  return data;
}

export async function saveMetric(metricKey: string, value: number, unit?: string, context?: string): Promise<void> {
  await api.post("/progress/metrics", { metricKey, value, unit, context });
}

/** Foto del estado actual (peso, score, perfil) para ver la evolución; se llama al actualizar el perfil. */
export async function recordSnapshot(): Promise<void> {
  const [profile, dashboard] = await Promise.allSettled([fetchProfile(), fetchDashboard()]);
  const jobs: Promise<void>[] = [];
  if (profile.status === "fulfilled" && profile.value.weightKg != null) {
    jobs.push(saveMetric("peso_kg", profile.value.weightKg, "kg"));
  }
  if (dashboard.status === "fulfilled") {
    jobs.push(saveMetric("global_score", dashboard.value.stats.globalScore, "pts"));
    jobs.push(saveMetric("profile_strength", dashboard.value.strengthPct, "%"));
  }
  await Promise.allSettled(jobs);
}
