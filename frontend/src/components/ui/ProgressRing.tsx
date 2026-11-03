import React from "react";
import { Text, View } from "react-native";
import Svg, { Circle } from "react-native-svg";

import { useTheme } from "../../theme";
import { useAnimatedNumber } from "./motion";

interface Props {
  /** 0-100 */
  pct: number;
  size?: number;
  stroke?: number;
  color?: string;
  trackColor?: string;
  /** Texto del centro; por defecto el porcentaje. */
  label?: string;
  labelColor?: string;
  fontSize?: number;
}

/** Anillo de progreso animado en SVG. */
export default function ProgressRing({ pct, size = 48, stroke = 5, color, trackColor, label, labelColor, fontSize }: Props) {
  const { colors } = useTheme();
  const value = useAnimatedNumber(Math.max(0, Math.min(100, pct)), 800);
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;

  return (
    <View style={{ width: size, height: size, alignItems: "center", justifyContent: "center" }}>
      {/* El giro se aplica al contenedor: las props rotation/origin de SVG generan atributos inválidos en web. */}
      <Svg width={size} height={size} style={{ position: "absolute", transform: [{ rotate: "-90deg" }] }}>
        <Circle cx={size / 2} cy={size / 2} r={radius} stroke={trackColor ?? colors.track} strokeWidth={stroke} fill="none" />
        <Circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          stroke={color ?? colors.primary}
          strokeWidth={stroke}
          fill="none"
          strokeLinecap="round"
          strokeDasharray={`${circumference} ${circumference}`}
          strokeDashoffset={circumference * (1 - value / 100)}
        />
      </Svg>
      <Text style={{ fontSize: fontSize ?? size * 0.27, fontWeight: "700", color: labelColor ?? colors.text }}>
        {label ?? `${Math.round(value)}%`}
      </Text>
    </View>
  );
}
