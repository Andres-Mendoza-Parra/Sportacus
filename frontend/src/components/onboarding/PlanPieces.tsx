import React from "react";
import { Text, View } from "react-native";

import { weekdayName } from "../../constants/labels";
import { Factor, PlanView, TrackInfo } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";

/** Lista de factores explicables ("por qué"): cada uno con su texto y, si suma, sus puntos. */
export function FactorList({ factors, color }: { factors: Factor[]; color?: string }) {
  const styles = useThemedStyles(createStyles);
  if (factors.length === 0) return null;
  return (
    <View style={{ gap: 8 }}>
      {factors.map((f) => (
        <View key={`${f.key}-${f.text}`} style={styles.factor}>
          <Text style={[styles.bullet, color ? { color } : null]}>●</Text>
          <Text style={styles.factorText}>{f.text}</Text>
          {f.points > 0 ? <Text style={[styles.points, color ? { color } : null]}>{`+${Math.round(f.points)}`}</Text> : null}
        </View>
      ))}
    </View>
  );
}

/** Rutas de un mundo con su descripción. */
export function TrackList({ tracks, color }: { tracks: TrackInfo[]; color: string }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={{ gap: 10 }}>
      {[...tracks]
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((t) => (
          <View key={t.key} style={[styles.track, { borderLeftColor: color }]}>
            <Text style={styles.trackName}>{`${t.icon ?? "•"}  ${t.name}`}</Text>
            <Text style={styles.trackDesc}>{t.description}</Text>
          </View>
        ))}
    </View>
  );
}

/** Vista previa de las sesiones del plan recién creado (semana actual). */
export function PlanPreview({ plan, color }: { plan: PlanView; color: string }) {
  const styles = useThemedStyles(createStyles);
  const sessions = plan.sessions.filter((s) => s.weekIndex === plan.currentWeek);
  const list = sessions.length > 0 ? sessions : plan.sessions;
  return (
    <View style={{ gap: 10 }}>
      <Text style={styles.planTitle}>{plan.title}</Text>
      {plan.summary ? <Text style={styles.trackDesc}>{plan.summary}</Text> : null}
      {plan.weeks > 1 ? <Text style={styles.weeks}>{`Plan de ${plan.weeks} semanas`}</Text> : null}
      {list.map((s) => (
        <View key={s.id} style={styles.session}>
          <View style={[styles.day, { backgroundColor: color }]}>
            <Text style={styles.dayText}>{weekdayName(s.weekday, true)}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.sessionTitle}>{s.title}</Text>
            {s.focus ? (
              <Text style={styles.sessionFocus} numberOfLines={2}>
                {s.focus}
              </Text>
            ) : null}
          </View>
          {s.estMinutes ? <Text style={styles.minutes}>{`${s.estMinutes} min`}</Text> : null}
        </View>
      ))}
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  factor: { flexDirection: "row" as const, alignItems: "flex-start" as const, gap: 8 },
  bullet: { fontSize: 10, color: colors.primary, marginTop: 4 },
  factorText: { flex: 1, fontSize: 13, color: colors.text, lineHeight: 19 },
  points: { fontSize: 12, fontWeight: "700" as const, color: colors.primary },
  track: { borderLeftWidth: 3, paddingLeft: 12, paddingVertical: 2 },
  trackName: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  trackDesc: { fontSize: 12, color: colors.textMuted, marginTop: 2, lineHeight: 17 },
  planTitle: { fontSize: 15, fontWeight: "700" as const, color: colors.text },
  weeks: { fontSize: 12, fontWeight: "600" as const, color: colors.textMuted },
  session: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, padding: 10, borderRadius: 14, backgroundColor: colors.cardAlt },
  day: { width: 46, height: 46, borderRadius: 14, alignItems: "center" as const, justifyContent: "center" as const },
  dayText: { color: "#fff", fontSize: 12, fontWeight: "800" as const },
  sessionTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  sessionFocus: { fontSize: 11, color: colors.textMuted, marginTop: 2 },
  minutes: { fontSize: 12, fontWeight: "700" as const, color: colors.textMuted },
});
