import React, { useEffect } from "react";
import { DimensionValue, StyleProp, View, ViewStyle } from "react-native";
import Animated, { useAnimatedStyle, useSharedValue, withRepeat, withTiming } from "react-native-reanimated";

import { useTheme } from "../../theme";

interface BlockProps {
  height?: number;
  width?: DimensionValue;
  radius?: number;
  style?: StyleProp<ViewStyle>;
}

/** Bloque gris que "respira" mientras carga el contenido real. */
export function SkeletonBlock({ height = 16, width = "100%", radius = 12, style }: BlockProps) {
  const { colors } = useTheme();
  const opacity = useSharedValue(0.5);

  useEffect(() => {
    opacity.value = withRepeat(withTiming(1, { duration: 800 }), -1, true);
  }, [opacity]);

  const animated = useAnimatedStyle(() => ({ opacity: opacity.value }));
  return <Animated.View style={[{ height, width, borderRadius: radius, backgroundColor: colors.track }, animated, style]} />;
}

/** Pantalla de carga genérica: cabecera + varias tarjetas. */
export function ScreenSkeleton({ cards = 3 }: { cards?: number }) {
  const { colors } = useTheme();
  return (
    <View style={{ flex: 1, backgroundColor: colors.bg, padding: 16, paddingTop: 64, gap: 14 }}>
      <SkeletonBlock height={110} radius={24} />
      {Array.from({ length: cards }).map((_, i) => (
        <SkeletonBlock key={i} height={78} radius={16} />
      ))}
    </View>
  );
}
