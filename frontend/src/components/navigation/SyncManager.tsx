import { useSegments } from "expo-router";
import { useCallback, useEffect, useRef } from "react";
import { AppState } from "react-native";

import { useAuth } from "../../context/AuthContext";
import { useWorld } from "../../context/WorldContext";
import { flushAnalytics, track } from "../../services/analytics";
import { api } from "../../services/api";
import { isOnline, subscribeOnline } from "../../services/connectivity";
import { flushPendingSessions, refreshPendingCount } from "../../services/pendingSessions";
import { useToast } from "../ui/Feedback";

const ANALYTICS_EVERY_MS = 30_000;
const PROBE_EVERY_MS = 20_000;

/**
 * Tareas de fondo sin interfaz:
 * - envía los entrenamientos pendientes (guardados sin conexión o con la sesión vencida) al entrar,
 *   al volver la conexión y al volver la app a primer plano;
 * - envía la analítica por lotes cada 30 s y al pasar a segundo plano;
 * - registra `app_open` y `screen_view` (con el patrón de la ruta, nunca ids ni datos).
 */
export default function SyncManager() {
  const { token, user } = useAuth();
  const { invalidate } = useWorld();
  const toast = useToast();
  const segments = useSegments();
  const userId = user?.userId ?? null;
  const userRef = useRef(userId);
  useEffect(() => {
    userRef.current = userId;
  });

  const syncPending = useCallback(async () => {
    const owner = userRef.current;
    if (!owner) return;
    const outcome = await flushPendingSessions(owner).catch(() => null);
    if (!outcome) return;
    if (outcome.sent.length > 0) {
      invalidate();
      toast.success(
        outcome.sent.length === 1
          ? "Registramos tu entrenamiento pendiente. ¡Nada se perdió!"
          : `Registramos tus ${outcome.sent.length} entrenamientos pendientes. ¡Nada se perdió!`,
      );
    }
    if (outcome.dropped > 0) {
      toast.info("Un entrenamiento guardado ya no se pudo registrar porque pasó demasiado tiempo.");
    }
  }, [invalidate, toast]);

  // Una vez al abrir la app.
  useEffect(() => {
    track("app_open");
    refreshPendingCount().catch(() => {});
  }, []);

  // Al entrar (o restaurar la sesión): pendientes + analítica.
  useEffect(() => {
    if (!token) return;
    syncPending();
    flushAnalytics().catch(() => {});
  }, [token, syncPending]);

  // Analítica cada 30 s.
  useEffect(() => {
    if (!token) return;
    const timer = setInterval(() => {
      flushAnalytics().catch(() => {});
    }, ANALYTICS_EVERY_MS);
    return () => clearInterval(timer);
  }, [token]);

  // Primer plano → reintenta pendientes; segundo plano → envía la analítica.
  useEffect(() => {
    const sub = AppState.addEventListener("change", (state) => {
      if (state === "active") syncPending();
      else if (state === "background" || state === "inactive") flushAnalytics().catch(() => {});
    });
    return () => sub.remove();
  }, [syncPending]);

  // Volvió la conexión → reintenta. Sin conexión → sondea de vez en cuando para detectar el regreso.
  useEffect(() => {
    let probe: ReturnType<typeof setInterval> | null = null;
    const onChange = () => {
      if (isOnline()) {
        if (probe) clearInterval(probe);
        probe = null;
        syncPending();
        flushAnalytics().catch(() => {});
      } else if (!probe) {
        probe = setInterval(() => {
          api.get("/legal/documents", { timeout: 5000 }).catch(() => {});
        }, PROBE_EVERY_MS);
      }
    };
    const unsubscribe = subscribeOnline(onChange);
    return () => {
      unsubscribe();
      if (probe) clearInterval(probe);
    };
  }, [syncPending]);

  // Vistas de pantalla con el patrón de ruta ("(tabs)/home", "session/[id]").
  const screen = segments.join("/") || "index";
  useEffect(() => {
    track("screen_view", { screen });
  }, [screen]);

  return null;
}
