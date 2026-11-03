import React, { useEffect } from "react";
import { Text, View } from "react-native";
import Animated, { useAnimatedProps, useSharedValue, withTiming } from "react-native-reanimated";
import Svg, { Path } from "react-native-svg";

import { FocusItem } from "../../services/dashboardService";
import { useTheme } from "../../theme";
import { useAnimatedNumber } from "../ui/motion";
import { paletteColor } from "./palette";

const AnimatedPath = Animated.createAnimatedComponent(Path);

/** Medidor semicircular para el valor principal (primer ítem) + lista de los demás. */
export default function GaugeChart({ items, size = 220 }: { items: FocusItem[]; size?: number }) {
  const { colors } = useTheme();
  const [main, ...rest] = items;
  const value = Math.max(0, Math.min(100, main?.value ?? 0));
  const stroke = 18;
  const r = (size - stroke) / 2;
  const cy = size / 2;
  const arcLength = Math.PI * r;
  const path = `M ${stroke / 2} ${cy} A ${r} ${r} 0 0 1 ${size - stroke / 2} ${cy}`;
  const color = main ? paletteColor(main, 0) : colors.primary;

  const progress = useSharedValue(0);
  useEffect(() => {
    progress.value = withTiming(value / 100, { duration: 900 });
  }, [value, progress]);
  const animatedProps = useAnimatedProps(() => ({
    strokeDasharray: `${arcLength * progress.value} ${arcLength}`,
  }));
  const shown = Math.round(useAnimatedNumber(value, 900));

  return (
    <View style={{ alignItems: "center", gap: 14 }}>
      <View style={{ width: size, height: size / 2 + stroke }}>
        <Svg width={size} height={size / 2 + stroke}>
          <Path d={path} stroke={colors.track} strokeWidth={stroke} fill="none" strokeLinecap="round" />
          <AnimatedPath
            d={path}
            stroke={color}
            strokeWidth={stroke}
            fill="none"
            strokeLinecap="round"
            animatedProps={animatedProps}
          />
        </Svg>
        <View style={{ position: "absolute", left: 0, right: 0, bottom: 0, alignItems: "center" }}>
          <Text style={{ fontSize: 36, fontWeight: "800", color: colors.text }}>{shown}%</Text>
          <Text style={{ fontSize: 12, color: colors.textMuted }}>{main?.label}</Text>
        </View>
      </View>
      {main?.detail ? <Text style={{ fontSize: 12, color: colors.textMuted, textAlign: "center" }}>{main.detail}</Text> : null}

      {rest.length > 0 ? (
        <View style={{ alignSelf: "stretch", gap: 8 }}>
          {rest.map((item, idx) => (
            <View key={item.key} style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
              <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: paletteColor(item, idx + 1) }} />
              <Text style={{ flex: 1, fontSize: 13, color: colors.text }}>{item.label}</Text>
              <Text style={{ fontSize: 13, fontWeight: "700", color: colors.text }}>{Math.round(item.value)}%</Text>
            </View>
          ))}
        </View>
      ) : null}
    </View>
  );
}
