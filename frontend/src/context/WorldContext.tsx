import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";

import { clearBlockDetailMemo, setCacheWorld } from "../services/worldService";
import { activateWorld, fetchMyWorlds } from "../services/worldsService";
import { MyWorld } from "../services/types";
import { useTheme } from "../theme";
import { useAuth } from "./AuthContext";

interface WorldContextValue {
  /** Mundos en los que el usuario está inscrito. */
  worlds: MyWorld[];
  /** El mundo activo (exactamente uno si hay inscripciones). */
  active: MyWorld | null;
  isLoading: boolean;
  /** Se incrementa cada vez que cambia el mundo activo o su plan: las pantallas lo usan para recargar. */
  version: number;
  refresh: () => Promise<MyWorld[]>;
  activate: (key: string) => Promise<void>;
  /** Avisa a las pantallas que los datos del mundo cambiaron (sesión registrada, plan regenerado…). */
  invalidate: () => void;
}

const WorldContext = createContext<WorldContextValue | undefined>(undefined);

/**
 * Mundo activo del usuario: carga /me/worlds, expone el activo y avisa su color al tema. El color del mundo
 * solo tiñe la app cuando en Ajustes está elegido "Color de mi mundo" (accent === "mundo", lo resuelve
 * ThemeProvider); se registra siempre para que volver a esa opción aplique el color correcto al instante.
 * Cambiar de mundo (activate) re-especializa todas las pestañas porque `version` cambia.
 */
export function WorldProvider({ children }: { children: React.ReactNode }) {
  const { token } = useAuth();
  const { setWorldColor } = useTheme();
  // La lista se guarda junto al token dueño: al cerrar sesión deja de verse sin borrarla en un efecto.
  const [state, setState] = useState<{ owner: string | null; list: MyWorld[] }>({ owner: null, list: [] });
  const [isLoading, setIsLoading] = useState(false);
  const [version, setVersion] = useState(0);
  const loadedFor = useRef<string | null>(null);
  const tokenRef = useRef(token);
  useEffect(() => {
    tokenRef.current = token;
  });

  const worlds = useMemo(() => (token && state.owner === token ? state.list : []), [token, state]);
  const active = useMemo(() => worlds.find((w) => w.active) ?? worlds[0] ?? null, [worlds]);

  const setWorlds = useCallback((updater: (prev: MyWorld[]) => MyWorld[]) => {
    setState((prev) => ({ owner: tokenRef.current, list: updater(prev.owner === tokenRef.current ? prev.list : []) }));
  }, []);

  const apply = useCallback(
    (list: MyWorld[]) => {
      setWorlds(() => list);
      const current = list.find((w) => w.active) ?? list[0] ?? null;
      setCacheWorld(current?.key ?? null);
      if (current) setWorldColor(current.color);
      setVersion((v) => v + 1);
    },
    [setWorldColor, setWorlds],
  );

  const refresh = useCallback(async () => {
    setIsLoading(true);
    try {
      const list = await fetchMyWorlds();
      apply(list);
      return list;
    } finally {
      setIsLoading(false);
    }
  }, [apply]);

  const activate = useCallback(
    async (key: string) => {
      const world = await activateWorld(key);
      setWorlds((prev) => {
        const next = prev.some((w) => w.key === world.key)
          ? prev.map((w) => (w.key === world.key ? { ...world, active: true } : { ...w, active: false }))
          : [...prev.map((w) => ({ ...w, active: false })), { ...world, active: true }];
        return next;
      });
      setCacheWorld(world.key);
      setWorldColor(world.color);
      setVersion((v) => v + 1);
    },
    [setWorldColor, setWorlds],
  );

  const invalidate = useCallback(() => {
    clearBlockDetailMemo();
    setVersion((v) => v + 1);
  }, []);

  useEffect(() => {
    if (!token) {
      loadedFor.current = null;
      setCacheWorld(null);
      return;
    }
    if (loadedFor.current === token) return;
    loadedFor.current = token;
    refresh().catch(() => {
      // sin conexión: las pantallas muestran su propio estado de error
    });
  }, [token, refresh]);

  const value = useMemo<WorldContextValue>(
    () => ({ worlds, active, isLoading, version, refresh, activate, invalidate }),
    [worlds, active, isLoading, version, refresh, activate, invalidate],
  );

  return <WorldContext.Provider value={value}>{children}</WorldContext.Provider>;
}

export function useWorld(): WorldContextValue {
  const ctx = useContext(WorldContext);
  if (!ctx) throw new Error("useWorld debe usarse dentro de un <WorldProvider>");
  return ctx;
}
