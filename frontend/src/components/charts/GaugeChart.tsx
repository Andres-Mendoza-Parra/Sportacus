import React from "react";
import { Text, View } from "react-native";
import Svg, { Circle, Line, Path } from "react-native-svg";

import { formatNumber } from "../../constants/labels";
import { GaugeZone } from "../../services/types";
import { useTheme } from "../../theme";
import { useAnimatedNumber } from "../ui/motion";
import { toneColor } from "./palette";

interface Props {
  value: number;
  min: number;
  max: number;
  unit?: string | null;
  zones: GaugeZone[];
  caption?: string | null;
  size?: number;
}

/** Punto del semicírculo para la fracción f (0 = izquierda, 1 = derecha). */
function polar(cx: number, cy: number, r: number, f: number) {
  const angle = Math.PI * (1 - f);
  return { x: cx + r * Math.cos(angle), y: cy - r * Math.sin(angle) };
}

function arc(cx: number, cy: number, r: number, f1: number, f2: number): string {
  const a = polar(cx, cy, r, f1);
  const b = polar(cx, cy, r, f2);
  return `M ${a.x} ${a.y} A ${r} ${r} 0 0 1 ${b.x} ${b.y}`;
}

/** Medidor semicircular con zonas coloreadas (p. ej. ACWR 0,8–1,3 seguro) y aguja en el valor. */
export default function GaugeChart({ value, min, max, unit, zones, caption, size = 240 }: Props) {
  const { colors } = useTheme();
  const span = max - min || 1;
  const frac = (v: number) => Math.max(0, Math.min(1, (v - min) / span));
  const stroke = 16;
  const r = (size - stroke) / 2 - 4;
  const cx = size / 2;
  const cy = r + stroke / 2 + 4;
  const height = cy + 14;

  const animated = useAnimatedNumber(frac(value), 900);
  const needle = polar(cx, cy, r - stroke, animated);
  const current = zones.find((z) => value >= z.from && value <= z.to);
  const valueColor = current ? toneColor(current.tone, colors) : colors.primary;

  return (
    <View style={{ alignItems: "center", gap: 10 }}>
      <View
        style={{ width: size, height }}
        accessible
        accessibilityLabel={`Valor ${formatNumber(value, 2)} ${unit ?? ""}${current ? `, zona ${current.label}` : ""}`}
      >
        <Svg width={size} height={height}>
          <Path d={arc(cx, cy, r, 0, 1)} stroke={colors.track} strokeWidth={stroke} fill="none" strokeLinecap="round" />
          {zones.map((z, i) => {
            const f1 = frac(z.from);
            const f2 = frac(z.to);
            if (f2 <= f1) return null;
            return <Path key={`${z.label}-${i}`} d={arc(cx, cy, r, f1, f2)} stroke={toneColor(z.tone, colors)} strokeWidth={stroke} fill="none" opacity={0.85} />;
          })}
          <Line x1={cx} y1={cy} x2={needle.x} y2={needle.y} stroke={colors.text} strokeWidth={3} strokeLinecap="round" />
          <Circle cx={cx} cy={cy} r={7} fill={colors.text} />
        </Svg>
        <Text style={{ position: "absolute", left: 0, bottom: 0, fontSize: 10, color: colors.textFaint }}>{formatNumber(min, 2)}</Text>
        <Text style={{ position: "absolute", right: 0, bottom: 0, fontSize: 10, color: colors.textFaint }}>{formatNumber(max, 2)}</Text>
      </View>
      <Text style={{ fontSize: 30, fontWeight: "800", color: valueColor }}>
        {formatNumber(value, 2)}
        {unit ? <Text style={{ fontSize: 14, color: colors.textMuted, fontWeight: "500" }}> {unit}</Text> : null}
      </Text>
      {current ? <Text style={{ fontSize: 12, fontWeight: "700", color: valueColor }}>{current.label}</Text> : null}
      {caption ? <Text style={{ fontSize: 12, color: colors.textMuted, textAlign: "center" }}>{caption}</Text> : null}
      {zones.length > 0 ? (
        <View style={{ flexDirection: "row", flexWrap: "wrap", justifyContent: "center", gap: 12 }}>
          {zones.map((z, i) => (
            <View key={`${z.label}-l${i}`} style={{ flexDirection: "row", alignItems: "center", gap: 5 }}>
              <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: toneColor(z.tone, colors) }} />
              <Text style={{ fontSize: 11, color: colors.textMuted }}>
                {z.label} ({formatNumber(z.from, 2)}–{formatNumber(z.to, 2)})
              </Text>
            </View>
          ))}
        </View>
      ) : null}
    </View>
  );
}
