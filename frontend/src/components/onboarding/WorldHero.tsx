import { LinearGradient } from "expo-linear-gradient";
import { router } from "expo-router";
import React from "react";
import { Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { deriveAccent, useTheme } from "../../theme";
import PressableScale from "../ui/PressableScale";

interface Props {
  color: string;
  icon: string;
  title: string;
  subtitle?: string | null;
  /** Muestra ← (por defecto sí). */
  back?: boolean;
  children?: React.ReactNode;
}

/**
 * Cabecera teñida con el color de UN mundo concreto (no del mundo activo): se usa en el detalle
 * del mundo y en el resultado de la inscripción, donde el mundo aún no es el activo.
 */
export default function WorldHero({ color, icon, title, subtitle, back = true, children }: Props) {
  const { isDark } = useTheme();
  const insets = useSafeAreaInsets();
  const a = deriveAccent(color);
  return (
    <LinearGradient
      colors={isDark ? [a.deep, a.deep, a.dark] : [a.deep, a.dark, a.main]}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={{ paddingHorizontal: 20, paddingTop: insets.top + 16, paddingBottom: 26, borderBottomLeftRadius: 28, borderBottomRightRadius: 28 }}
    >
      {back ? (
        <PressableScale
          onPress={() => (router.canGoBack() ? router.back() : router.replace("/onboarding/gallery"))}
          accessibilityRole="button"
          accessibilityLabel="Volver"
          style={{ width: 38, height: 38, borderRadius: 13, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center", justifyContent: "center", marginBottom: 12 }}
        >
          <Text style={{ color: "#fff", fontSize: 18 }}>←</Text>
        </PressableScale>
      ) : null}
      <View style={{ flexDirection: "row", alignItems: "center", gap: 14 }}>
        <View style={{ width: 64, height: 64, borderRadius: 20, backgroundColor: "rgba(255,255,255,0.18)", alignItems: "center", justifyContent: "center" }}>
          <Text style={{ fontSize: 34 }}>{icon}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={{ color: "#fff", fontSize: 24, fontWeight: "800" }}>{title}</Text>
          {subtitle ? <Text style={{ color: "rgba(255,255,255,0.86)", fontSize: 13, marginTop: 3, lineHeight: 18 }}>{subtitle}</Text> : null}
        </View>
      </View>
      {children}
    </LinearGradient>
  );
}
