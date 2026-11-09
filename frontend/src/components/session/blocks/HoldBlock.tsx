import React, { useState } from "react";
import { Text, View } from "react-native";

import { useTheme } from "../../../theme";
import { formatClock, useCountdown } from "../../ui/useCountdown";
import NumberField from "../NumberField";
import RestPanel from "../RestPanel";
import { BigButton, BigClock, BlockRendererProps, SetDoneToggle, patchEntry } from "./shared";

/** Sostenimientos: cuenta regresiva de holdS por serie; se registra el tiempo logrado. */
export default function HoldBlock({ block, entries, onChange, ui, speak }: BlockRendererProps) {
  const { colors } = useTheme();
  const p = block.prescription;
  const target = p.holdS ?? 30;
  const restS = p.restS ?? 0;
  const [active, setActive] = useState<number | null>(null);
  const [restingAfter, setRestingAfter] = useState<number | null>(null);

  function finishSet(index: number, achieved: number) {
    onChange(patchEntry(entries, index, { holdS: Math.round(achieved), completed: true }));
    setActive(null);
    if (restS > 0 && index < entries.length - 1) setRestingAfter(index);
  }

  const countdown = useCountdown(() => {
    if (active === null) return;
    speak("¡Tiempo!");
    finishSet(active, target);
  });

  function startSet(index: number) {
    setRestingAfter(null);
    setActive(index);
    countdown.start(target);
    speak("Sostén");
  }

  function stopSet() {
    if (active === null) return;
    const achieved = target - countdown.remaining;
    countdown.stop();
    finishSet(active, achieved);
  }

  const next = entries.findIndex((e) => !e.completed);

  return (
    <View style={{ gap: 12 }}>
      {active !== null ? (
        <View style={{ gap: 8 }}>
          <BigClock value={formatClock(countdown.remaining)} caption={`Serie ${active + 1} de ${entries.length} · sostén`} />
          <BigButton label="Detener" tone="danger" onPress={stopSet} />
        </View>
      ) : next >= 0 ? (
        <BigButton label={`▶ Empezar serie ${next + 1} (${target} s)`} onPress={() => startSet(next)} />
      ) : (
        <Text style={{ fontSize: 14, color: colors.success, fontWeight: "700", textAlign: "center" }}>¡Todas las series hechas!</Text>
      )}

      {entries.map((entry, i) => (
        <React.Fragment key={i}>
          <View style={{ flexDirection: "row", alignItems: "flex-end", gap: 8 }}>
            <Text style={{ width: 26, fontSize: 15, fontWeight: "800", color: colors.textMuted, paddingBottom: 12 }}>{i + 1}</Text>
            <NumberField
              label="Logrado"
              unit="s"
              decimal={false}
              value={entry.holdS}
              onChange={(holdS) => onChange(patchEntry(entries, i, { holdS }))}
            />
            <SetDoneToggle
              done={!!entry.completed}
              onToggle={() => onChange(patchEntry(entries, i, { completed: !entry.completed }))}
              label={`Serie ${i + 1} hecha`}
            />
          </View>
          {restingAfter === i ? <RestPanel seconds={restS} tips={ui.tips} seed={i} onDone={() => setRestingAfter(null)} /> : null}
        </React.Fragment>
      ))}
    </View>
  );
}
