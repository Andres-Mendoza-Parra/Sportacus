import React from "react";
import { Text, View } from "react-native";
import Svg, { Circle } from "react-native-svg";

import { useTheme } from "../../theme";
import { useAnimatedNumber } from "../ui/motion";

interface Props {
  /** 0–100 */
  pct: number;
  size?: number;
  stroke?: number;
  color?: string;
  trackColor?: string;
  /** Color del número central (por defecto el del anillo). */
  textColor?: string;
  caption?: string | null;
  captionColor?: string;
}

/** Anillo de avance con el porcentaje en el centro (Perfil 360). */
export default function ProgressRing({ pct, size = 96, stroke = 10, color, trackColor, textColor, caption, captionColor }: Props) {
  const { colors } = useTheme();
  const clamped = Math.max(0, Math.min(100, pct));
  const animated = useAnimatedNumber(clamped, 900);
  const r = (size - stroke) / 2;
  const circumference = 2 * Math.PI * r;
  const main = color ?? colors.primary;

  return (
    <View style={{ width: size, height: size }} accessible accessibilityLabel={`Perfil completo al ${Math.round(clamped)} por ciento`}>
      <Svg width={size} height={size}>
        <Circle cx={size / 2} cy={size / 2} r={r} stroke={trackColor ?? colors.track} strokeWidth={stroke} fill="none" />
        <Circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke={main}
          strokeWidth={stroke}
          fill="none"
          strokeLinecap="round"
          strokeDasharray={`${circumference} ${circumference}`}
          strokeDashoffset={circumference * (1 - animated / 100)}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </Svg>
      <View style={{ position: "absolute", top: 0, left: 0, right: 0, bottom: 0, alignItems: "center", justifyContent: "center" }}>
        <Text style={{ fontSize: size * 0.24, fontWeight: "800", color: textColor ?? main }}>{`${Math.round(animated)}%`}</Text>
        {caption ? (
          <Text style={{ fontSize: Math.max(9, size * 0.1), color: captionColor ?? colors.textMuted, marginTop: -2 }} numberOfLines={1}>
            {caption}
          </Text>
        ) : null}
      </View>
    </View>
  );
}
