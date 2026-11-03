import { api } from "./api";
import { cachedRequest } from "./cache";

export interface RadarPoint {
  key: string;
  label: string;
  value: number; // 0-100
}

export interface ProgressItem {
  key: string;
  label: string;
  icon: string;
  color: string;
  progressPct: number; // 0-100
}

export interface CurrentActivity {
  area: string;
  subtitle: string;
  fromRecentSession: boolean;
}

export interface DashboardStats {
  globalScore: number;
  profileCompletionPct: number;
  activeModules: number;
}

export type FocusType = "radar" | "donut" | "bars" | "gauge";

export interface FocusItem {
  key: string;
  label: string;
  value: number; // 0-100
  detail?: string;
  color?: string;
}

/** Gráfico principal; el backend elige el tipo según la meta/categoría del usuario. */
export interface DashboardFocus {
  type: FocusType;
  title: string;
  subtitle: string;
  items: FocusItem[];
}

export interface DashboardToday {
  routineName: string;
  doneCount: number;
  totalCount: number;
  pct: number;
}

export type MetricStatus = "good" | "warn" | "low";

export interface DashboardMetric {
  key: string;
  label: string;
  value: string;
  unit: string;
  status: MetricStatus;
  hint: string;
  pct?: number;
}

export type InsightTone = "good" | "warn" | "info";

export interface DashboardInsight {
  icon: string;
  title: string;
  text: string;
  tone: InsightTone;
  route?: string;
}

export interface DashboardData {
  displayName: string;
  stats: DashboardStats;
  radar: RadarPoint[];
  progress: ProgressItem[];
  currentActivity: CurrentActivity;
  goalKey: string;
  goalLabel: string;
  focus: DashboardFocus | null;
  today: DashboardToday | null;
  metrics: DashboardMetric[];
  insights: DashboardInsight[];
  strengthPct: number;
}

const FOCUS_TYPES: FocusType[] = ["radar", "donut", "bars", "gauge"];

/** Rellena con valores por defecto lo que un backend más antiguo podría no enviar. */
function normalizeDashboard(raw: Partial<DashboardData>): DashboardData {
  const focusRaw = raw.focus;
  const focus: DashboardFocus | null =
    focusRaw && FOCUS_TYPES.includes(focusRaw.type) && (focusRaw.items ?? []).length > 0
      ? {
          type: focusRaw.type,
          title: focusRaw.title ?? "",
          subtitle: focusRaw.subtitle ?? "",
          items: focusRaw.items.map((i) => ({ ...i, value: Number(i.value) || 0 })),
        }
      : null;

  const todayRaw = raw.today;
  const today: DashboardToday | null = todayRaw
    ? {
        routineName: todayRaw.routineName ?? "",
        doneCount: todayRaw.doneCount ?? 0,
        totalCount: todayRaw.totalCount ?? 0,
        pct:
          todayRaw.pct ??
          (todayRaw.totalCount ? Math.round(((todayRaw.doneCount ?? 0) / todayRaw.totalCount) * 100) : 0),
      }
    : null;

  return {
    displayName: raw.displayName ?? "",
    stats: raw.stats ?? { globalScore: 0, profileCompletionPct: 0, activeModules: 0 },
    radar: raw.radar ?? [],
    progress: raw.progress ?? [],
    currentActivity: raw.currentActivity ?? { area: "", subtitle: "", fromRecentSession: false },
    goalKey: raw.goalKey ?? "",
    goalLabel: raw.goalLabel ?? "",
    focus,
    today,
    metrics: raw.metrics ?? [],
    insights: raw.insights ?? [],
    strengthPct: raw.strengthPct ?? raw.stats?.profileCompletionPct ?? 0,
  };
}

export async function fetchDashboard(): Promise<DashboardData> {
  return cachedRequest("dashboard", async () => {
    const { data } = await api.get<DashboardData>("/dashboard");
    return normalizeDashboard(data);
  });
}
