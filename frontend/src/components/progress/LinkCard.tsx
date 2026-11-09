import React from "react";
import { Text, View } from "react-native";

import { useTheme } from "../../theme";
import PressableScale from "../ui/PressableScale";

interface Props {
  icon: string;
  title: string;
  body: string;
  onPress: () => void;
  /** Resaltado con el color del mundo. */
  highlight?: boolean;
}

/** Tarjeta-enlace a otra sección (Tests, Biblioteca…). */
export default function LinkCard({ icon, title, body, onPress, highlight }: Props) {
  const { colors, shadow } = useTheme();
  return (
    <PressableScale
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={title}
      style={{
        flexDirection: "row",
        alignItems: "center",
        gap: 12,
        padding: 14,
        borderRadius: 18,
        backgroundColor: highlight ? colors.primary : colors.card,
        ...shadow,
      }}
    >
      <View style={{ width: 44, height: 44, borderRadius: 14, backgroundColor: highlight ? "rgba(255,255,255,0.2)" : colors.primarySoft, alignItems: "center", justifyContent: "center" }}>
        <Text style={{ fontSize: 22 }}>{icon}</Text>
      </View>
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: 15, fontWeight: "700", color: highlight ? colors.onPrimary : colors.text }}>{title}</Text>
        <Text style={{ fontSize: 12, color: highlight ? "rgba(255,255,255,0.85)" : colors.textMuted, marginTop: 2 }}>{body}</Text>
      </View>
      <Text style={{ fontSize: 18, color: highlight ? colors.onPrimary : colors.textFaint }}>›</Text>
    </PressableScale>
  );
}
