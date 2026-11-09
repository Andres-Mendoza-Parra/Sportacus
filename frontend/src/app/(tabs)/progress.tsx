import { router } from "expo-router";
import React from "react";
import { RefreshControl, ScrollView, View } from "react-native";

import WorldHeroHeader from "../../components/home/WorldHeroHeader";
import AchievementsGrid from "../../components/progress/AchievementsGrid";
import LinkCard from "../../components/progress/LinkCard";
import RecentSessions from "../../components/progress/RecentSessions";
import { SectionTitle } from "../../components/ui/Card";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { EmptyState, ErrorState } from "../../components/ui/States";
import WidgetList from "../../components/widgets/WidgetList";
import { useWorld } from "../../context/WorldContext";
import { useLoader, useReloadOnFocus } from "../../hooks/useLoader";
import { fetchProgress } from "../../services/worldService";
import { useTheme } from "../../theme";

/** Progreso del mundo activo: métricas históricas, logros, sesiones recientes y acceso a los tests. */
export default function ProgressScreen() {
  const { colors } = useTheme();
  const { version } = useWorld();
  const { data, error, loading, refreshing, reload } = useLoader(fetchProgress, [version]);
  useReloadOnFocus(reload);

  if (loading && !data) return <ScreenSkeleton cards={4} />;
  if (!data) {
    return <ErrorState message={error ?? "Intenta de nuevo."} onRetry={reload} style={{ flex: 1, justifyContent: "center", backgroundColor: colors.bg }} />;
  }

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingBottom: 32 }}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={reload} tintColor={colors.primary} colors={[colors.primary]} />}
      showsVerticalScrollIndicator={false}
    >
      <WorldHeroHeader greeting="Tu progreso" world={data.world} streak={data.streak} />
      <View style={{ padding: 16 }}>
        <LinkCard
          icon="🧪"
          title="Tests de tu mundo"
          body="Mide tu nivel y compáralo con tus marcas anteriores."
          onPress={() => router.push("/assessments")}
          highlight
        />

        <SectionTitle>Métricas</SectionTitle>
        {data.widgets.length > 0 ? (
          <WidgetList widgets={data.widgets} />
        ) : (
          <EmptyState icon="📊" title="Sin métricas todavía" body="Registra tus primeras sesiones y aquí verás cómo evolucionas." />
        )}

        <SectionTitle>Logros</SectionTitle>
        <AchievementsGrid achievements={data.achievements} />

        <SectionTitle>Sesiones recientes</SectionTitle>
        <RecentSessions sessions={data.recentSessions} />

        <View style={{ height: 14 }} />
        <LinkCard icon="📚" title="Biblioteca" body="Técnica, variantes y favoritos de tu mundo." onPress={() => router.push("/library")} />
      </View>
    </ScrollView>
  );
}
