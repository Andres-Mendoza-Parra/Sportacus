import React, { useEffect } from "react";
import { Text, View } from "react-native";
import Animated, { useAnimatedProps, useSharedValue, withTiming } from "react-native-reanimated";
import Svg, { Circle, G } from "react-native-svg";

import { FocusItem } from "../../services/dashboardService";
import { useTheme } from "../../theme";
import { paletteColor } from "./palette";

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

interface Props {
  items: FocusItem[];
  size?: number;
}

/** Un segmento del donut: su arco se dibuja de 0 a su porción al aparecer. */
function Segment({
  cx,
  cy,
  r,
  stroke,
  circumference,
  start,
  length,
  color,
  delay,
}: {
  cx: number;
  cy: number;
  r: number;
  stroke: number;
  circumference: number;
  start: number;
  length: number;
  color: string;
  delay: number;
}) {
  const progress = useSharedValue(0);
  useEffect(() => {
    progress.value = 0;
    progress.value = withTiming(1, { duration: 700 + delay });
  }, [length, progress, delay]);
  const animatedProps = useAnimatedProps(() => ({
    strokeDasharray: `${Math.max(0, length * progress.value - 1.5)} ${circumference}`,
  }));
  return (
    <AnimatedCircle
      cx={cx}
      cy={cy}
      r={r}
      stroke={color}
      strokeWidth={stroke}
      fill="none"
      strokeLinecap="butt"
      strokeDashoffset={-start}
      animatedProps={animatedProps}
    />
  );
}

/** Donut con leyenda: reparte el total entre los valores de cada ítem. */
export default function DonutChart({ items, size = 170 }: Props) {
  const { colors } = useTheme();
  const total = items.reduce((s, i) => s + Math.max(0, i.value), 0);
  const stroke = 22;
  const r = (size - stroke) / 2;
  const c = size / 2;
  const circumference = 2 * Math.PI * r;

  let offset = 0;
  const segments = items.map((item, idx) => {
    const frac = total > 0 ? Math.max(0, item.value) / total : 0;
    const length = frac * circumference;
    const seg = { item, start: offset, length, color: paletteColor(item, idx) };
    offset += length;
    return seg;
  });

  return (
    <View style={{ alignItems: "center", gap: 16 }}>
      <View style={{ width: size, height: size, alignItems: "center", justifyContent: "center" }}>
        <Svg width={size} height={size} style={{ position: "absolute", transform: [{ rotate: "-90deg" }] }}>
          <G>
            <Circle cx={c} cy={c} r={r} stroke={colors.track} strokeWidth={stroke} fill="none" />
            {segments.map((s, i) => (
              <Segment
                key={s.item.key}
                cx={c}
                cy={c}
                r={r}
                stroke={stroke}
                circumference={circumference}
                start={s.start}
                length={s.length}
                color={s.color}
                delay={i * 120}
              />
            ))}
          </G>
        </Svg>
        <Text style={{ fontSize: 26, fontWeight: "800", color: colors.text }}>{Math.round(total)}</Text>
        <Text style={{ fontSize: 11, color: colors.textMuted }}>total</Text>
      </View>

      <View style={{ alignSelf: "stretch", gap: 8 }}>
        {segments.map((s) => (
          <View key={s.item.key} style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
            <View style={{ width: 12, height: 12, borderRadius: 4, backgroundColor: s.color }} />
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: 13, fontWeight: "600", color: colors.text }}>{s.item.label}</Text>
              {s.item.detail ? <Text style={{ fontSize: 11, color: colors.textMuted }}>{s.item.detail}</Text> : null}
            </View>
            <Text style={{ fontSize: 13, fontWeight: "700", color: colors.text }}>
              {total > 0 ? Math.round((s.item.value / total) * 100) : 0}%
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}
