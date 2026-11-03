import React from "react";
import { Text, View } from "react-native";

import { FocusItem } from "../../services/dashboardService";
import { useTheme } from "../../theme";
import AnimatedBar from "../ui/AnimatedBar";
import { paletteColor } from "./palette";

/** Barras horizontales animadas, una por ítem, con su detalle (p. ej. "12 de 30 series"). */
export default function BarsChart({ items }: { items: FocusItem[] }) {
  const { colors } = useTheme();
  return (
    <View style={{ gap: 14 }}>
      {items.map((item, idx) => (
        <View key={item.key}>
          <View style={{ flexDirection: "row", alignItems: "baseline", justifyContent: "space-between" }}>
            <Text style={{ fontSize: 13, fontWeight: "600", color: colors.text }}>{item.label}</Text>
            <Text style={{ fontSize: 13, fontWeight: "700", color: paletteColor(item, idx) }}>{Math.round(item.value)}%</Text>
          </View>
          <AnimatedBar pct={item.value} color={paletteColor(item, idx)} height={9} delay={idx * 110} style={{ marginTop: 6 }} />
          {item.detail ? <Text style={{ fontSize: 11, color: colors.textMuted, marginTop: 4 }}>{item.detail}</Text> : null}
        </View>
      ))}
    </View>
  );
}
