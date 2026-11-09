import { router } from "expo-router";
import { useState } from "react";
import { ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import FormScreen from "../../components/forms/FormScreen";
import RankingItem from "../../components/onboarding/RankingItem";
import { isSelectable } from "../../components/onboarding/WorldCardItem";
import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import NoticeBox from "../../components/ui/NoticeBox";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { EmptyState } from "../../components/ui/States";
import { extractErrorMessage } from "../../services/api";
import { Answers, DiscoveryResult, WorldCard } from "../../services/types";
import { fetchDiscoveryForm, fetchWorlds, runDiscovery } from "../../services/worldsService";
import { useTheme } from "../../theme";

/** Test de descubrimiento → ranking de mundos explicado (afinidad, razones y precauciones). */
export default function DiscoveryScreen() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const [result, setResult] = useState<DiscoveryResult | null>(null);
  const [catalog, setCatalog] = useState<WorldCard[]>([]);

  async function submit(answers: Answers) {
    try {
      const [ranking, worlds] = await Promise.all([runDiscovery(answers), fetchWorlds().catch(() => [] as WorldCard[])]);
      setCatalog(worlds);
      setResult(ranking);
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
  }

  function exit() {
    if (router.canGoBack()) router.back();
    else router.replace("/onboarding/choose");
  }

  if (!result) return <FormScreen load={fetchDiscoveryForm} submitLabel="Ver mi recomendación" onSubmit={submit} onExit={exit} />;

  /** Disponibilidad según la galería; si no se pudo cargar, se asume elegible. */
  const available = (key: string) => {
    const card = catalog.find((w) => w.key === key);
    return card ? isSelectable(card) : true;
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title="Tu recomendación" subtitle="Ordenamos los mundos según lo que nos contaste." onBack={() => setResult(null)} />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: insets.bottom + 24 }}>
        {result.notices.map((n) => (
          <NoticeBox key={n} tone="warn" body={n} />
        ))}
        {result.ranking.length === 0 ? (
          <EmptyState icon="🧭" title="No encontramos una recomendación" body="Mira la galería y elige el mundo que más te llame." />
        ) : (
          result.ranking.map((item, i) => (
            <RankingItem
              key={item.worldKey}
              item={item}
              position={i}
              available={available(item.worldKey)}
              onPick={() => router.push({ pathname: "/onboarding/world/[key]", params: { key: item.worldKey } })}
            />
          ))
        )}
        <GradientButton title="Ver todos los mundos" variant="ghost" onPress={() => router.push("/onboarding/gallery")} />
        <GradientButton title="Repetir el test" variant="ghost" onPress={() => setResult(null)} />
      </ScrollView>
    </View>
  );
}
