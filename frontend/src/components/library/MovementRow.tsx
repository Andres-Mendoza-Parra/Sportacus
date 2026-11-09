import { Image } from "expo-image";
import React from "react";
import { Pressable, Text, View } from "react-native";

import { humanize } from "../../constants/labels";
import { MovementCard } from "../../services/types";
import { Theme, useTheme, useThemedStyles } from "../../theme";
import PressableScale from "../ui/PressableScale";

interface Props {
  item: MovementCard;
  onPress: () => void;
  onToggleFavorite: () => void;
}

/** Puntos de nivel (1–5). */
export function LevelDots({ level }: { level: number | null | undefined }) {
  const { colors } = useTheme();
  if (!level) return null;
  return (
    <View style={{ flexDirection: "row", gap: 3 }} accessibilityLabel={`Nivel ${level} de 5`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <View key={n} style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: n <= level ? colors.primary : colors.track }} />
      ))}
    </View>
  );
}

/** Fila de la biblioteca: miniatura, nombre, patrón, músculos, equipo y favorito. */
export default function MovementRow({ item, onPress, onToggleFavorite }: Props) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const muscles = item.muscles.map(humanize).join(", ");
  const equipment = item.equipment.length ? item.equipment.map(humanize).join(", ") : "Sin equipo";

  return (
    <PressableScale style={styles.row} onPress={onPress} accessibilityRole="button" accessibilityLabel={`Ver ${item.name}`}>
      {item.imageUrl ? (
        <Image source={{ uri: item.imageUrl }} style={styles.thumb} contentFit="cover" transition={150} accessibilityIgnoresInvertColors />
      ) : (
        <View style={[styles.thumb, styles.thumbEmpty]}>
          <Text style={{ fontSize: 22 }}>🏃</Text>
        </View>
      )}
      <View style={{ flex: 1, gap: 3 }}>
        <Text style={styles.name} numberOfLines={2}>
          {item.name}
        </Text>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
          {item.pattern ? <Text style={styles.pattern}>{humanize(item.pattern)}</Text> : null}
          <LevelDots level={item.level} />
        </View>
        {muscles ? (
          <Text style={styles.meta} numberOfLines={1}>
            💪 {muscles}
          </Text>
        ) : null}
        <Text style={styles.meta} numberOfLines={1}>
          🧰 {equipment}
        </Text>
        {!item.equipmentOk ? <Text style={styles.warn}>Requiere equipo que no tienes</Text> : null}
      </View>
      <Pressable
        onPress={onToggleFavorite}
        hitSlop={10}
        style={styles.fav}
        accessibilityRole="button"
        accessibilityLabel={item.favorite ? `Quitar ${item.name} de favoritos` : `Añadir ${item.name} a favoritos`}
        accessibilityState={{ selected: item.favorite }}
      >
        <Text style={{ fontSize: 22, color: item.favorite ? colors.warn : colors.textFaint }}>{item.favorite ? "★" : "☆"}</Text>
      </Pressable>
    </PressableScale>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  row: { flexDirection: "row" as const, gap: 12, alignItems: "center" as const, backgroundColor: colors.card, borderRadius: 16, padding: 12, ...shadow },
  thumb: { width: 64, height: 64, borderRadius: 12, backgroundColor: colors.cardAlt },
  thumbEmpty: { alignItems: "center" as const, justifyContent: "center" as const },
  name: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  pattern: { fontSize: 11, fontWeight: "600" as const, color: colors.primary },
  meta: { fontSize: 11, color: colors.textMuted },
  warn: { fontSize: 11, color: colors.warn, fontWeight: "600" as const },
  fav: { width: 44, height: 44, alignItems: "center" as const, justifyContent: "center" as const },
});
