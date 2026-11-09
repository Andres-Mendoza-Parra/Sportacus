import React from "react";
import { Text, View } from "react-native";

import { TypedWidget } from "../../services/types";
import { useTheme } from "../../theme";
import { toneColor } from "../charts/palette";
import WidgetFrame from "./WidgetFrame";

/** Fila de cifras clave (sesiones, series, tonelaje…), coloreadas según su tono. */
export default function StatRowWidget({ widget, delay }: { widget: TypedWidget<"stat_row">; delay?: number }) {
  const { colors } = useTheme();
  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
        {widget.data.stats.map((s, i) => (
          <View
            key={`${s.label}-${i}`}
            accessible
            accessibilityLabel={`${s.label}: ${s.value} ${s.unit ?? ""} ${s.hint ?? ""}`}
            style={{ flexGrow: 1, flexBasis: "28%", backgroundColor: colors.cardAlt, borderRadius: 14, padding: 12 }}
          >
            <Text style={{ fontSize: 11, color: colors.textMuted, fontWeight: "600" }} numberOfLines={1}>
              {s.label}
            </Text>
            <Text style={{ fontSize: 20, fontWeight: "800", color: s.tone ? toneColor(s.tone, colors) : colors.text, marginTop: 2 }} numberOfLines={1}>
              {s.value}
              {s.unit ? <Text style={{ fontSize: 12, fontWeight: "500", color: colors.textMuted }}> {s.unit}</Text> : null}
            </Text>
            {s.hint ? (
              <Text style={{ fontSize: 10, color: colors.textFaint }} numberOfLines={1}>
                {s.hint}
              </Text>
            ) : null}
          </View>
        ))}
      </View>
    </WidgetFrame>
  );
}
