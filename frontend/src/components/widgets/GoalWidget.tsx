import React from "react";
import { Text, View } from "react-native";

import { formatDate } from "../../constants/labels";
import { TypedWidget } from "../../services/types";
import { useTheme } from "../../theme";
import AnimatedBar from "../ui/AnimatedBar";
import WidgetFrame from "./WidgetFrame";

/** Meta del mundo (carrera objetivo, habilidad…) con barra de avance y fecha. */
export default function GoalWidget({ widget, delay }: { widget: TypedWidget<"goal">; delay?: number }) {
  const { colors } = useTheme();
  const d = widget.data;
  const pct = Math.max(0, Math.min(100, d.progressPct));
  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <View style={{ flexDirection: "row", alignItems: "baseline", justifyContent: "space-between", gap: 8 }}>
        <Text style={{ flex: 1, fontSize: 15, fontWeight: "700", color: colors.text }}>🎯 {d.label}</Text>
        <Text style={{ fontSize: 18, fontWeight: "800", color: colors.primary }}>{Math.round(pct)}%</Text>
      </View>
      <AnimatedBar pct={pct} height={10} style={{ marginTop: 10 }} />
      {d.detail ? <Text style={{ fontSize: 12, color: colors.textMuted, marginTop: 8 }}>{d.detail}</Text> : null}
      {d.targetDate ? (
        <Text style={{ fontSize: 12, color: colors.textFaint, marginTop: 4 }}>Fecha objetivo: {formatDate(d.targetDate, true)}</Text>
      ) : null}
    </WidgetFrame>
  );
}
