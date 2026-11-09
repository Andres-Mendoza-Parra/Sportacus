import React from "react";
import { Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { formatDate, todayWeekday, weekdayName } from "../../constants/labels";
import { SessionView } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import PressableScale from "../ui/PressableScale";

interface Props {
  session: SessionView;
  index: number;
  /** Solo la semana en curso marca "Hoy". */
  isCurrentWeek: boolean;
  onPress: () => void;
}

/** Tarjeta de una sesión del plan: día, título, foco, minutos y estado. */
export default function PlanSessionCard({ session, index, isCurrentWeek, onPress }: Props) {
  const styles = useThemedStyles(createStyles);
  const isToday = isCurrentWeek && session.weekday === todayWeekday() && !session.done;

  return (
    <Animated.View entering={FadeInDown.delay(index * 60).duration(350)}>
      <PressableScale
        onPress={onPress}
        style={[styles.card, isToday && styles.cardToday, session.done && styles.cardDone]}
        accessibilityRole="button"
        accessibilityLabel={`${weekdayName(session.weekday)}: ${session.title}${session.done ? ", hecha" : ""}`}
      >
        <View style={[styles.day, session.done && styles.dayDone]}>
          <Text style={[styles.dayText, session.done && styles.dayTextDone]}>{session.done ? "✓" : weekdayName(session.weekday, true)}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <View style={styles.row}>
            <Text style={styles.weekday}>{weekdayName(session.weekday)}</Text>
            {isToday ? <Text style={styles.today}>HOY</Text> : null}
          </View>
          <Text style={[styles.title, session.done && styles.titleDone]} numberOfLines={1}>
            {session.title}
          </Text>
          {session.focus ? (
            <Text style={styles.focus} numberOfLines={2}>
              {session.focus}
            </Text>
          ) : null}
          <Text style={styles.meta}>
            {[
              session.estMinutes ? `⏱️ ${session.estMinutes} min` : null,
              `${session.blocks.length} bloques`,
              session.done && session.lastDoneOn ? `Hecha el ${formatDate(session.lastDoneOn)}` : null,
            ]
              .filter(Boolean)
              .join(" · ")}
          </Text>
        </View>
        <Text style={styles.chevron}>›</Text>
      </PressableScale>
    </Animated.View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  card: {
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 12,
    backgroundColor: colors.card,
    borderRadius: 18,
    padding: 14,
    borderWidth: 1.5,
    borderColor: "transparent",
    ...shadow,
  },
  cardToday: { borderColor: colors.primary },
  cardDone: { opacity: 0.75 },
  day: { width: 48, height: 48, borderRadius: 14, backgroundColor: colors.primarySoft, alignItems: "center" as const, justifyContent: "center" as const },
  dayDone: { backgroundColor: colors.primary },
  dayText: { color: colors.primary, fontWeight: "800" as const, fontSize: 13 },
  dayTextDone: { color: colors.onPrimary, fontSize: 20 },
  row: { flexDirection: "row" as const, alignItems: "center" as const, gap: 8 },
  weekday: { fontSize: 11, color: colors.textFaint, fontWeight: "700" as const, textTransform: "uppercase" as const },
  today: { fontSize: 10, color: colors.onPrimary, backgroundColor: colors.primary, paddingHorizontal: 6, paddingVertical: 1, borderRadius: 6, fontWeight: "800" as const, overflow: "hidden" as const },
  title: { fontSize: 15, fontWeight: "700" as const, color: colors.text, marginTop: 2 },
  titleDone: { textDecorationLine: "line-through" as const },
  focus: { fontSize: 12, color: colors.textMuted, marginTop: 2, lineHeight: 17 },
  meta: { fontSize: 11, color: colors.textFaint, marginTop: 4 },
  chevron: { fontSize: 24, color: colors.textFaint },
});
