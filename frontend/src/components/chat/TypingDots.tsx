import React, { useEffect } from "react";
import { View } from "react-native";
import Animated, { useAnimatedStyle, useSharedValue, withDelay, withRepeat, withSequence, withTiming } from "react-native-reanimated";

import { useTheme } from "../../theme";

function Dot({ delay, color }: { delay: number; color: string }) {
  const opacity = useSharedValue(0.3);
  useEffect(() => {
    opacity.value = withDelay(delay, withRepeat(withSequence(withTiming(1, { duration: 400 }), withTiming(0.3, { duration: 400 })), -1));
  }, [delay, opacity]);
  const style = useAnimatedStyle(() => ({ opacity: opacity.value }));
  return <Animated.View style={[{ width: 7, height: 7, borderRadius: 4, backgroundColor: color }, style]} />;
}

/** Tres puntos que laten mientras el asistente "escribe". */
export default function TypingDots() {
  const { colors } = useTheme();
  return (
    <View style={{ flexDirection: "row", gap: 4, paddingVertical: 4 }} accessibilityLabel="El asistente está escribiendo">
      {[0, 1, 2].map((i) => (
        <Dot key={i} delay={i * 150} color={colors.primaryDark} />
      ))}
    </View>
  );
}
