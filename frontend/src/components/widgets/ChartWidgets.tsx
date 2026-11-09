import React from "react";
import { Text, View } from "react-native";

import { BarItem, TypedWidget } from "../../services/types";
import { useTheme } from "../../theme";
import BarsChart from "../charts/BarsChart";
import GaugeChart from "../charts/GaugeChart";
import LineChart from "../charts/LineChart";
import RadarChart from "../charts/RadarChart";
import WeekBarsChart from "../charts/WeekBarsChart";
import WidgetFrame from "./WidgetFrame";

interface Props<T extends "line" | "week_bars" | "bars" | "radar" | "gauge"> {
  widget: TypedWidget<T>;
  delay?: number;
}

/** Evolución de una métrica (1RM estimado, ritmo, dolor…). */
export function LineWidget({ widget, delay }: Props<"line">) {
  const d = widget.data;
  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <LineChart points={d.points} unit={d.unit} direction={d.direction} target={d.target} />
    </WidgetFrame>
  );
}

/** Barras por semana (tonelaje, km, carga…). */
export function WeekBarsWidget({ widget, delay }: Props<"week_bars">) {
  const d = widget.data;
  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <WeekBarsChart bars={d.bars} unit={d.unit} target={d.target} />
    </WidgetFrame>
  );
}

/** Barras horizontales 0-100 (p. ej. series por músculo respecto al objetivo). */
export function BarsWidget({ widget, delay }: Props<"bars">) {
  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <BarsChart items={widget.data.bars} />
    </WidgetFrame>
  );
}

/** Radar de N ejes 0-100. */
export function RadarWidget({ widget, delay }: Props<"radar">) {
  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <View style={{ alignItems: "center" }}>
        <RadarChart points={widget.data.axes.map((a) => ({ label: a.label, value: a.value }))} />
      </View>
      <AxisLegend axes={widget.data.axes} />
    </WidgetFrame>
  );
}

/** Leyenda compacta en dos columnas con el detalle de cada eje ("4/10 series"). */
function AxisLegend({ axes }: { axes: BarItem[] }) {
  const { colors } = useTheme();
  const withDetail = axes.filter((a) => a.detail);
  if (withDetail.length === 0) return null;
  return (
    <View style={{ flexDirection: "row", flexWrap: "wrap", rowGap: 6, marginTop: 4 }}>
      {withDetail.map((a, i) => (
        <Text key={`${a.label}-${i}`} style={{ width: "50%", fontSize: 12, color: colors.textMuted }}>
          <Text style={{ fontWeight: "700", color: colors.text }}>{a.label}</Text> · {a.detail}
        </Text>
      ))}
    </View>
  );
}

/** Indicador con zonas (ACWR, IMC…). */
export function GaugeWidget({ widget, delay }: Props<"gauge">) {
  const d = widget.data;
  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <View style={{ alignItems: "center" }}>
        <GaugeChart value={d.value} min={d.min} max={d.max} unit={d.unit} zones={d.zones ?? []} caption={d.caption} />
      </View>
    </WidgetFrame>
  );
}
