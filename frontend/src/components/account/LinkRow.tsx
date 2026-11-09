import React from "react";
import { ActivityIndicator, Text, View } from "react-native";

import { useTheme } from "../../theme";
import PressableScale from "../ui/PressableScale";

interface Props {
  icon: string;
  title: string;
  subtitle?: string;
  onPress: () => void;
  danger?: boolean;
  loading?: boolean;
  last?: boolean;
}

/** Fila de menú con ícono, texto y flecha. */
export default function LinkRow({ icon, title, subtitle, onPress, danger, loading, last }: Props) {
  const { colors } = useTheme();
  return (
    <PressableScale
      onPress={onPress}
      disabled={loading}
      accessibilityRole="button"
      accessibilityLabel={title}
      scaleTo={0.98}
      style={{
        flexDirection: "row",
        alignItems: "center",
        gap: 12,
        paddingVertical: 13,
        borderBottomWidth: last ? 0 : 1,
        borderBottomColor: colors.border,
      }}
    >
      <Text style={{ fontSize: 19, width: 26, textAlign: "center" }}>{icon}</Text>
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: 14, fontWeight: "600", color: danger ? colors.danger : colors.text }}>{title}</Text>
        {subtitle ? <Text style={{ fontSize: 12, color: colors.textMuted, marginTop: 1 }}>{subtitle}</Text> : null}
      </View>
      {loading ? <ActivityIndicator color={colors.primary} /> : <Text style={{ fontSize: 20, color: colors.textFaint }}>›</Text>}
    </PressableScale>
  );
}
