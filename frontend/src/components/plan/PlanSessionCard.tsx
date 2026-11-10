import { router } from "expo-router";
import React, { useState } from "react";
import { Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { blockTypeLabel, formatDate, todayWeekday, weekdayName } from "../../constants/labels";
import { SessionView } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import BlockThumb from "../session/BlockThumb";
import { prescriptionSummary } from "../session/format";
import PressableScale from "../ui/PressableScale";

interface Props {
  session: SessionView;
  index: number;
  /** Solo la semana en curso marca "Hoy". */
  isCurrentWeek: boolean;
  onPress: () => void;
}

/** Tarjeta de una sesión del plan: día, título, foco, minutos, estado y sus ejercicios (cada uno abre su detalle). */
export default function PlanSessionCard({ session, index, isCurrentWeek, onPress }: Props) {
  const styles = useThemedStyles(createStyles);
  const isToday = isCurrentWeek && session.weekday === todayWeekday() && !session.done;
  const [open, setOpen] = useState(false);
  const blocks = [...session.blocks].sort((a, b) => a.orderIndex - b.orderIndex);

  return (
    <Animated.View entering={FadeInDown.delay(index * 60).duration(350)} style={[styles.card, isToday && styles.cardToday]}>
      <PressableScale
        onPress={onPress}
        style={[styles.main, session.done && styles.cardDone]}
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
              `${session.blocks.length} ${session.blocks.length === 1 ? "bloque" : "bloques"}`,
              session.done && session.lastDoneOn ? `Hecha el ${formatDate(session.lastDoneOn)}` : null,
            ]
              .filter(Boolean)
              .join(" · ")}
          </Text>
        </View>
        <Text style={styles.chevron}>›</Text>
      </PressableScale>
      {blocks.length > 0 ? (
        <PressableScale
          onPress={() => setOpen((o) => !o)}
          style={styles.toggle}
          accessibilityRole="button"
          accessibilityLabel={open ? "Ocultar ejercicios" : `Ver los ${blocks.length} ejercicios de ${session.title}`}
          accessibilityState={{ expanded: open }}
        >
          <Text style={styles.toggleText}>{open ? "Ocultar ejercicios ▲" : `Ver ejercicios (${blocks.length}) ▼`}</Text>
        </PressableScale>
      ) : null}
      {open
        ? blocks.map((block, i) => (
            <PressableScale
              key={block.id}
              onPress={() => router.push({ pathname: "/block/[id]", params: { id: block.id } })}
              style={styles.block}
              accessibilityRole="button"
              accessibilityLabel={`${block.title}. ${prescriptionSummary(block.prescription)}`}
              accessibilityHint="Abre por qué y cómo hacer este ejercicio"
            >
              <BlockThumb block={block} size={40} index={i + 1} />
              <View style={{ flex: 1 }}>
                <Text style={styles.blockType}>{blockTypeLabel(block.blockType)}</Text>
                <Text style={styles.blockTitle} numberOfLines={1}>
                  {block.title}
                </Text>
                <Text style={styles.blockDose} numberOfLines={1}>
                  {prescriptionSummary(block.prescription)}
                </Text>
                {block.summary ? (
                  <Text style={styles.focus} numberOfLines={1}>
                    💡 {block.summary}
                  </Text>
                ) : null}
              </View>
              <Text style={[styles.chevron, { fontSize: 20 }]}>›</Text>
            </PressableScale>
          ))
        : null}
    </Animated.View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  card: {
    backgroundColor: colors.card,
    borderRadius: 18,
    padding: 14,
    borderWidth: 1.5,
    borderColor: "transparent",
    ...shadow,
  },
  cardToday: { borderColor: colors.primary },
  main: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  toggle: { marginTop: 10, paddingTop: 10, borderTopWidth: 1, borderTopColor: colors.border, alignItems: "center" as const },
  toggleText: { fontSize: 12, fontWeight: "700" as const, color: colors.primary },
  block: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, paddingVertical: 8, marginTop: 4 },
  blockType: { fontSize: 10, color: colors.textFaint, fontWeight: "700" as const, textTransform: "uppercase" as const },
  blockTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  blockDose: { fontSize: 12, color: colors.primary, fontWeight: "600" as const },
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
