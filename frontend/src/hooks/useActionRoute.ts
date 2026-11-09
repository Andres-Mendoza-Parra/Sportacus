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
      const [path] = route.split("?");
      const parts = path.split("/").filter(Boolean);
      const [head, arg] = parts;
      const id = arg ? decodeURIComponent(arg) : undefined;
      switch (head) {
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
        default:
          if (__DEV__) console.warn(`[useActionRoute] Ruta de acción desconocida: ${route}`);
      }
    },
    [openNextSession],
  );
}
