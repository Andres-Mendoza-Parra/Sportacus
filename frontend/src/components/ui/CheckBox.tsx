import React, { useEffect, useRef, useState } from "react";
import { Pressable, StyleProp, Text, ViewStyle } from "react-native";
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from "react-native-reanimated";

import { useTheme } from "../../theme";

interface Props {
  checked: boolean;
  onToggle: () => void;
  size?: number;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}

const PARTICLES = 8;

/** Una partícula del destello: sale disparada desde el centro y se desvanece. */
function Particle({ index, trigger, size, color }: { index: number; trigger: number; size: number; color: string }) {
  const progress = useSharedValue(0);
  const angle = (Math.PI * 2 * index) / PARTICLES;
  const distance = size * 0.95;

  useEffect(() => {
    if (trigger === 0) return;
    progress.value = 0;
    progress.value = withTiming(1, { duration: 520, easing: Easing.out(Easing.cubic) });
  }, [trigger, progress]);

  const style = useAnimatedStyle(() => ({
    opacity: progress.value === 0 ? 0 : 1 - progress.value,
    transform: [
      { translateX: Math.cos(angle) * distance * progress.value },
      { translateY: Math.sin(angle) * distance * progress.value },
      { scale: 1 - progress.value * 0.5 },
    ],
  }));

  const dot = Math.max(4, Math.round(size * 0.16));
  return (
    <Animated.View
      pointerEvents="none"
      style={[
        { position: "absolute", width: dot, height: dot, borderRadius: dot / 2, backgroundColor: color },
        style,
      ]}
    />
  );
}

/** Casilla grande de checklist: rebote al marcar, relleno de acento y destello de partículas. */
export default function CheckBox({ checked, onToggle, size = 30, disabled, style }: Props) {
  const { colors } = useTheme();
  const fill = useSharedValue(checked ? 1 : 0);
  const pop = useSharedValue(1);
  const burst = useRef(0);
  const [burstKey, setBurstKey] = useState(0);
  const first = useRef(true);

  useEffect(() => {
    fill.value = withTiming(checked ? 1 : 0, { duration: 220 });
    if (first.current) {
      first.current = false;
      return;
    }
    if (checked) {
      pop.value = withSequence(withSpring(1.3, { damping: 6, stiffness: 420 }), withSpring(1, { damping: 9, stiffness: 260 }));
      burst.current += 1;
      setBurstKey(burst.current);
    } else {
      pop.value = withSequence(withTiming(0.88, { duration: 90 }), withSpring(1));
    }
  }, [checked, fill, pop]);

  const boxStyle = useAnimatedStyle(() => ({ transform: [{ scale: pop.value }] }));
  const fillStyle = useAnimatedStyle(() => ({ opacity: fill.value, transform: [{ scale: 0.6 + fill.value * 0.4 }] }));
  const markStyle = useAnimatedStyle(() => ({ opacity: fill.value, transform: [{ scale: fill.value }] }));

  return (
    <Pressable
      onPress={onToggle}
      disabled={disabled}
      hitSlop={8}
      accessibilityRole="checkbox"
      accessibilityState={{ checked, disabled: !!disabled }}
      style={[{ width: size, height: size, alignItems: "center", justifyContent: "center" }, style]}
    >
      {Array.from({ length: PARTICLES }).map((_, i) => (
        <Particle key={i} index={i} trigger={burstKey} size={size} color={i % 2 === 0 ? colors.primary : colors.warn} />
      ))}
      <Animated.View
        style={[
          {
            width: size,
            height: size,
            borderRadius: size * 0.34,
            borderWidth: 2,
            borderColor: checked ? colors.primary : colors.textFaint,
            alignItems: "center",
            justifyContent: "center",
            overflow: "hidden",
          },
          boxStyle,
        ]}
      >
        <Animated.View
          style={[{ position: "absolute", top: 0, left: 0, right: 0, bottom: 0, backgroundColor: colors.primary }, fillStyle]}
        />
        <Animated.View style={markStyle}>
          <Text style={{ color: colors.onPrimary, fontSize: size * 0.6, fontWeight: "800", lineHeight: size * 0.75 }}>✓</Text>
        </Animated.View>
      </Animated.View>
    </Pressable>
  );
}
