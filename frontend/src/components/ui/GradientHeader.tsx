import { LinearGradient } from "expo-linear-gradient";
import React from "react";
import { StyleProp, ViewStyle } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useTheme } from "../../theme";

interface Props {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  /** Redondea las esquinas inferiores (estilo del dashboard). */
  rounded?: boolean;
  /** Reserva el espacio de la barra de estado (por defecto sí). */
  safeTop?: boolean;
}

/** Cabecera con degradado del color de acento. */
export default function GradientHeader({ children, style, rounded, safeTop = true }: Props) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  return (
    <LinearGradient
      colors={colors.headerGradient}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={[
        { paddingHorizontal: 20, paddingTop: 18 + (safeTop ? insets.top : 0), paddingBottom: 24 },
        rounded && { borderBottomLeftRadius: 28, borderBottomRightRadius: 28 },
        style,
      ]}
    >
      {children}
    </LinearGradient>
  );
}
