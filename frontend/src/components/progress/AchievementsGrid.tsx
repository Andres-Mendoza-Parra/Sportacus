import React from "react";
import { Text, View } from "react-native";

import { formatDate } from "../../constants/labels";
import { Achievement } from "../../services/types";
import { useTheme } from "../../theme";
import { Card } from "../ui/Card";

/** Cuadrícula de logros desbloqueados en el mundo. */
export default function AchievementsGrid({ achievements }: { achievements: Achievement[] }) {
  const { colors } = useTheme();
  if (achievements.length === 0) {
    return (
      <Card>
        <Text style={{ fontSize: 13, color: colors.textMuted, textAlign: "center" }}>
          🏅 Aún no tienes logros aquí. Completa tu primera sesión para estrenar la vitrina.
        </Text>
      </Card>
    );
  }
  return (
    <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
      {achievements.map((a) => (
        <View
          key={a.key}
          accessible
          accessibilityLabel={`Logro: ${a.title}${a.achievedAt ? `, ${formatDate(a.achievedAt, true)}` : ""}`}
          style={{ flexBasis: "47%", flexGrow: 1, backgroundColor: colors.card, borderRadius: 16, padding: 12, alignItems: "center", gap: 4, borderWidth: 1, borderColor: colors.border }}
        >
          <Text style={{ fontSize: 30 }}>{a.icon ?? "🏅"}</Text>
          <Text style={{ fontSize: 13, fontWeight: "700", color: colors.text, textAlign: "center" }} numberOfLines={2}>
            {a.title}
          </Text>
          {a.achievedAt ? <Text style={{ fontSize: 11, color: colors.textFaint }}>{formatDate(a.achievedAt, true)}</Text> : null}
        </View>
      ))}
    </View>
  );
}
