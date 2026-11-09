import React from "react";
import { Text, View } from "react-native";

import { TypedWidget } from "../../services/types";
import { useTheme } from "../../theme";
import WidgetFrame from "./WidgetFrame";

/** Escalera vertical de fases (p. ej. protocolo de Salud: control del dolor → fuerza → funcional). */
export default function PhaseTrackWidget({ widget, delay }: { widget: TypedWidget<"phase_track">; delay?: number }) {
  const { colors } = useTheme();
  const phases = widget.data.phases;
  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      {phases.map((p, i) => {
        const done = p.status === "done";
        const current = p.status === "current";
        const dotColor = done || current ? colors.primary : colors.track;
        return (
          <View key={`${p.name}-${i}`} style={{ flexDirection: "row", gap: 12 }} accessible accessibilityLabel={`Fase ${i + 1}: ${p.name}, ${done ? "completada" : current ? "en curso" : "bloqueada"}`}>
            <View style={{ alignItems: "center", width: 26 }}>
              <View
                style={{
                  width: 26,
                  height: 26,
                  borderRadius: 13,
                  backgroundColor: done ? dotColor : colors.card,
                  borderWidth: 2.5,
                  borderColor: dotColor,
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <Text style={{ fontSize: 11, fontWeight: "800", color: done ? colors.onPrimary : current ? colors.primary : colors.textFaint }}>
                  {done ? "✓" : i + 1}
                </Text>
              </View>
              {i < phases.length - 1 ? <View style={{ width: 2.5, flex: 1, minHeight: 18, backgroundColor: done ? colors.primary : colors.track }} /> : null}
            </View>
            <View style={{ flex: 1, paddingBottom: i < phases.length - 1 ? 14 : 0 }}>
              <Text style={{ fontSize: 14, fontWeight: current ? "800" : "600", color: p.status === "locked" ? colors.textFaint : colors.text }}>
                {p.name}
                {current ? <Text style={{ color: colors.primary, fontSize: 12 }}>  · En curso</Text> : null}
              </Text>
              {p.detail ? <Text style={{ fontSize: 12, color: colors.textMuted, marginTop: 2, lineHeight: 17 }}>{p.detail}</Text> : null}
            </View>
          </View>
        );
      })}
    </WidgetFrame>
  );
}
