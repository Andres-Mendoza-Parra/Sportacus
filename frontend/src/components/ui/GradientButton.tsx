import { LinearGradient } from "expo-linear-gradient";
import React from "react";
import { ActivityIndicator, StyleProp, Text, ViewStyle } from "react-native";

import { useTheme } from "../../theme";
import PressableScale from "./PressableScale";

interface Props {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  variant?: "primary" | "danger" | "ghost";
}

/** Botón principal con degradado, rebote y estado de carga. */
export default function GradientButton({ title, onPress, loading, disabled, style, variant = "primary" }: Props) {
  const { colors } = useTheme();
  const inactive = disabled || loading;

  if (variant === "ghost") {
    return (
      <PressableScale
        onPress={onPress}
        disabled={inactive}
        style={[
          { paddingVertical: 13, borderRadius: 14, alignItems: "center", backgroundColor: colors.cardAlt, opacity: inactive ? 0.6 : 1 },
          style,
        ]}
      >
        {loading ? (
          <ActivityIndicator color={colors.primary} />
        ) : (
          <Text style={{ color: colors.text, fontSize: 14, fontWeight: "600" }}>{title}</Text>
        )}
      </PressableScale>
    );
  }

  const gradient: [string, string] = variant === "danger" ? [colors.danger, "#8e1b1b"] : colors.buttonGradient;
  return (
    <PressableScale onPress={onPress} disabled={inactive} style={[{ opacity: inactive ? 0.6 : 1 }, style]}>
      <LinearGradient
        colors={gradient}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={{ paddingVertical: 14, borderRadius: 14, alignItems: "center", justifyContent: "center" }}
      >
        {loading ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={{ color: "#fff", fontSize: 15, fontWeight: "600" }}>{title}</Text>
        )}
      </LinearGradient>
    </PressableScale>
  );
}
