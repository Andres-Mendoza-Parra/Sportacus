import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { TrackList } from "../../../components/onboarding/PlanPieces";
import WorldHero from "../../../components/onboarding/WorldHero";
import { isSelectable } from "../../../components/onboarding/WorldCardItem";
import { Card, SectionTitle } from "../../../components/ui/Card";
import { useToast } from "../../../components/ui/Feedback";
import GradientButton from "../../../components/ui/GradientButton";
import { ScreenSkeleton } from "../../../components/ui/Skeleton";
import { EmptyState, ErrorState } from "../../../components/ui/States";
import { useWorld } from "../../../context/WorldContext";
import { useLoader } from "../../../hooks/useLoader";
import { extractErrorMessage } from "../../../services/api";
import { fetchWorlds } from "../../../services/worldsService";
import { Theme, deriveAccent, useThemedStyles, useTheme } from "../../../theme";

/** Detalle de un mundo antes de elegirlo: descripción, puntos fuertes y rutas. */
export default function WorldDetailScreen() {
  const { key } = useLocalSearchParams<{ key: string }>();
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const worlds = useLoader(fetchWorlds, [key]);
  const toast = useToast();
  const { activate } = useWorld();
  const [activating, setActivating] = useState(false);

  if (worlds.loading) return <ScreenSkeleton cards={3} />;
  const world = worlds.data?.find((w) => w.key === key);
  if (!world) {
    return (
      <View style={{ flex: 1, justifyContent: "center", backgroundColor: colors.bg }}>
        {worlds.error ? (
          <ErrorState message={worlds.error} onRetry={worlds.reload} />
        ) : (
          <EmptyState icon="🌍" title="Este mundo no existe" actionLabel="Ver la galería" onAction={() => router.replace("/onboarding/gallery")} />
        )}
      </View>
    );
  }

  const accent = deriveAccent(world.color);
  const selectable = isSelectable(world);

  async function activateThis() {
    if (!world) return;
    setActivating(true);
    try {
      await activate(world.key);
      toast.success(`${world.icon} ${world.name} es ahora tu mundo activo`);
      router.dismissTo("/home");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setActivating(false);
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScrollView contentContainerStyle={{ paddingBottom: 24 }}>
        <WorldHero color={world.color} icon={world.icon} title={world.name} subtitle={world.tagline} />
        <View style={{ padding: 16 }}>
          <Card delay={60}>
            <Text style={styles.description}>{world.description}</Text>
          </Card>

          {world.highlights.length > 0 ? (
            <>
              <SectionTitle>Lo que te ofrece</SectionTitle>
              <Card delay={120} style={{ gap: 8 }}>
                {world.highlights.map((h) => (
                  <Text key={h} style={styles.highlight}>
                    <Text style={{ color: accent.main, fontWeight: "800" }}>✓ </Text>
                    {h}
                  </Text>
                ))}
              </Card>
            </>
          ) : null}

          {world.tracks.length > 0 ? (
            <>
              <SectionTitle>Rutas</SectionTitle>
              <Card delay={180}>
                <Text style={styles.muted}>Te ubicamos en la ruta que mejor encaje con tus respuestas.</Text>
                <View style={{ height: 12 }} />
                <TrackList tracks={world.tracks} color={accent.main} />
              </Card>
            </>
          ) : null}
        </View>
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: 16 + insets.bottom }]}>
        {world.active ? (
          <GradientButton title="Ir a mi mundo" onPress={() => router.dismissTo("/home")} />
        ) : world.enrolled ? (
          <View style={{ gap: 8 }}>
            <Text style={styles.muted}>Este mundo ya está en tus mundos: tu plan y tu progreso siguen ahí.</Text>
            <GradientButton title="Activar este mundo" onPress={activateThis} loading={activating} />
          </View>
        ) : selectable ? (
          <GradientButton title="Elegir este mundo" onPress={() => router.push({ pathname: "/onboarding/setup/[key]", params: { key: world.key } })} />
        ) : (
          <Text style={styles.muted}>Próximamente: este mundo aún no está disponible.</Text>
        )}
      </View>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  description: { fontSize: 14, color: colors.text, lineHeight: 21 },
  highlight: { fontSize: 13, color: colors.text, lineHeight: 19 },
  muted: { fontSize: 12, color: colors.textMuted, lineHeight: 17, textAlign: "center" as const },
  footer: { padding: 16, backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border },
});
