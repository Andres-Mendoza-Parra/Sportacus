import React from "react";
import { RefreshControl, ScrollView, View } from "react-native";

import WorldHeroHeader from "../../components/home/WorldHeroHeader";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { ErrorState } from "../../components/ui/States";
import WidgetList from "../../components/widgets/WidgetList";
import { useWorld } from "../../context/WorldContext";
import { useLoader, useReloadOnFocus } from "../../hooks/useLoader";
import { fetchDashboard } from "../../services/worldService";
import { useTheme } from "../../theme";

/** Inicio del mundo activo: cabecera con racha y widgets dirigidos por el servidor. */
export default function HomeScreen() {
  const { colors } = useTheme();
  const { version } = useWorld();
  const { data, error, loading, refreshing, reload } = useLoader(fetchDashboard, [version]);
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
      <WorldHeroHeader greeting={data.greeting} world={data.world} streak={data.streak} />
      <View style={{ padding: 16 }}>
        <WidgetList widgets={data.widgets} />
      </View>
    </ScrollView>
  );
}
