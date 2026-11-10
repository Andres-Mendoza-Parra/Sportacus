import { router, usePathname } from "expo-router";
import React, { useEffect, useRef, useState } from "react";
import { BackHandler, Text, View, useWindowDimensions } from "react-native";
import Animated, { FadeIn, FadeInDown } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { track } from "../../services/analytics";
import { savePref } from "../../services/prefs";
import { completeTutorial } from "../../services/profileService";
import { Theme, useThemedStyles, useTheme } from "../../theme";
import GradientButton from "../ui/GradientButton";
import { TOUR_STEPS } from "./steps";
import { ViewRef, useTour, useTourRegistry } from "./TourContext";

export const TOUR_DONE_PREF = "tour_done";

interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

const PAD = 6;
const CARD_EST = 230;

const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function measure(view: ViewRef): Promise<Rect | null> {
  return new Promise((resolve) => {
    try {
      view.measureInWindow((x: number, y: number, width: number, height: number) => resolve(width > 0 && height > 0 ? { x, y, width, height } : null));
    } catch {
      resolve(null);
    }
  });
}

/** Marca el recorrido como visto (en el servidor y, por si no hay conexión, en el dispositivo). */
export async function markTourDone(): Promise<void> {
  await savePref(TOUR_DONE_PREF, true).catch(() => {});
  await completeTutorial().catch(() => {});
}

/**
 * Capa del recorrido: oscurece la pantalla dejando un "hueco" sobre la zona del paso y muestra una
 * tarjeta con el texto, el progreso y los botones «Saltar» y «Siguiente». Navega entre pestañas.
 */
export default function TourOverlay() {
  const { running } = useTour();
  if (!running) return null;
  return <TourLayer />;
}

function TourLayer() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const { width: winW, height: winH } = useWindowDimensions();
  const pathname = usePathname();
  const { step, setStep, stop } = useTour();
  const registry = useTourRegistry();
  const rootRef = useRef<ViewRef>(null);
  const [rect, setRect] = useState<Rect | null>(null);
  const [ready, setReady] = useState(false);
  const pathRef = useRef(pathname);
  useEffect(() => {
    pathRef.current = pathname;
  });

  const current = TOUR_STEPS[step];
  const last = step === TOUR_STEPS.length - 1;

  // Cada paso: ir a su pestaña, esperar a que la zona exista, desplazarla a la vista y medirla.
  useEffect(() => {
    if (!current || !registry) return;
    let cancelled = false;
    track("tutorial_step", { step: step + 1, key: current.key });
    (async () => {
      setReady(false);
      const moved = pathRef.current !== current.route;
      if (moved) router.navigate(current.route);
      await wait(moved ? 450 : 120);
      const origin = rootRef.current ? await measure(rootRef.current) : null;
      const ox = origin?.x ?? 0;
      const oy = origin?.y ?? 0;
      let found: { view: ViewRef; rect: Rect } | null = null;
      for (let attempt = 0; attempt < 12 && !found && !cancelled; attempt++) {
        for (const id of current.targets) {
          const view = registry.getTarget(id);
          const r = view ? await measure(view) : null;
          if (view && r) {
            found = { view, rect: r };
            break;
          }
        }
        if (!found) await wait(150);
      }
      if (found) {
        const r = found.rect;
        const tooLow = r.y + Math.min(r.height, winH * 0.45) > winH - insets.bottom - 90;
        const tooHigh = r.y < insets.top;
        const scroller = registry.getScroller(current.screen);
        if ((tooLow || tooHigh) && scroller) {
          scroller.scrollBy(r.y - (insets.top + 96));
          await wait(450);
          found = { view: found.view, rect: (await measure(found.view)) ?? r };
        }
      }
      if (cancelled) return;
      setRect(found ? { ...found.rect, x: found.rect.x - ox, y: found.rect.y - oy } : null);
      setReady(true);
    })();
    return () => {
      cancelled = true;
    };
  }, [current, step, registry, winH, insets.top, insets.bottom]);

  async function finish(completed: boolean) {
    track(completed ? "tutorial_completed" : "tutorial_skipped", { step: step + 1 });
    stop();
    router.navigate("/home");
    await markTourDone();
  }

  // Botón atrás de Android: retrocede un paso (o cierra el recorrido en el primero).
  useEffect(() => {
    const sub = BackHandler.addEventListener("hardwareBackPress", () => {
      if (step > 0) setStep(step - 1);
      else finish(false);
      return true;
    });
    return () => sub.remove();
  });

  if (!current) return null;

  const hole = rect
    ? {
        x: Math.max(0, rect.x - PAD),
        y: Math.max(0, rect.y - PAD),
        w: Math.min(winW, rect.width + PAD * 2),
        h: Math.min(winH, rect.height + PAD * 2),
      }
    : null;
  const cardWidth = Math.min(420, winW - 32);
  const below = hole ? hole.y + hole.h + 12 + CARD_EST < winH - insets.bottom - 8 : false;
  const cardPos = hole
    ? below
      ? { top: hole.y + hole.h + 12 }
      : { bottom: Math.max(insets.bottom + 12, winH - hole.y + 12) }
    : { top: Math.max(insets.top + 40, winH / 2 - CARD_EST / 2) };
  const dim = colors.overlay === "rgba(0,0,0,0.5)" ? "rgba(0,0,0,0.62)" : colors.overlay;

  return (
    <View ref={rootRef} collapsable={false} style={styles.root} accessibilityViewIsModal>
      {hole && ready ? (
        <>
          <View style={[styles.dim, { backgroundColor: dim, top: 0, left: 0, right: 0, height: hole.y }]} />
          <View style={[styles.dim, { backgroundColor: dim, top: hole.y + hole.h, left: 0, right: 0, bottom: 0 }]} />
          <View style={[styles.dim, { backgroundColor: dim, top: hole.y, left: 0, width: hole.x, height: hole.h }]} />
          <View style={[styles.dim, { backgroundColor: dim, top: hole.y, left: hole.x + hole.w, right: 0, height: hole.h }]} />
          <Animated.View
            key={`hole-${step}`}
            entering={FadeIn.duration(220)}
            pointerEvents="none"
            style={[styles.ring, { top: hole.y, left: hole.x, width: hole.w, height: hole.h, borderColor: colors.onPrimary }]}
          />
        </>
      ) : (
        <View style={[styles.dim, { backgroundColor: dim, top: 0, left: 0, right: 0, bottom: 0 }]} />
      )}

      {ready ? (
        <Animated.View
          key={`card-${step}`}
          entering={FadeInDown.duration(260)}
          style={[styles.card, { width: cardWidth, left: (winW - cardWidth) / 2 }, cardPos]}
          accessibilityLiveRegion="polite"
        >
          <View style={styles.headRow}>
            <Text style={{ fontSize: 26 }}>{current.icon}</Text>
            <Text style={styles.title} accessibilityRole="header">
              {current.title}
            </Text>
          </View>
          <Text style={styles.body}>{current.body}</Text>
          <View style={styles.progressRow} accessibilityLabel={`Paso ${step + 1} de ${TOUR_STEPS.length}`}>
            {TOUR_STEPS.map((s, i) => (
              <View key={s.key} style={[styles.dot, i === step && styles.dotActive, i < step && styles.dotDone]} />
            ))}
            <Text style={styles.progressText}>{`${step + 1} de ${TOUR_STEPS.length}`}</Text>
          </View>
          <View style={styles.buttons}>
            {last ? null : <GradientButton title="Saltar" variant="ghost" onPress={() => finish(false)} style={{ flex: 1 }} />}
            {step > 0 ? <GradientButton title="Atrás" variant="ghost" onPress={() => setStep(step - 1)} style={{ flex: 1 }} /> : null}
            <GradientButton
              title={last ? "¡Empezar!" : "Siguiente"}
              onPress={() => (last ? finish(true) : setStep(step + 1))}
              style={{ flex: 1.4 }}
            />
          </View>
        </Animated.View>
      ) : null}
    </View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  root: { position: "absolute" as const, top: 0, left: 0, right: 0, bottom: 0, zIndex: 1000, elevation: 1000 },
  dim: { position: "absolute" as const },
  ring: { position: "absolute" as const, borderWidth: 3, borderRadius: 18 },
  card: { position: "absolute" as const, backgroundColor: colors.card, borderRadius: 20, padding: 18, gap: 10, ...shadow },
  headRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 10 },
  title: { flex: 1, fontSize: 17, fontWeight: "800" as const, color: colors.text },
  body: { fontSize: 14, color: colors.text, lineHeight: 20 },
  progressRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 5, marginTop: 2 },
  dot: { width: 7, height: 7, borderRadius: 4, backgroundColor: colors.track },
  dotActive: { width: 18, backgroundColor: colors.primary },
  dotDone: { backgroundColor: colors.primary, opacity: 0.5 },
  progressText: { marginLeft: 6, fontSize: 12, color: colors.textMuted },
  buttons: { flexDirection: "row" as const, gap: 8, marginTop: 4 },
});
