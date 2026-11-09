import React from "react";
import { Text, View } from "react-native";

import { formatNumber } from "../../constants/labels";
import { BarItem } from "../../services/types";
import { useTheme, withAlpha } from "../../theme";

interface Props {
  bars: BarItem[];
  unit?: string | null;
  target?: number | null;
  height?: number;
}

/** Valor compacto para la etiqueta sobre la barra: 7920 → "7,9k". */
function compact(v: number): string {
  if (Math.abs(v) >= 1000) return `${formatNumber(v / 1000)}k`;
  return formatNumber(v);
}

/** Barras verticales por semana (la última resaltada) con línea objetivo opcional. */
export default function WeekBarsChart({ bars, unit, target, height = 140 }: Props) {
  const { colors } = useTheme();
  const max = Math.max(1, target ?? 0, ...bars.map((b) => b.value));
  const targetBottom = target != null ? (target / max) * height : null;

  return (
    <View>
      <View style={{ height: height + 18, flexDirection: "row", alignItems: "flex-end", gap: 6 }}>
        {targetBottom != null ? (
          <View pointerEvents="none" style={{ position: "absolute", left: 0, right: 0, bottom: targetBottom, borderTopWidth: 1.5, borderStyle: "dashed", borderColor: colors.warn }}>
            <Text style={{ position: "absolute", right: 0, top: -15, fontSize: 10, color: colors.warn }}>
              Objetivo {formatNumber(target ?? 0)}
            </Text>
          </View>
        ) : null}
        {bars.map((b, i) => {
          const isLast = i === bars.length - 1;
          const h = Math.max(b.value > 0 ? 4 : 2, (b.value / max) * height);
          return (
            <View
              key={`${b.label}-${i}`}
              style={{ flex: 1, alignItems: "center", justifyContent: "flex-end" }}
              accessible
              accessibilityLabel={`${b.label}: ${formatNumber(b.value)} ${unit ?? ""}`}
            >
              {b.value > 0 ? (
                <Text style={{ fontSize: 9, color: isLast ? colors.primary : colors.textMuted, fontWeight: isLast ? "700" : "500", marginBottom: 2 }} numberOfLines={1}>
                  {compact(b.value)}
                </Text>
              ) : null}
              <View
                style={{
                  width: "80%",
                  maxWidth: 34,
                  height: h,
                  borderRadius: 6,
                  backgroundColor: isLast ? colors.primary : withAlpha(colors.primary, 0.35),
                }}
              />
            </View>
          );
        })}
      </View>
      <View style={{ flexDirection: "row", gap: 6, marginTop: 6 }}>
        {bars.map((b, i) => (
          <Text key={`l${i}`} style={{ flex: 1, textAlign: "center", fontSize: 9, color: colors.textMuted }} numberOfLines={1}>
            {b.label}
          </Text>
        ))}
      </View>
      {unit ? <Text style={{ fontSize: 11, color: colors.textFaint, marginTop: 6 }}>En {unit}</Text> : null}
    </View>
  );
}
