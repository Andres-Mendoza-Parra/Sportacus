import { LinearGradient } from "expo-linear-gradient";
import React, { createContext, useContext } from "react";
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

/**
 * Espacio extra (px) que las cabeceras reservan a la derecha: en las pestañas lo ocupa el botón fijo
 * del menú rápido (☰), así ningún contenido de la cabecera queda debajo de él.
 */
export const HeaderRightInsetContext = createContext(0);

/** Cabecera con degradado del color de acento. */
export default function GradientHeader({ children, style, rounded, safeTop = true }: Props) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const rightInset = useContext(HeaderRightInsetContext);
  return (
    <LinearGradient
      colors={colors.headerGradient}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={[
        { paddingHorizontal: 20, paddingRight: 20 + rightInset, paddingTop: 18 + (safeTop ? insets.top : 0), paddingBottom: 24 },
        rounded && { borderBottomLeftRadius: 28, borderBottomRightRadius: 28 },
        style,
      ]}
    >
      {children}
    </LinearGradient>
  );
}
