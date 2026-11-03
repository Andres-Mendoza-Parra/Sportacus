import { Image } from "expo-image";
import { router } from "expo-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { ActivityIndicator, FlatList, ScrollView, Text, TextInput, View } from "react-native";

import { useToast } from "../../components/ui/Feedback";
import GradientHeader from "../../components/ui/GradientHeader";
import PressableScale from "../../components/ui/PressableScale";
import { extractErrorMessage } from "../../services/api";
import {
  LibraryExercise,
  LibraryFilters,
  addFavorite,
  fetchLibraryFilters,
  removeFavorite,
  searchLibrary,
} from "../../services/libraryService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

const PAGE = 30;

export default function LibraryScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();

  const [filters, setFilters] = useState<LibraryFilters>({ muscles: [], difficulties: [], moduleName: "" });
  const [query, setQuery] = useState("");
  const [muscle, setMuscle] = useState<string | null>(null);
  const [difficulty, setDifficulty] = useState<number | null>(null);
  const [onlyFavorites, setOnlyFavorites] = useState(false);
  const [items, setItems] = useState<LibraryExercise[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [exhausted, setExhausted] = useState(false);
  const requestId = useRef(0);

  useEffect(() => {
    fetchLibraryFilters().then(setFilters).catch(() => {});
  }, []);

  const run = useCallback(
    async (offset: number) => {
      const id = ++requestId.current;
      try {
        const page = await searchLibrary({
          q: query.trim() || undefined,
          muscle: muscle ?? undefined,
          difficulty: difficulty ?? undefined,
          favoritesOnly: onlyFavorites || undefined,
          limit: PAGE,
          offset,
        });
        if (id !== requestId.current) return; // llegó una búsqueda más nueva
        setItems((prev) => (offset === 0 ? page : [...prev, ...page]));
        setExhausted(page.length < PAGE);
      } catch (error) {
        if (id === requestId.current) toast.error(extractErrorMessage(error));
      } finally {
        if (id === requestId.current) {
          setLoading(false);
          setLoadingMore(false);
        }
      }
    },
    [query, muscle, difficulty, onlyFavorites, toast],
  );

  // Busca con un pequeño retraso mientras el usuario escribe.
  useEffect(() => {
    const t = setTimeout(() => {
      setLoading(true);
      run(0);
    }, 300);
    return () => clearTimeout(t);
  }, [run]);

  function loadMore() {
    if (loadingMore || loading || exhausted) return;
    setLoadingMore(true);
    run(items.length);
  }

  /** Favoritos sincronizados en el servidor: optimista, con rollback si falla. */
  async function toggleFavorite(ex: LibraryExercise) {
    const next = !ex.favorite;
    const previous = items;
    setItems((prev) =>
      onlyFavorites && !next
        ? prev.filter((it) => it.id !== ex.id)
        : prev.map((it) => (it.id === ex.id ? { ...it, favorite: next } : it)),
    );
    try {
      await (next ? addFavorite(ex.id) : removeFavorite(ex.id));
    } catch (error) {
      setItems(previous);
      toast.error(extractErrorMessage(error));
    }
  }

  const shown = items;

  return (
    <View style={styles.bg}>
      <GradientHeader style={{ paddingBottom: 14 }}>
        <View style={styles.headTop}>
          <PressableScale style={styles.backBtn} onPress={() => (router.canGoBack() ? router.back() : router.replace("/routine"))}>
            <Text style={styles.backText}>←</Text>
          </PressableScale>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>{filters.moduleName ? `Biblioteca de ${filters.moduleName}` : "Biblioteca"}</Text>
            <Text style={styles.subtitle}>Ejercicios de tu módulo: explora y añade a tu rutina</Text>
          </View>
        </View>
        <TextInput
          style={styles.search}
          placeholder="Buscar ejercicio…"
          placeholderTextColor="rgba(255,255,255,0.6)"
          value={query}
          onChangeText={setQuery}
          autoCorrect={false}
        />
      </GradientHeader>

      <View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
          <Chip label="⭐ Favoritos" active={onlyFavorites} onPress={() => setOnlyFavorites((v) => !v)} />
          {filters.difficulties.map((d, i) => (
            <Chip key={d} label={d} active={difficulty === i + 1} onPress={() => setDifficulty(difficulty === i + 1 ? null : i + 1)} />
          ))}
        </ScrollView>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
          {filters.muscles.map((m) => (
            <Chip key={m} label={m} active={muscle === m} onPress={() => setMuscle(muscle === m ? null : m)} />
          ))}
        </ScrollView>
      </View>

      {loading && shown.length === 0 ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={shown}
          keyExtractor={(e) => e.id}
          contentContainerStyle={{ padding: 16, gap: 10 }}
          onEndReached={loadMore}
          onEndReachedThreshold={0.5}
          ListEmptyComponent={
            <Text style={styles.empty}>
              {onlyFavorites
                ? "Aún no tienes favoritos."
                : `No encontramos ejercicios de ${filters.moduleName || "tu módulo"} con esos filtros.`}
            </Text>
          }
          ListFooterComponent={loadingMore ? <ActivityIndicator color={colors.primary} /> : undefined}
          renderItem={({ item }) => (
            <PressableScale style={styles.row} onPress={() => router.push({ pathname: "/routine/[id]", params: { id: item.id, lib: "1" } })}>
              {item.imageUrl ? (
                <Image source={{ uri: item.imageUrl }} style={styles.thumb} contentFit="cover" cachePolicy="disk" />
              ) : (
                <View style={[styles.thumb, styles.thumbEmpty]}>
                  <Text style={{ fontSize: 26 }}>{item.icon}</Text>
                </View>
              )}
              <View style={{ flex: 1 }}>
                <Text style={styles.name} numberOfLines={2}>{item.name}</Text>
                <Text style={styles.meta}>{[item.muscleGroup, item.difficultyTag].join(" · ")}</Text>
                {item.equipment ? <Text style={styles.meta} numberOfLines={1}>🛠 {item.equipment}</Text> : null}
              </View>
              <PressableScale onPress={() => toggleFavorite(item)} style={{ padding: 6 }}>
                <Text style={{ fontSize: 20 }}>{item.favorite ? "⭐" : "☆"}</Text>
              </PressableScale>
            </PressableScale>
          )}
        />
      )}
    </View>
  );
}

function Chip({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  const styles = useThemedStyles(createStyles);
  return (
    <PressableScale style={[styles.chip, active && styles.chipActive]} onPress={onPress} scaleTo={0.94}>
      <Text style={[styles.chipText, active && styles.chipTextActive]}>{label}</Text>
    </PressableScale>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  headTop: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  backBtn: { width: 38, height: 38, borderRadius: 19, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center" as const, justifyContent: "center" as const },
  backText: { color: "#fff", fontSize: 18 },
  title: { color: "#fff", fontSize: 22, fontWeight: "800" as const },
  subtitle: { color: "rgba(255,255,255,0.8)", fontSize: 13 },
  search: { marginTop: 14, backgroundColor: "rgba(255,255,255,0.18)", borderRadius: 14, paddingVertical: 10, paddingHorizontal: 14, color: "#fff", fontSize: 14 },
  chips: { paddingHorizontal: 16, paddingTop: 10, gap: 8 },
  chip: { paddingVertical: 7, paddingHorizontal: 14, borderRadius: 18, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { color: colors.textMuted, fontSize: 12, fontWeight: "600" as const },
  chipTextActive: { color: "#fff" },
  row: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, backgroundColor: colors.card, borderRadius: 16, padding: 10, borderWidth: 1, borderColor: colors.border },
  thumb: { width: 64, height: 64, borderRadius: 12, backgroundColor: colors.cardAlt },
  thumbEmpty: { alignItems: "center" as const, justifyContent: "center" as const },
  name: { color: colors.text, fontSize: 14, fontWeight: "700" as const },
  meta: { color: colors.textMuted, fontSize: 12, marginTop: 2 },
  empty: { color: colors.textMuted, textAlign: "center" as const, marginTop: 40 },
});
