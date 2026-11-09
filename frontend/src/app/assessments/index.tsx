import { router } from "expo-router";
import { RefreshControl, ScrollView, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import AssessmentCard from "../../components/assessments/AssessmentCard";
import NoticeBox from "../../components/ui/NoticeBox";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { SkeletonBlock } from "../../components/ui/Skeleton";
import { EmptyState, ErrorState } from "../../components/ui/States";
import { useWorld } from "../../context/WorldContext";
import { useLoader, useReloadOnFocus } from "../../hooks/useLoader";
import { fetchAssessments } from "../../services/worldService";
import { useTheme } from "../../theme";

/** Tests físicos del mundo activo, con su último resultado y cuándo toca repetirlos. */
export default function AssessmentsScreen() {
  const { colors } = useTheme();
  const { active, version } = useWorld();
  const { data, error, loading, refreshing, reload } = useLoader(fetchAssessments, [version]);
  useReloadOnFocus(reload);

  const items = [...(data ?? [])].sort((a, b) => Number(b.due) - Number(a.due) || a.definition.sortOrder - b.definition.sortOrder);
  const dueCount = items.filter((i) => i.due).length;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title="Tests" subtitle={active ? `${active.icon} ${active.name}` : "Mide tu progreso"} fallback="/progress" />
      <ScrollView
        contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: 40 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={reload} tintColor={colors.primary} />}
      >
        {loading ? (
          [0, 1, 2].map((i) => <SkeletonBlock key={i} height={120} radius={18} />)
        ) : error && !data ? (
          <ErrorState message={error} onRetry={reload} />
        ) : items.length === 0 ? (
          <EmptyState icon="🧪" title="Este mundo aún no tiene tests" body="Cuando haya tests disponibles los verás aquí." />
        ) : (
          <>
            {dueCount > 0 ? (
              <NoticeBox
                tone="info"
                body={dueCount === 1 ? "Tienes 1 test pendiente: repetirlo ajusta tu plan a tu nivel real." : `Tienes ${dueCount} tests pendientes: repetirlos ajusta tu plan a tu nivel real.`}
              />
            ) : null}
            {items.map((item, idx) => (
              <Animated.View key={item.definition.key} entering={FadeInDown.delay(idx * 60).duration(350)}>
                <AssessmentCard item={item} onPress={() => router.push({ pathname: "/assessments/[key]", params: { key: item.definition.key } })} />
              </Animated.View>
            ))}
          </>
        )}
      </ScrollView>
    </View>
  );
}
