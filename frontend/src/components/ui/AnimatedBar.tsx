import React, { useEffect } from "react";
import { StyleProp, View, ViewStyle } from "react-native";
import Animated, { useAnimatedStyle, useSharedValue, withDelay, withTiming } from "react-native-reanimated";

import { useTheme } from "../../theme";

interface Props {
  /** 0-100 */
  pct: number;
  color?: string;
  height?: number;
  delay?: number;
  trackColor?: string;
  style?: StyleProp<ViewStyle>;
}

/** Barra de progreso cuyo relleno se anima al aparecer o al cambiar. */
export default function AnimatedBar({ pct, color, height = 6, delay = 0, trackColor, style }: Props) {
  const { colors } = useTheme();
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = withDelay(delay, withTiming(Math.max(0, Math.min(100, pct)), { duration: 800 }));
  }, [pct, delay, progress]);

  const fill = useAnimatedStyle(() => ({ width: `${progress.value}%` }));
  return (
    <View style={[{ height, borderRadius: height / 2, backgroundColor: trackColor ?? colors.track, overflow: "hidden" }, style]}>
      <Animated.View style={[{ height, borderRadius: height / 2, backgroundColor: color ?? colors.primary }, fill]} />
    </View>
  );
}
