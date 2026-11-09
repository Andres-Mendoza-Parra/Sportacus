import React, { useState } from "react";
import { LayoutChangeEvent, Text, View } from "react-native";
import Svg, { Circle, Line, Polyline, Text as SvgText } from "react-native-svg";

import { formatDate, formatNumber } from "../../constants/labels";
import { LinePoint } from "../../services/types";
import { useTheme } from "../../theme";

interface Props {
  points: LinePoint[];
  unit?: string | null;
  /** "up" = más es mejor · "down" = menos es mejor. */
  direction?: "up" | "down" | null;
  target?: number | null;
  height?: number;
  color?: string;
}

const PAD = { top: 16, right: 12, bottom: 22, left: 40 };

/** Línea de evolución con puntos, fechas, mínimo/máximo y línea objetivo opcional. */
export default function LineChart({ points, unit, direction, target, height = 170, color }: Props) {
  const { colors } = useTheme();
  const [width, setWidth] = useState(0);
  const stroke = color ?? colors.primary;

  function onLayout(e: LayoutChangeEvent) {
    setWidth(Math.round(e.nativeEvent.layout.width));
  }

  if (points.length === 0) {
    return (
      <View style={{ paddingVertical: 24, alignItems: "center" }}>
        <Text style={{ fontSize: 13, color: colors.textMuted, textAlign: "center" }}>Aún no hay registros. Aparecerán aquí tras tus próximas sesiones.</Text>
      </View>
    );
  }

  const values = points.map((p) => p.value);
  if (target != null) values.push(target);
  let min = Math.min(...values);
  let max = Math.max(...values);
  if (max === min) {
    const pad = Math.abs(max) * 0.1 || 1;
    min -= pad;
    max += pad;
  }
  const innerW = Math.max(1, width - PAD.left - PAD.right);
  const innerH = height - PAD.top - PAD.bottom;
  const x = (i: number) => PAD.left + (points.length > 1 ? (i / (points.length - 1)) * innerW : innerW / 2);
  const y = (v: number) => PAD.top + (1 - (v - min) / (max - min)) * innerH;
  const coords = points.map((p, i) => ({ x: x(i), y: y(p.value) }));
  const last = points[points.length - 1];
  const first = points[0];
  const dateIdx = points.length > 2 ? [0, Math.floor((points.length - 1) / 2), points.length - 1] : points.map((_, i) => i);
  const delta = points.length > 1 ? last.value - first.value : null;
  const good = delta == null || delta === 0 ? null : direction === "down" ? delta < 0 : delta > 0;
  const u = unit ? ` ${unit}` : "";

  return (
    <View onLayout={onLayout}>
      <View style={{ flexDirection: "row", alignItems: "baseline", gap: 8, marginBottom: 6 }}>
        <Text style={{ fontSize: 26, fontWeight: "800", color: colors.text }}>
          {formatNumber(last.value)}
          <Text style={{ fontSize: 13, fontWeight: "500", color: colors.textMuted }}>{u}</Text>
        </Text>
        {delta != null && delta !== 0 ? (
          <Text style={{ fontSize: 12, fontWeight: "700", color: good ? colors.success : colors.warn }}>
            {delta > 0 ? "+" : ""}
            {formatNumber(delta)}
            {u} desde {formatDate(first.date)}
          </Text>
        ) : null}
      </View>
      {width > 0 ? (
        <Svg width={width} height={height} accessibilityLabel={`Gráfico de ${points.length} registros, último ${formatNumber(last.value)}${u}`}>
          <SvgText x={PAD.left - 6} y={PAD.top + 4} fontSize="10" fill={colors.textFaint} textAnchor="end">
            {formatNumber(max)}
          </SvgText>
          <SvgText x={PAD.left - 6} y={PAD.top + innerH} fontSize="10" fill={colors.textFaint} textAnchor="end">
            {formatNumber(min)}
          </SvgText>
          <Line x1={PAD.left} y1={PAD.top + innerH} x2={PAD.left + innerW} y2={PAD.top + innerH} stroke={colors.border} strokeWidth={1} />
          {target != null ? (
            <>
              <Line x1={PAD.left} y1={y(target)} x2={PAD.left + innerW} y2={y(target)} stroke={colors.warn} strokeWidth={1.5} strokeDasharray="5 4" />
              <SvgText x={PAD.left + innerW} y={y(target) - 4} fontSize="10" fill={colors.warn} textAnchor="end">
                {`Objetivo ${formatNumber(target)}${u}`}
              </SvgText>
            </>
          ) : null}
          {coords.length > 1 ? (
            <Polyline points={coords.map((c) => `${c.x},${c.y}`).join(" ")} fill="none" stroke={stroke} strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
          ) : null}
          {coords.map((c, i) => (
            <Circle key={i} cx={c.x} cy={c.y} r={i === coords.length - 1 ? 5 : 3.5} fill={i === coords.length - 1 ? stroke : colors.card} stroke={stroke} strokeWidth={2} />
          ))}
          {dateIdx.map((i) => (
            <SvgText
              key={`d${i}`}
              x={coords[i].x}
              y={height - 6}
              fontSize="10"
              fill={colors.textMuted}
              textAnchor={points.length === 1 ? "middle" : i === 0 ? "start" : i === points.length - 1 ? "end" : "middle"}
            >
              {formatDate(points[i].date)}
            </SvgText>
          ))}
        </Svg>
      ) : (
        <View style={{ height }} />
      )}
      {direction ? (
        <Text style={{ fontSize: 11, color: colors.textFaint, marginTop: 4 }}>{direction === "down" ? "↓ Menos es mejor" : "↑ Más es mejor"}</Text>
      ) : null}
    </View>
  );
}
