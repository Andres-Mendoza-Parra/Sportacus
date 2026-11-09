import React from "react";
import { Text, View } from "react-native";

import { WorldCard } from "../../services/types";
import { Theme, deriveAccent, useThemedStyles, withAlpha } from "../../theme";
import PressableScale from "../ui/PressableScale";

interface Props {
  world: WorldCard;
  onPress: () => void;
}

export function isSelectable(world: Pick<WorldCard, "status" | "available">): boolean {
  return world.status !== "proximamente" && world.available;
}

/** Tarjeta de la galería: color e ícono del mundo, lema, puntos fuertes y rutas. */
export default function WorldCardItem({ world, onPress }: Props) {
  const styles = useThemedStyles(createStyles);
  const accent = deriveAccent(world.color);
  const selectable = isSelectable(world);
  const badge = world.active ? "Activo" : world.enrolled ? "Inscrito" : !selectable ? "Próximamente" : null;

  return (
    <PressableScale
      onPress={onPress}
      disabled={!selectable && !world.enrolled}
      accessibilityRole="button"
      accessibilityState={{ disabled: !selectable && !world.enrolled }}
      accessibilityLabel={`${world.name}. ${world.tagline}${badge ? `. ${badge}` : ""}`}
      style={[styles.card, { borderLeftColor: accent.main }, !selectable && !world.enrolled && { opacity: 0.55 }]}
    >
      <View style={styles.row}>
        <View style={[styles.iconWrap, { backgroundColor: withAlpha(accent.main, 0.16) }]}>
          <Text style={{ fontSize: 28 }}>{world.icon}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <View style={styles.titleRow}>
            <Text style={styles.name}>{world.name}</Text>
            {badge ? (
              <View style={[styles.badge, { backgroundColor: selectable || world.enrolled ? accent.main : "#888" }]}>
                <Text style={styles.badgeText}>{badge}</Text>
              </View>
            ) : null}
          </View>
          <Text style={styles.tagline}>{world.tagline}</Text>
        </View>
      </View>

      {world.highlights.length > 0 ? (
        <View style={{ gap: 4, marginTop: 12 }}>
          {world.highlights.slice(0, 3).map((h) => (
            <Text key={h} style={styles.highlight}>
              <Text style={{ color: accent.main }}>✓ </Text>
              {h}
            </Text>
          ))}
        </View>
      ) : null}

      {world.tracks.length > 0 ? (
        <View style={styles.tracks}>
          {world.tracks.map((t) => (
            <View key={t.key} style={[styles.track, { backgroundColor: withAlpha(accent.main, 0.12) }]}>
              <Text style={[styles.trackText, { color: accent.main }]}>{`${t.icon ?? ""} ${t.name}`.trim()}</Text>
            </View>
          ))}
        </View>
      ) : null}
    </PressableScale>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  card: { backgroundColor: colors.card, borderRadius: 20, padding: 16, borderLeftWidth: 5, ...shadow },
  row: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  iconWrap: { width: 54, height: 54, borderRadius: 17, alignItems: "center" as const, justifyContent: "center" as const },
  titleRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 8, flexWrap: "wrap" as const },
  name: { fontSize: 17, fontWeight: "800" as const, color: colors.text },
  badge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 999 },
  badgeText: { color: "#fff", fontSize: 10, fontWeight: "700" as const },
  tagline: { fontSize: 13, color: colors.textMuted, marginTop: 2, lineHeight: 18 },
  highlight: { fontSize: 12, color: colors.text, lineHeight: 17 },
  tracks: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 6, marginTop: 12 },
  track: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 999 },
  trackText: { fontSize: 11, fontWeight: "700" as const },
});
