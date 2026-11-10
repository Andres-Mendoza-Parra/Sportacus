import { router } from "expo-router";
import { useMemo } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import AchievementsGrid from "../components/progress/AchievementsGrid";
import ScreenHeader from "../components/ui/ScreenHeader";
import { ScreenSkeleton } from "../components/ui/Skeleton";
import { EmptyState, ErrorState } from "../components/ui/States";
import { useWorld } from "../context/WorldContext";
import { useLoader } from "../hooks/useLoader";
import { AchievementView } from "../services/types";
import { fetchAllAchievements } from "../services/worldsService";
import { Theme, useThemedStyles, useTheme } from "../theme";

interface WorldGroup {
  key: string;
  name: string;
  icon: string;
  items: AchievementView[];
}

/** Agrupa por mundo conservando el orden del servidor (más reciente primero): el mundo con el logro más nuevo va arriba. */
function groupByWorld(list: AchievementView[]): WorldGroup[] {
  const groups = new Map<string, WorldGroup>();
  for (const a of list) {
    const key = a.worldKey ?? "otros";
    let group = groups.get(key);
    if (!group) {
      group = { key, name: a.worldName ?? (a.worldKey ? a.worldKey : "Otros"), icon: a.worldIcon ?? "🌍", items: [] };
      groups.set(key, group);
    }
    group.items.push(a);
  }
  return [...groups.values()];
}

/** Logros de todos los mundos, agrupados por mundo y con su fecha. */
export default function AchievementsScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const { version } = useWorld();
  const { data, error, loading, refreshing, reload } = useLoader(fetchAllAchievements, [version]);
  const groups = useMemo(() => groupByWorld(data ?? []), [data]);

  if (loading && !data) return <ScreenSkeleton cards={3} />;

  const total = data?.length ?? 0;

  return (
    <View style={styles.bg}>
      <ScreenHeader
        title="Mis logros"
        subtitle={data ? (total === 1 ? "1 logro desbloqueado" : `${total} logros desbloqueados`) : null}
        icon="🏅"
        fallback="/progress"
      />
      {!data ? (
        <ErrorState message={error ?? "No pudimos cargar tus logros."} onRetry={reload} />
      ) : (
        <ScrollView
          contentContainerStyle={{ padding: 16, paddingBottom: 32 + insets.bottom }}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={reload} tintColor={colors.primary} colors={[colors.primary]} />}
          showsVerticalScrollIndicator={false}
        >
          {groups.length === 0 ? (
            <EmptyState
              icon="🏅"
              title="Tu vitrina te espera"
              body="Completa sesiones, supera tests y mantén tu racha: cada logro que ganes en cualquier mundo aparecerá aquí."
              actionLabel="Ver mi plan"
              onAction={() => router.navigate("/plan")}
            />
          ) : (
            groups.map((g, i) => (
              <Animated.View key={g.key} entering={FadeInDown.delay(i * 70).duration(350)} style={{ marginBottom: 20 }}>
                <View style={styles.groupHead} accessibilityRole="header">
                  <Text style={{ fontSize: 22 }}>{g.icon}</Text>
                  <Text style={styles.groupName}>{g.name}</Text>
                  <Text style={styles.count}>{g.items.length}</Text>
                </View>
                <AchievementsGrid achievements={g.items} />
              </Animated.View>
            ))
          )}
        </ScrollView>
      )}
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  groupHead: { flexDirection: "row" as const, alignItems: "center" as const, gap: 8, marginBottom: 10 },
  groupName: { flex: 1, fontSize: 16, fontWeight: "800" as const, color: colors.text },
  count: {
    fontSize: 12,
    fontWeight: "800" as const,
    color: colors.primary,
    backgroundColor: colors.primarySoft,
    paddingHorizontal: 9,
    paddingVertical: 3,
    borderRadius: 999,
    overflow: "hidden" as const,
  },
});
