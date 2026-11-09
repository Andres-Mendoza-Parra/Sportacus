import React from "react";
import { Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { RankedWorld } from "../../services/types";
import { Theme, deriveAccent, useThemedStyles, withAlpha } from "../../theme";
import AnimatedBar from "../ui/AnimatedBar";
import GradientButton from "../ui/GradientButton";

interface Props {
  item: RankedWorld;
  position: number;
  /** false si el mundo aún no se puede elegir (próximamente). */
  available: boolean;
  onPick: () => void;
}

/** Un mundo del ranking de descubrimiento: afinidad 0-100, razones y precauciones. */
export default function RankingItem({ item, position, available, onPick }: Props) {
  const styles = useThemedStyles(createStyles);
  const accent = deriveAccent(item.color);
  const score = Math.max(0, Math.min(100, Math.round(item.score)));

  return (
    <Animated.View entering={FadeInDown.delay(position * 90).duration(380)} style={[styles.card, position === 0 && { borderColor: accent.main, borderWidth: 2 }]}>
      {position === 0 ? (
        <View style={[styles.top, { backgroundColor: accent.main }]}>
          <Text style={styles.topText}>⭐ Tu mejor opción</Text>
        </View>
      ) : null}
      <View style={styles.row}>
        <View style={[styles.iconWrap, { backgroundColor: withAlpha(accent.main, 0.16) }]}>
          <Text style={{ fontSize: 26 }}>{item.icon}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.name}>{item.name}</Text>
          <Text style={styles.tagline} numberOfLines={2}>
            {item.tagline}
          </Text>
        </View>
        <Text style={[styles.score, { color: accent.main }]} accessibilityLabel={`Afinidad ${score} de 100`}>
          {score}
        </Text>
      </View>
      <AnimatedBar pct={score} color={accent.main} height={8} delay={position * 90} style={{ marginTop: 10 }} />

      {item.reasons.length > 0 ? (
        <View style={{ gap: 4, marginTop: 12 }}>
          {item.reasons.map((r) => (
            <Text key={r} style={styles.reason}>{`✓ ${r}`}</Text>
          ))}
        </View>
      ) : null}
      {item.cautions.length > 0 ? (
        <View style={{ gap: 4, marginTop: 8 }}>
          {item.cautions.map((c) => (
            <Text key={c} style={styles.caution}>{`⚠️ ${c}`}</Text>
          ))}
        </View>
      ) : null}

      {available ? (
        <GradientButton title={`Ver ${item.name}`} variant={position === 0 ? "primary" : "ghost"} onPress={onPick} style={{ marginTop: 14 }} />
      ) : (
        <Text style={styles.soon}>Próximamente: aún no se puede elegir.</Text>
      )}
    </Animated.View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  card: { backgroundColor: colors.card, borderRadius: 20, padding: 16, overflow: "hidden" as const, borderWidth: 1, borderColor: colors.border, ...shadow },
  top: { marginHorizontal: -16, marginTop: -16, marginBottom: 12, paddingVertical: 6, paddingHorizontal: 16 },
  topText: { color: "#fff", fontSize: 12, fontWeight: "700" as const },
  row: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  iconWrap: { width: 50, height: 50, borderRadius: 16, alignItems: "center" as const, justifyContent: "center" as const },
  name: { fontSize: 16, fontWeight: "800" as const, color: colors.text },
  tagline: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  score: { fontSize: 26, fontWeight: "800" as const },
  reason: { fontSize: 13, color: colors.text, lineHeight: 18 },
  caution: { fontSize: 12, color: colors.warn, lineHeight: 17 },
  soon: { fontSize: 12, color: colors.textFaint, marginTop: 12, fontStyle: "italic" as const },
});
