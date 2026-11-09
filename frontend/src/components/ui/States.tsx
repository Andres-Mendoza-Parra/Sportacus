import React from "react";
import { StyleProp, Text, View, ViewStyle } from "react-native";
import Animated, { FadeIn } from "react-native-reanimated";

import { useTheme } from "../../theme";
import GradientButton from "./GradientButton";

interface EmptyProps {
  icon: string;
  title: string;
  body?: string;
  actionLabel?: string;
  onAction?: () => void;
  style?: StyleProp<ViewStyle>;
}

/** Estado vacío: ícono, mensaje y acción opcional. */
export function EmptyState({ icon, title, body, actionLabel, onAction, style }: EmptyProps) {
  const { colors } = useTheme();
  return (
    <Animated.View entering={FadeIn.duration(300)} style={[{ alignItems: "center", padding: 28, gap: 6 }, style]}>
      <Text style={{ fontSize: 42 }}>{icon}</Text>
      <Text style={{ fontSize: 16, fontWeight: "700", color: colors.text, textAlign: "center" }}>{title}</Text>
      {body ? <Text style={{ fontSize: 13, color: colors.textMuted, textAlign: "center", lineHeight: 19 }}>{body}</Text> : null}
      {actionLabel && onAction ? <GradientButton title={actionLabel} onPress={onAction} style={{ marginTop: 12, alignSelf: "stretch" }} /> : null}
    </Animated.View>
  );
}

/** Error de carga con botón de reintento. */
export function ErrorState({ message, onRetry, style }: { message: string; onRetry?: () => void; style?: StyleProp<ViewStyle> }) {
  return <EmptyState icon="📡" title="No pudimos cargar esto" body={message} actionLabel={onRetry ? "Reintentar" : undefined} onAction={onRetry} style={style} />;
}

/** Fila de banner de "datos sin conexión". */
export function OfflineBanner() {
  const { colors } = useTheme();
  return (
    <View style={{ backgroundColor: colors.warnSoft, padding: 10, borderRadius: 12, marginBottom: 10 }}>
      <Text style={{ color: colors.warn, fontSize: 12, fontWeight: "600" }}>Sin conexión: mostramos tu última copia guardada.</Text>
    </View>
  );
}
