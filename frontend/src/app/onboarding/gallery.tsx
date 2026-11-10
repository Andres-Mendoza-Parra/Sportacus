import { router } from "expo-router";
import { useState } from "react";
import { RefreshControl, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import WorldCardItem, { isSelectable } from "../../components/onboarding/WorldCardItem";
import { useConfirm, useToast } from "../../components/ui/Feedback";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { SkeletonBlock } from "../../components/ui/Skeleton";
import { ErrorState } from "../../components/ui/States";
import { useWorld } from "../../context/WorldContext";
import { useLoader } from "../../hooks/useLoader";
import { extractErrorMessage } from "../../services/api";
import { WorldCard } from "../../services/types";
import { fetchWorlds } from "../../services/worldsService";
import { useTheme } from "../../theme";

/** Galería de mundos: los disponibles se abren en detalle; los inscritos se pueden activar. */
export default function GalleryScreen() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const confirm = useConfirm();
  const { activate } = useWorld();
  const worlds = useLoader(fetchWorlds);
  const [busy, setBusy] = useState(false);

  async function open(world: WorldCard) {
    if (world.active) {
      router.replace("/home");
      return;
    }
    if (world.enrolled) {
      const ok = await confirm({
        title: `¿Cambiar a ${world.name}?`,
        message: "Ya está en tus mundos: tu plan y tu progreso siguen ahí. Toda la app se adaptará a este mundo.",
        confirmText: "Activar",
      });
      if (!ok || busy) return;
      setBusy(true);
      try {
        await activate(world.key);
        toast.success(`${world.icon} ${world.name} es ahora tu mundo activo`);
        router.replace("/home");
      } catch (error) {
        toast.error(extractErrorMessage(error));
      } finally {
        setBusy(false);
      }
      return;
    }
    if (!isSelectable(world)) return;
    router.push({ pathname: "/onboarding/world/[key]", params: { key: world.key } });
  }

  // Mi Base no se muestra en la galería (se vuelve a ella desde el selector de mundos).
  const sorted = [...(worlds.data ?? [])].filter((w) => w.key !== "base").sort((a, b) => Number(isSelectable(b)) - Number(isSelectable(a)));

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title="Explorar mundos" subtitle="Cada uno tiene su plan, su sesión y sus métricas." fallback="/onboarding/choose" />
      <ScrollView
        contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: insets.bottom + 24 }}
        refreshControl={<RefreshControl refreshing={worlds.refreshing} onRefresh={worlds.reload} tintColor={colors.primary} />}
      >
        {worlds.loading ? (
          [0, 1, 2].map((i) => <SkeletonBlock key={i} height={170} radius={20} />)
        ) : worlds.error && !worlds.data ? (
          <ErrorState message={worlds.error} onRetry={worlds.reload} />
        ) : (
          sorted.map((w) => <WorldCardItem key={w.key} world={w} onPress={() => open(w)} />)
        )}
      </ScrollView>
    </View>
  );
}
