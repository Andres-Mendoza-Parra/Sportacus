import React from "react";
import { Text, View } from "react-native";

import { formatNumber } from "../../constants/labels";
import { TrafficLevel, TypedWidget } from "../../services/types";
import { Colors, useTheme, withAlpha } from "../../theme";
import WidgetFrame from "./WidgetFrame";

const LABELS: Record<TrafficLevel, string> = { verde: "Verde", amarillo: "Amarillo", rojo: "Rojo", sin_datos: "Sin datos" };

function levelColor(level: string, colors: Colors): string {
  if (level === "verde") return colors.success;
  if (level === "amarillo") return colors.warn;
  if (level === "rojo") return colors.danger;
  return colors.textFaint;
}

/** Semáforo grande (p. ej. dolor en Salud) con su valor y mensaje. */
export default function TrafficLightWidget({ widget, delay }: { widget: TypedWidget<"traffic_light">; delay?: number }) {
  const { colors } = useTheme();
  const d = widget.data;
  const color = levelColor(d.level, colors);
  const order: TrafficLevel[] = ["rojo", "amarillo", "verde"];

  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 16 }}>
        <View
          accessible
          accessibilityLabel={`Semáforo en ${LABELS[d.level] ?? d.level}`}
          style={{ backgroundColor: colors.cardAlt, borderRadius: 20, padding: 8, gap: 6 }}
        >
          {order.map((l) => {
            const on = l === d.level;
            const c = levelColor(l, colors);
            return (
              <View
                key={l}
                style={{
                  width: 30,
                  height: 30,
                  borderRadius: 15,
                  backgroundColor: on ? c : withAlpha(c, 0.18),
                  boxShadow: on ? `0px 0px 12px ${withAlpha(c, 0.7)}` : undefined,
                }}
              />
            );
          })}
        </View>
        <View style={{ flex: 1, gap: 4 }}>
          <Text style={{ fontSize: 13, fontWeight: "800", color, textTransform: "uppercase" }}>{LABELS[d.level] ?? d.level}</Text>
          {d.value != null ? (
            <Text style={{ fontSize: 28, fontWeight: "800", color: colors.text }}>
              {formatNumber(d.value)}
              {d.unit ? <Text style={{ fontSize: 13, color: colors.textMuted, fontWeight: "500" }}> {d.unit}</Text> : null}
            </Text>
          ) : null}
          <Text style={{ fontSize: 13, color: colors.textMuted, lineHeight: 19 }}>{d.message}</Text>
        </View>
      </View>
    </WidgetFrame>
  );
}
