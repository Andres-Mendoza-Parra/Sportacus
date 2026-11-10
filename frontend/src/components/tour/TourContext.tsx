import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { NativeScrollEvent, NativeSyntheticEvent, ScrollView, StyleProp, View, ViewStyle } from "react-native";

/**
 * Recorrido inicial (coach marks) propio, sin dependencias nativas: las zonas a resaltar se registran
 * con <TourTarget id="…">, el overlay las mide con measureInWindow (funciona en web y nativo) y, si
 * quedan fuera de pantalla, pide a la pantalla que se desplace (useTourScroll).
 */
export type ViewRef = React.ComponentRef<typeof View>;
export type ScrollRef = React.ComponentRef<typeof ScrollView>;

export interface Scroller {
  scrollBy: (dy: number) => void;
}

interface Registry {
  getTarget: (id: string) => ViewRef | null;
  registerTarget: (id: string, view: ViewRef | null) => void;
  getScroller: (screen: string) => Scroller | null;
  registerScroller: (screen: string, scroller: Scroller | null) => void;
}

interface Control {
  running: boolean;
  step: number;
  start: () => void;
  setStep: (step: number) => void;
  stop: () => void;
}

const RegistryContext = createContext<Registry | null>(null);
const ControlContext = createContext<Control | null>(null);

export function TourProvider({ children }: { children: React.ReactNode }) {
  const targets = useRef(new Map<string, ViewRef>());
  const scrollers = useRef(new Map<string, Scroller>());
  const [running, setRunning] = useState(false);
  const [step, setStep] = useState(0);

  const registry = useMemo<Registry>(
    () => ({
      getTarget: (id) => targets.current.get(id) ?? null,
      registerTarget: (id, view) => {
        if (view) targets.current.set(id, view);
        else targets.current.delete(id);
      },
      getScroller: (screen) => scrollers.current.get(screen) ?? null,
      registerScroller: (screen, scroller) => {
        if (scroller) scrollers.current.set(screen, scroller);
        else scrollers.current.delete(screen);
      },
    }),
    [],
  );

  const start = useCallback(() => {
    setStep(0);
    setRunning(true);
  }, []);
  const stop = useCallback(() => setRunning(false), []);

  const control = useMemo<Control>(() => ({ running, step, start, setStep, stop }), [running, step, start, stop]);

  return (
    <RegistryContext.Provider value={registry}>
      <ControlContext.Provider value={control}>{children}</ControlContext.Provider>
    </RegistryContext.Provider>
  );
}

export function useTourRegistry(): Registry | null {
  return useContext(RegistryContext);
}

export function useTour(): Control {
  const ctx = useContext(ControlContext);
  if (!ctx) throw new Error("useTour debe usarse dentro de un <TourProvider>");
  return ctx;
}

/** Envuelve una zona que el recorrido puede resaltar. No cambia el diseño (solo agrega un View). */
export function TourTarget({ id, children, style }: { id: string; children: React.ReactNode; style?: StyleProp<ViewStyle> }) {
  const registry = useTourRegistry();
  const setRef = useCallback(
    (view: ViewRef | null) => {
      registry?.registerTarget(id, view);
    },
    [registry, id],
  );
  return (
    <View ref={setRef} collapsable={false} style={style}>
      {children}
    </View>
  );
}

/**
 * Conecta un ScrollView al recorrido para que pueda desplazarlo hasta la zona resaltada.
 * Devuelve el manejador de `onScroll` (úsese con scrollEventThrottle).
 */
export function useTourScroll(screen: string, scrollRef: React.RefObject<ScrollRef | null>) {
  const registry = useTourRegistry();
  const offset = useRef(0);

  useEffect(() => {
    if (!registry) return;
    registry.registerScroller(screen, {
      scrollBy: (dy) => scrollRef.current?.scrollTo({ y: Math.max(0, offset.current + dy), animated: true }),
    });
    return () => registry.registerScroller(screen, null);
  }, [registry, screen, scrollRef]);

  return useCallback((e: NativeSyntheticEvent<NativeScrollEvent>) => {
    offset.current = e.nativeEvent.contentOffset.y;
  }, []);
}
