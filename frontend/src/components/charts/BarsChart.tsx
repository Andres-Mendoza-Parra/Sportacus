import React from "react";
import { Text, View } from "react-native";

import { useTheme } from "../../theme";
import AnimatedBar from "../ui/AnimatedBar";
import { paletteColor } from "./palette";

export interface BarsChartItem {
  label: string;
  /** 0-100 */
  value: number;
  detail?: string | null;
  color?: string | null;
}

/** Barras horizontales animadas, una por ítem, con su detalle (p. ej. "12 de 30 series"). */
export default function BarsChart({ items }: { items: BarsChartItem[] }) {
  const { colors } = useTheme();
  return (
    <View style={{ gap: 14 }}>
      {items.map((item, idx) => {
        const color = paletteColor(idx, item.color);
        return (
          <View key={`${item.label}-${idx}`} accessible accessibilityLabel={`${item.label}: ${Math.round(item.value)}%${item.detail ? `, ${item.detail}` : ""}`}>
            <View style={{ flexDirection: "row", alignItems: "baseline", justifyContent: "space-between" }}>
              <Text style={{ fontSize: 13, fontWeight: "600", color: colors.text }}>{item.label}</Text>
              <Text style={{ fontSize: 13, fontWeight: "700", color }}>{Math.round(item.value)}%</Text>
            </View>
            <AnimatedBar pct={item.value} color={color} height={9} delay={idx * 110} style={{ marginTop: 6 }} />
            {item.detail ? <Text style={{ fontSize: 11, color: colors.textMuted, marginTop: 4 }}>{item.detail}</Text> : null}
          </View>
        );
      })}
    </View>
  );
}
