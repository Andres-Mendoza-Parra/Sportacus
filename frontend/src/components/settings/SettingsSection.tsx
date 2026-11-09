import React from "react";
import { Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { Theme, useThemedStyles } from "../../theme";
import PressableScale from "../ui/PressableScale";

/** Sección con título en mayúsculas y tarjeta. */
export function SettingsSection({ title, delay, children }: { title: string; delay: number; children: React.ReactNode }) {
  const styles = useThemedStyles(createStyles);
  return (
    <Animated.View entering={FadeInDown.delay(delay).duration(400)} style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <View style={styles.card}>{children}</View>
    </Animated.View>
  );
}

interface SegmentProps<T extends string> {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}

/** Control segmentado (una opción activa). */
export function Segment<T extends string>({ options, value, onChange }: SegmentProps<T>) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={styles.segment}>
      {options.map((opt) => {
        const active = value === opt.value;
        return (
          <PressableScale
            key={opt.value}
            style={[styles.segmentItem, active && styles.segmentItemActive]}
            onPress={() => onChange(opt.value)}
            scaleTo={0.95}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
          >
            <Text style={[styles.segmentText, active && styles.segmentTextActive]}>{opt.label}</Text>
          </PressableScale>
        );
      })}
    </View>
  );
}

export const createStyles = ({ colors, shadow }: Theme) => ({
  section: { marginHorizontal: 16, marginTop: 20 },
  sectionTitle: { fontSize: 11, color: colors.textFaint, fontWeight: "700" as const, textTransform: "uppercase" as const, letterSpacing: 0.8, marginBottom: 8 },
  card: { backgroundColor: colors.card, borderRadius: 18, padding: 16, ...shadow },
  label: { fontSize: 12, color: colors.textMuted, fontWeight: "600" as const, marginBottom: 8 },
  segment: { flexDirection: "row" as const, backgroundColor: colors.cardAlt, borderRadius: 14, padding: 3 },
  segmentItem: { flex: 1, paddingVertical: 9, borderRadius: 11, alignItems: "center" as const },
  segmentItemActive: { backgroundColor: colors.primary },
  segmentText: { fontSize: 12, fontWeight: "600" as const, color: colors.textMuted },
  segmentTextActive: { color: "#fff" },
  rowTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  rowSub: { fontSize: 12, color: colors.textMuted, marginTop: 2, lineHeight: 17 },
  divider: { height: 1, backgroundColor: colors.border, marginVertical: 18 },
});
