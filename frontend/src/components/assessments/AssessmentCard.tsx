import React from "react";
import { Text, View } from "react-native";

import { formatDate } from "../../constants/labels";
import { AssessmentView } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import PressableScale from "../ui/PressableScale";
import { formatValue, openSource } from "./shared";

interface Props {
  item: AssessmentView;
  onPress: () => void;
}

/** Tarjeta de un test del mundo activo: último resultado, vencimiento y fuente. */
export default function AssessmentCard({ item, onPress }: Props) {
  const styles = useThemedStyles(createStyles);
  const { definition: d } = item;
  const hasValue = item.lastValue != null;

  return (
    <PressableScale style={styles.card} onPress={onPress} accessibilityRole="button" accessibilityLabel={`Abrir test ${d.name}`}>
      <View style={styles.row}>
        <View style={{ flex: 1 }}>
          <Text style={styles.name}>
            {d.name}
            {d.safetyNote ? <Text accessibilityLabel="Tiene aviso de seguridad"> ⚠️</Text> : null}
          </Text>
          <Text style={styles.desc} numberOfLines={2}>
            {d.description}
          </Text>
        </View>
        {item.due ? (
          <View style={[styles.badge, styles.badgeDue]}>
            <Text style={styles.badgeDueText}>{hasValue ? "Toca repetirlo" : "Pendiente"}</Text>
          </View>
        ) : item.daysUntilDue != null ? (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>En {item.daysUntilDue} días</Text>
          </View>
        ) : null}
      </View>

      <View style={styles.footer}>
        {hasValue ? (
          <Text style={styles.last}>
            Último: <Text style={styles.lastValue}>{formatValue(item.lastValue, d.unit)}</Text>
            {item.lastClassification ? ` · ${item.lastClassification}` : ""}
            {item.lastAt ? ` · ${formatDate(item.lastAt)}` : ""}
          </Text>
        ) : (
          <Text style={styles.last}>Aún no lo has hecho</Text>
        )}
      </View>
      {item.sourceName ? (
        <Text
          style={[styles.source, item.sourceUrl ? styles.sourceLink : null]}
          onPress={item.sourceUrl ? () => openSource(item.sourceUrl as string) : undefined}
          accessibilityRole={item.sourceUrl ? "link" : "text"}
          numberOfLines={1}
        >
          Fuente: {item.sourceName}
        </Text>
      ) : null}
    </PressableScale>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  card: { backgroundColor: colors.card, borderRadius: 18, padding: 16, ...shadow },
  row: { flexDirection: "row" as const, gap: 10, alignItems: "flex-start" as const },
  name: { fontSize: 15, fontWeight: "700" as const, color: colors.text },
  desc: { fontSize: 12, color: colors.textMuted, marginTop: 3, lineHeight: 17 },
  badge: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 999, backgroundColor: colors.cardAlt },
  badgeText: { fontSize: 11, fontWeight: "600" as const, color: colors.textMuted },
  badgeDue: { backgroundColor: colors.primarySoft },
  badgeDueText: { fontSize: 11, fontWeight: "700" as const, color: colors.primary },
  footer: { marginTop: 10, paddingTop: 10, borderTopWidth: 1, borderTopColor: colors.border },
  last: { fontSize: 12, color: colors.textMuted },
  lastValue: { fontWeight: "700" as const, color: colors.text },
  source: { fontSize: 11, color: colors.textFaint, marginTop: 6 },
  sourceLink: { textDecorationLine: "underline" as const },
});
