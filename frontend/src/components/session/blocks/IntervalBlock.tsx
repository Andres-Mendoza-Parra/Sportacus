import React, { useEffect, useRef, useState } from "react";
import { Text, View } from "react-native";

import { formatPace } from "../../../constants/labels";
import { usePausableCountdown } from "../../../hooks/usePausableCountdown";
import { useTheme } from "../../../theme";
import AnimatedBar from "../../ui/AnimatedBar";
import { formatClock } from "../../ui/useCountdown";
import { BigButton, BlockRendererProps } from "./shared";

type IntervalPhase = "idle" | "work" | "recover" | "done";

/** Intervalos automáticos: repeats × (trabajo + recuperación) con fase grande y avisos por voz. */
export default function IntervalBlock({ block, entries, onChange, speak }: BlockRendererProps) {
  const { colors } = useTheme();
  const p = block.prescription;
  const repeats = Math.max(1, p.repeats ?? 1);
  const workS = p.workS ?? 60;
  const recoverS = p.recoverS ?? 0;
  const entry = entries[0] ?? { completed: false };

  const [phase, setPhase] = useState<IntervalPhase>(entry.completed ? "done" : "idle");
  const [round, setRound] = useState(1);
  const [doneSeconds, setDoneSeconds] = useState(0);

  // El fin de una fase arranca la siguiente; se usa una ref porque el temporizador aún no existe aquí.
  const startPhase = useRef<(seconds: number) => void>(() => {});

  function finishAll(total: number) {
    setPhase("done");
    speak("¡Intervalos terminados! Buen trabajo.");
    onChange([{ ...entry, completed: true, durationS: Math.round(total) }]);
  }

  const timer = usePausableCountdown(() => {
    if (phase === "work") {
      const total = doneSeconds + workS;
      setDoneSeconds(total);
      if (recoverS > 0 && round < repeats) {
        setPhase("recover");
        speak("Recupera");
        startPhase.current(recoverS);
      } else if (round < repeats) {
        setRound(round + 1);
        speak(`Ronda ${round + 1}. Trabajo`);
        startPhase.current(workS);
      } else finishAll(total);
    } else if (phase === "recover") {
      setDoneSeconds(doneSeconds + recoverS);
      setRound(round + 1);
      setPhase("work");
      speak(`Ronda ${round + 1}. Trabajo`);
      startPhase.current(workS);
    }
  });

  useEffect(() => {
    startPhase.current = timer.start;
  });

  // Cuenta atrás hablada en los últimos 3 segundos de cada fase.
  useEffect(() => {
    if (timer.running && timer.remaining > 0 && timer.remaining <= 3) speak(String(timer.remaining));
  }, [timer.remaining, timer.running, speak]);

  function start() {
    setRound(1);
    setDoneSeconds(0);
    setPhase("work");
    speak("Ronda 1. Trabajo");
    timer.start(workS);
  }

  function stopEarly() {
    const current = phase === "work" ? workS - timer.remaining : phase === "recover" ? recoverS - timer.remaining : 0;
    timer.reset();
    finishAll(doneSeconds + current);
  }

  const working = phase === "work";
  const phaseColor = working ? colors.danger : phase === "recover" ? colors.success : colors.primary;
  const phaseTotal = working ? workS : recoverS;
  const pct = phase === "work" || phase === "recover" ? ((phaseTotal - timer.remaining) / Math.max(1, phaseTotal)) * 100 : phase === "done" ? 100 : 0;
  const pace = formatPace(p.paceSecPerKm);

  return (
    <View style={{ gap: 12 }}>
      <View style={{ alignItems: "center", padding: 18, borderRadius: 20, backgroundColor: colors.cardAlt, borderWidth: 2, borderColor: phaseColor }}>
        <Text style={{ fontSize: 22, fontWeight: "900", letterSpacing: 2, color: phaseColor }}>
          {phase === "work" ? "TRABAJO" : phase === "recover" ? "RECUPERA" : phase === "done" ? "¡HECHO!" : "LISTO"}
        </Text>
        <Text accessibilityLiveRegion="polite" style={{ fontSize: 64, fontWeight: "800", color: colors.text, fontVariant: ["tabular-nums"] }}>
          {formatClock(phase === "idle" ? workS : phase === "done" ? 0 : timer.remaining)}
        </Text>
        <Text style={{ fontSize: 14, color: colors.textMuted }}>
          Ronda {Math.min(round, repeats)} de {repeats}
          {timer.paused ? " · en pausa" : ""}
        </Text>
        <AnimatedBar pct={pct} color={phaseColor} height={8} style={{ alignSelf: "stretch", marginTop: 10 }} />
      </View>
      {p.intensity || pace ? (
        <Text style={{ textAlign: "center", fontSize: 13, color: colors.text }}>
          {[p.intensity, pace ? `Ritmo ${pace}` : null].filter(Boolean).join(" · ")}
        </Text>
      ) : null}
      <View style={{ flexDirection: "row", gap: 10 }}>
        {phase === "idle" || phase === "done" ? (
          <BigButton label={phase === "done" ? "↺ Repetir" : "▶ Empezar intervalos"} onPress={start} />
        ) : (
          <>
            <BigButton label={timer.paused ? "▶ Reanudar" : "⏸ Pausa"} tone="ghost" onPress={timer.paused ? timer.resume : timer.pause} />
            <BigButton label="⏹ Terminar" tone="danger" onPress={stopEarly} />
          </>
        )}
      </View>
    </View>
  );
}
