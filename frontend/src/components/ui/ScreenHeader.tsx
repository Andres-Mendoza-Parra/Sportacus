import { router } from "expo-router";
import React from "react";
import { StyleProp, Text, View, ViewStyle } from "react-native";

import GradientHeader from "./GradientHeader";
import PressableScale from "./PressableScale";

interface Props {
  title: string;
  subtitle?: string | null;
  /** Ícono grande a la izquierda del título (p. ej. el del mundo). */
  icon?: string | null;
  /** Muestra el botón ← (por defecto sí). */
  back?: boolean;
  /** Qué hacer al volver; por defecto router.back() o `fallback`. */
  onBack?: () => void;
  /** Ruta a la que volver si no hay historial. */
  fallback?: Parameters<typeof router.replace>[0];
  right?: React.ReactNode;
  children?: React.ReactNode;
  rounded?: boolean;
  style?: StyleProp<ViewStyle>;
}

/** Cabecera con degradado del mundo activo, botón atrás, título y acciones opcionales. */
export default function ScreenHeader({ title, subtitle, icon, back = true, onBack, fallback = "/home", right, children, rounded, style }: Props) {
  function goBack() {
    if (onBack) return onBack();
    if (router.canGoBack()) router.back();
    else router.replace(fallback);
  }

  return (
    <GradientHeader rounded={rounded} style={[{ paddingBottom: 18 }, style]}>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
        {back ? (
          <PressableScale
            onPress={goBack}
            accessibilityRole="button"
            accessibilityLabel="Volver"
            style={{ width: 38, height: 38, borderRadius: 13, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center", justifyContent: "center" }}
          >
            <Text style={{ color: "#fff", fontSize: 18 }}>←</Text>
          </PressableScale>
        ) : null}
        {icon ? <Text style={{ fontSize: 30 }}>{icon}</Text> : null}
        <View style={{ flex: 1 }}>
          <Text style={{ color: "#fff", fontSize: 20, fontWeight: "700" }} numberOfLines={2}>
            {title}
          </Text>
          {subtitle ? (
            <Text style={{ color: "rgba(255,255,255,0.82)", fontSize: 12, marginTop: 2 }} numberOfLines={2}>
              {subtitle}
            </Text>
          ) : null}
        </View>
        {right}
      </View>
      {children}
    </GradientHeader>
  );
}

/** Botón redondo translúcido para la zona derecha de la cabecera. */
export function HeaderIconButton({ icon, label, onPress }: { icon: string; label: string; onPress: () => void }) {
  return (
    <PressableScale
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={{ width: 38, height: 38, borderRadius: 13, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center", justifyContent: "center" }}
    >
      <Text style={{ color: "#fff", fontSize: 17 }}>{icon}</Text>
    </PressableScale>
  );
}
