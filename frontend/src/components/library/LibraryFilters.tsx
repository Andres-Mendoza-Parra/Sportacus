import React from "react";
import { Pressable, ScrollView, Text, TextInput, View } from "react-native";

import { humanize } from "../../constants/labels";
import { Theme, useTheme, useThemedStyles } from "../../theme";
import Chip from "../ui/Chip";

interface Props {
  query: string;
  onQuery: (q: string) => void;
  patterns: string[];
  pattern: string | null;
  onPattern: (p: string | null) => void;
  favorites: boolean;
  onFavorites: (v: boolean) => void;
}

/** Buscador + chips de patrón de movimiento + filtro de favoritos. */
export default function LibraryFilters({ query, onQuery, patterns, pattern, onPattern, favorites, onFavorites }: Props) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  return (
    <View style={{ gap: 10 }}>
      <View style={styles.search}>
        <Text style={{ fontSize: 15 }}>🔍</Text>
        <TextInput
          style={styles.input}
          value={query}
          onChangeText={onQuery}
          placeholder="Buscar ejercicio…"
          placeholderTextColor={colors.textFaint}
          returnKeyType="search"
          autoCorrect={false}
          accessibilityLabel="Buscar ejercicio"
        />
        {query ? (
          <Pressable onPress={() => onQuery("")} hitSlop={10} accessibilityRole="button" accessibilityLabel="Borrar búsqueda">
            <Text style={{ color: colors.textFaint, fontSize: 15 }}>✕</Text>
          </Pressable>
        ) : null}
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingRight: 8 }} keyboardShouldPersistTaps="handled">
        <Chip label="★ Favoritos" selected={favorites} onPress={() => onFavorites(!favorites)} />
        <Chip label="Todos" selected={pattern === null} onPress={() => onPattern(null)} />
        {patterns.map((p) => (
          <Chip key={p} label={humanize(p)} selected={pattern === p} onPress={() => onPattern(pattern === p ? null : p)} />
        ))}
      </ScrollView>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  search: {
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 8,
    backgroundColor: colors.card,
    borderRadius: 14,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: colors.border,
  },
  input: { flex: 1, paddingVertical: 11, fontSize: 14, color: colors.text },
});
