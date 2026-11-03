import React from "react";
import Svg, { Circle, Polyline } from "react-native-svg";

interface Props {
  values: number[];
  color: string;
  width?: number;
  height?: number;
}

/** Línea de evolución mínima (sin ejes) para series cortas de mediciones. */
export default function Sparkline({ values, color, width = 220, height = 56 }: Props) {
  if (values.length === 0) return null;
  const pad = 6;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const stepX = values.length > 1 ? (width - pad * 2) / (values.length - 1) : 0;
  const points = values.map((v, i) => ({
    x: values.length > 1 ? pad + i * stepX : width / 2,
    y: height - pad - ((v - min) / span) * (height - pad * 2),
  }));
  const last = points[points.length - 1];

  return (
    <Svg width={width} height={height}>
      {points.length > 1 ? (
        <Polyline points={points.map((p) => `${p.x},${p.y}`).join(" ")} fill="none" stroke={color} strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
      ) : null}
      <Circle cx={last.x} cy={last.y} r={4} fill={color} />
    </Svg>
  );
}
