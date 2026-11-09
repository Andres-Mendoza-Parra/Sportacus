import { router } from "expo-router";
import { useEffect, useState } from "react";
import { FlatList, RefreshControl, View } from "react-native";

import LibraryFilters from "../components/library/LibraryFilters";
import MovementRow from "../components/library/MovementRow";
import { useToast } from "../components/ui/Feedback";
import ScreenHeader from "../components/ui/ScreenHeader";
import { SkeletonBlock } from "../components/ui/Skeleton";
import { EmptyState, ErrorState } from "../components/ui/States";
import { useWorld } from "../context/WorldContext";
import { useLoader, useReloadOnFocus } from "../hooks/useLoader";
import { extractErrorMessage } from "../services/api";
import { MovementList } from "../services/types";
import { fetchMovements, setFavorite } from "../services/worldService";
import { useTheme } from "../theme";

/** Biblioteca de movimientos del mundo activo: búsqueda, patrones y favoritos. */
export default function LibraryScreen() {
  const { colors } = useTheme();
  const toast = useToast();
  const { active, version } = useWorld();
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [pattern, setPattern] = useState<string | null>(null);
  const [favorites, setFavorites] = useState(false);
  const [patterns, setPatterns] = useState<string[]>([]);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(query), 350);
    return () => clearTimeout(t);
  }, [query]);

  const { data, error, loading, refreshing, reload, setData } = useLoader<MovementList>(
    () => fetchMovements({ q: debounced, pattern, favorites }),
    [debounced, pattern, favorites, version],
  );
  useReloadOnFocus(reload);

  // Los patrones se conservan aunque un filtro deje la lista vacía.
  if (data?.patterns.length && data.patterns !== patterns) setPatterns(data.patterns);

  async function toggleFavorite(key: string, next: boolean) {
    const apply = (value: boolean) =>
      setData((prev) => {
        const base = prev ?? { items: [], patterns: [] };
        const items = base.items.map((m) => (m.key === key ? { ...m, favorite: value } : m));
        return { ...base, items: favorites && !value ? items.filter((m) => m.key !== key) : items };
      });
    apply(next);
    try {
      await setFavorite(key, next);
    } catch (e) {
      apply(!next);
      if (favorites && !next) reload();
      toast.error(extractErrorMessage(e));
    }
  }

  const items = data?.items ?? [];
  const filtered = !!debounced || !!pattern || favorites;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title="Biblioteca" subtitle={active ? `Movimientos de ${active.name}` : "Movimientos"} icon={active?.icon} fallback="/account" />
      <FlatList
        data={loading || (error && !data) ? [] : items}
        keyExtractor={(m) => m.key}
        contentContainerStyle={{ padding: 16, gap: 10, paddingBottom: 40 }}
        keyboardShouldPersistTaps="handled"
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={reload} tintColor={colors.primary} />}
        ListHeaderComponent={
          <View style={{ marginBottom: 6 }}>
            <LibraryFilters
              query={query}
              onQuery={setQuery}
              patterns={patterns}
              pattern={pattern}
              onPattern={setPattern}
              favorites={favorites}
              onFavorites={setFavorites}
            />
          </View>
        }
        ListEmptyComponent={
          loading ? (
            <View style={{ gap: 10 }}>
              {[0, 1, 2, 3].map((i) => (
                <SkeletonBlock key={i} height={88} radius={16} />
              ))}
            </View>
          ) : error && !data ? (
            <ErrorState message={error} onRetry={reload} />
          ) : favorites && !debounced && !pattern ? (
            <EmptyState icon="☆" title="Aún no tienes favoritos" body="Toca la estrella de un ejercicio para guardarlo aquí." />
          ) : (
            <EmptyState icon="🔍" title={filtered ? "Sin resultados" : "La biblioteca está vacía"} body={filtered ? "Prueba con otra búsqueda o quita los filtros." : undefined} />
          )
        }
        renderItem={({ item }) => (
          <MovementRow
            item={item}
            onPress={() => router.push({ pathname: "/movement/[key]", params: { key: item.key } })}
            onToggleFavorite={() => toggleFavorite(item.key, !item.favorite)}
          />
        )}
      />
    </View>
  );
}
