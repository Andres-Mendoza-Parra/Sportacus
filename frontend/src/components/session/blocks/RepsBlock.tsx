import React, { useState } from "react";
import { Text, View } from "react-native";

import { useTheme } from "../../../theme";
import NumberField from "../NumberField";
import RestPanel from "../RestPanel";
import { BlockRendererProps, SetDoneToggle, patchEntry } from "./shared";

/** Series × repeticiones: una fila por serie (reps, carga, RIR según ui.logFields) y descanso automático. */
export default function RepsBlock({ block, entries, onChange, ui }: BlockRendererProps) {
  const { colors } = useTheme();
  const p = block.prescription;
  const [restingAfter, setRestingAfter] = useState<number | null>(null);
  const showLoad = ui.logFields.includes("load");
  const showRir = ui.logFields.includes("rir");
  const restS = p.restS ?? 0;

  function toggle(index: number) {
    const done = !entries[index].completed;
    onChange(patchEntry(entries, index, { completed: done }));
    if (done && restS > 0 && index < entries.length - 1) setRestingAfter(index);
    else if (!done && restingAfter === index) setRestingAfter(null);
  }

  return (
    <View style={{ gap: 10 }}>
      {entries.map((entry, i) => (
        <React.Fragment key={i}>
          <View
            style={{
              flexDirection: "row",
              alignItems: "flex-end",
              gap: 8,
              padding: 10,
              borderRadius: 14,
              backgroundColor: entry.completed ? colors.primarySoft : colors.card,
              borderWidth: 1,
              borderColor: colors.border,
            }}
          >
            <Text style={{ width: 26, fontSize: 15, fontWeight: "800", color: colors.textMuted, paddingBottom: 12 }}>{i + 1}</Text>
            <NumberField label="Reps" decimal={false} value={entry.reps} onChange={(reps) => onChange(patchEntry(entries, i, { reps }))} />
            {showLoad ? (
              <NumberField
                label="Carga"
                unit="kg"
                hint={p.loadKg != null ? "sugerido" : undefined}
                value={entry.loadKg}
                onChange={(loadKg) => onChange(patchEntry(entries, i, { loadKg }))}
              />
            ) : null}
            {showRir ? (
              <NumberField
                label="RIR"
                decimal={false}
                value={entry.rir}
                onChange={(rir) => onChange(patchEntry(entries, i, { rir }))}
                style={{ flex: 0.7 }}
              />
            ) : null}
            <SetDoneToggle done={!!entry.completed} onToggle={() => toggle(i)} label={`Serie ${i + 1} hecha`} />
          </View>
          {restingAfter === i ? <RestPanel seconds={restS} tips={ui.tips} seed={i} onDone={() => setRestingAfter(null)} /> : null}
        </React.Fragment>
      ))}
      {p.rir != null && showRir ? (
        <Text style={{ fontSize: 12, color: colors.textMuted }}>
          RIR = repeticiones que te quedaban en reserva. Objetivo: {p.rir}.
        </Text>
      ) : null}
    </View>
  );
}
