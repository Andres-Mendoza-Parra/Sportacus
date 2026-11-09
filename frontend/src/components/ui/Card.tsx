import React from "react";
import { StyleProp, Text, View, ViewStyle } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { useTheme } from "../../theme";

interface CardProps {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  /** Retraso de la animación de entrada (ms); sin valor no se anima. */
  delay?: number;
}

/** Tarjeta base del sistema de diseño (fondo, radio y sombra del tema). */
export function Card({ children, style, delay }: CardProps) {
  const { colors, shadow } = useTheme();
  const base: ViewStyle = { backgroundColor: colors.card, borderRadius: 18, padding: 16, ...shadow };
  if (delay === undefined) return <View style={[base, style]}>{children}</View>;
  return (
    <Animated.View entering={FadeInDown.delay(delay).duration(380)} style={[base, style]}>
      {children}
    </Animated.View>
  );
}

/** Título pequeño en mayúsculas para separar secciones. */
export function SectionTitle({ children, style }: { children: React.ReactNode; style?: StyleProp<ViewStyle> }) {
  const { colors } = useTheme();
  return (
    <View style={[{ marginTop: 22, marginBottom: 8 }, style]}>
      <Text style={{ fontSize: 11, color: colors.textFaint, fontWeight: "700", textTransform: "uppercase", letterSpacing: 0.8 }}>
        {children}
      </Text>
    </View>
  );
}

/** Título + subtítulo dentro de una tarjeta. */
export function CardTitle({ title, subtitle, right }: { title?: string | null; subtitle?: string | null; right?: React.ReactNode }) {
  const { colors } = useTheme();
  if (!title && !subtitle && !right) return null;
  return (
    <View style={{ flexDirection: "row", alignItems: "flex-start", marginBottom: 12, gap: 8 }}>
      <View style={{ flex: 1 }}>
        {title ? <Text style={{ fontSize: 15, fontWeight: "700", color: colors.text }}>{title}</Text> : null}
        {subtitle ? <Text style={{ fontSize: 12, color: colors.textMuted, marginTop: 2 }}>{subtitle}</Text> : null}
      </View>
      {right}
    </View>
  );
}
