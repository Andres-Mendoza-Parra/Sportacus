import React from "react";
import { Text, View } from "react-native";

import { formatDate, formatDistance } from "../../constants/labels";
import { SessionSummary } from "../../services/types";
import { useTheme } from "../../theme";
import { Card } from "../ui/Card";

function details(s: SessionSummary): string {
  return [
    s.durationMin != null ? `${s.durationMin} min` : null,
    s.distanceM != null ? formatDistance(s.distanceM) : null,
    s.rpe != null ? `Esfuerzo ${s.rpe}/10` : null,
    s.painAfter != null ? `Dolor ${s.painAfter}/10` : null,
    s.painNextDay != null ? `A las 24 h ${s.painNextDay}/10` : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

/** Últimas sesiones registradas en el mundo. */
export default function RecentSessions({ sessions }: { sessions: SessionSummary[] }) {
  const { colors } = useTheme();
  if (sessions.length === 0) {
    return (
      <Card>
        <Text style={{ fontSize: 13, color: colors.textMuted, textAlign: "center" }}>Todavía no registras sesiones. Las verás aquí al terminar cada una.</Text>
      </Card>
    );
  }
  return (
    <Card style={{ paddingVertical: 6 }}>
      {sessions.map((s, i) => (
        <View
          key={s.id}
          style={{ flexDirection: "row", alignItems: "center", gap: 12, paddingVertical: 10, borderTopWidth: i === 0 ? 0 : 1, borderTopColor: colors.border }}
        >
          <View style={{ width: 46, alignItems: "center", backgroundColor: colors.primarySoft, borderRadius: 12, paddingVertical: 6 }}>
            <Text style={{ fontSize: 12, fontWeight: "800", color: colors.primary }}>{formatDate(s.performedOn)}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: 14, fontWeight: "700", color: colors.text }} numberOfLines={1}>
              {s.title ?? "Sesión libre"}
            </Text>
            {details(s) ? <Text style={{ fontSize: 12, color: colors.textMuted, marginTop: 2 }}>{details(s)}</Text> : null}
          </View>
        </View>
      ))}
    </Card>
  );
}
