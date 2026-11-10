import { router } from "expo-router";
import { useCallback } from "react";

import { useToast } from "../components/ui/Feedback";
import { todayWeekday } from "../constants/labels";
import { extractErrorMessage } from "../services/api";
import { SessionView } from "../services/types";
import { fetchPlan } from "../services/worldService";

/** Primera sesión pendiente de la semana en curso, priorizando hoy o los días siguientes. */
export function pickNextSession(sessions: SessionView[], currentWeek: number): SessionView | null {
  const pending = sessions
    .filter((s) => !s.done && s.weekIndex === currentWeek)
    .sort((a, b) => (a.weekday ?? 8) - (b.weekday ?? 8) || a.dayIndex - b.dayIndex);
  const today = todayWeekday();
  return pending.find((s) => (s.weekday ?? 8) >= today) ?? pending[0] ?? null;
}

/**
 * Navega a las rutas de acción que propone el backend en los widgets (`actionRoute`).
 * Rutas desconocidas se ignoran: el backend puede adelantarse a la app.
 */
export function useActionRoute(): (route: string) => void {
  const toast = useToast();

  const openNextSession = useCallback(async () => {
    try {
      const plan = await fetchPlan();
      const next = pickNextSession(plan.sessions, plan.currentWeek);
      if (next) router.push({ pathname: "/session/[id]", params: { id: next.id } });
      else {
        toast.info("Semana completa 🎉 Ya hiciste todas las sesiones de esta semana.");
        router.push("/plan");
      }
    } catch (error) {
      toast.error(extractErrorMessage(error));
    }
  }, [toast]);

  return useCallback(
    (route: string) => {
      const [path, query = ""] = route.split("?");
      const parts = path.split("/").filter(Boolean);
      const [head, arg, third] = parts;
      const id = arg ? decodeURIComponent(arg) : undefined;
      const params: Record<string, string> = {};
      for (const pair of query.split("&").filter(Boolean)) {
        const [k, v = ""] = pair.split("=");
        params[decodeURIComponent(k)] = decodeURIComponent(v);
      }
      switch (head) {
        case "profile360":
          if (id) router.push({ pathname: "/profile360/[key]", params: { key: id } });
          else router.push({ pathname: "/profile360", params });
          return;
        case "checkin":
          router.push("/checkin");
          return;
        case "log-activity":
          router.push("/log-activity");
          return;
        case "explore":
          router.push("/onboarding/choose");
          return;
        case "onboarding": {
          const key = third ? decodeURIComponent(third) : undefined;
          if (id === "setup" && key) router.push({ pathname: "/onboarding/setup/[key]", params: { ...params, key } });
          else if (id === "world" && key) router.push({ pathname: "/onboarding/world/[key]", params: { key } });
          else if (id === "gallery") router.push("/onboarding/gallery");
          else if (id === "discovery") router.push("/onboarding/discovery");
          else if (id === "health") router.push({ pathname: "/onboarding/health", params: { mode: "edit" } });
          else router.push("/onboarding/choose");
          return;
        }
        case "session":
          if (id) router.push({ pathname: "/session/[id]", params: { id } });
          else openNextSession();
          return;
        case "assessments":
          if (id) router.push({ pathname: "/assessments/[key]", params: { key: id } });
          else router.push("/assessments");
          return;
        case "pain-check":
          if (id) router.push({ pathname: "/pain-check/[logId]", params: { logId: id } });
          return;
        case "movement":
          if (id) router.push({ pathname: "/movement/[key]", params: { key: id } });
          return;
        case "block":
          if (id) router.push({ pathname: "/block/[id]", params: params.world ? { id, world: params.world } : { id } });
          return;
        case "achievements":
          router.push("/achievements");
          return;
        case "goals":
          router.push("/goals");
          return;
        case "settings":
          router.push("/settings");
          return;
        case "library":
          router.push("/library");
          return;
        case "health":
          router.push("/health");
          return;
        case "plan":
          router.push("/plan");
          return;
        case "progress":
          router.push("/progress");
          return;
        case "chat":
          router.push("/chat");
          return;
        case "home":
          router.navigate("/home");
          return;
        case "account":
          router.navigate("/account");
          return;
        default:
          if (__DEV__) console.warn(`[useActionRoute] Ruta de acción desconocida: ${route}`);
      }
    },
    [openNextSession],
  );
}
