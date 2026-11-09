import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { openSource } from "../../components/assessments/shared";
import FeedbackButtons, { Reaction } from "../../components/library/FeedbackButtons";
import MovementMedia from "../../components/library/MovementMedia";
import { Attribution, MovementBody } from "../../components/library/MovementSections";
import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import ScreenHeader, { HeaderIconButton } from "../../components/ui/ScreenHeader";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { ErrorState } from "../../components/ui/States";
import { useLoader } from "../../hooks/useLoader";
import { extractErrorMessage } from "../../services/api";
import { fetchMovement, sendMovementFeedback, setFavorite } from "../../services/worldService";
import { useTheme } from "../../theme";

/** Ficha de un movimiento: medios, técnica, seguridad, atribución, favorito y valoración. */
export default function MovementDetailScreen() {
  const { key } = useLocalSearchParams<{ key: string }>();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const { data: detail, error, loading, reload, setData } = useLoader(() => fetchMovement(String(key)), [key]);
  const [reaction, setReaction] = useState<Reaction | null>(null);
  const [sending, setSending] = useState(false);

  if (loading) return <ScreenSkeleton cards={4} />;

  if (!detail) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <ScreenHeader title="Ejercicio" fallback="/library" />
        <ErrorState message={error ?? "No encontramos este ejercicio."} onRetry={reload} />
      </View>
    );
  }

  async function toggleFavorite() {
    if (!detail) return;
    const next = !detail.favorite;
    setData((prev) => ({ ...(prev ?? detail), favorite: next }));
    try {
      await setFavorite(detail.key, next);
      toast.success(next ? "Guardado en favoritos ★" : "Quitado de favoritos");
    } catch (e) {
      setData((prev) => ({ ...(prev ?? detail), favorite: !next }));
      toast.error(extractErrorMessage(e));
    }
  }

  async function react(value: Reaction) {
    if (!detail) return;
    setSending(true);
    try {
      await sendMovementFeedback(detail.key, value);
      setReaction(value);
      toast.success("¡Gracias! Lo tendremos en cuenta en tus próximos planes.");
    } catch (e) {
      toast.error(extractErrorMessage(e));
    } finally {
      setSending(false);
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader
        title={detail.name}
        fallback="/library"
        right={
          <HeaderIconButton
            icon={detail.favorite ? "★" : "☆"}
            label={detail.favorite ? "Quitar de favoritos" : "Añadir a favoritos"}
            onPress={toggleFavorite}
          />
        }
      />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: 32 + insets.bottom }}>
        <MovementMedia imageUrls={detail.imageUrls ?? []} muscleViews={detail.muscleViews ?? []} name={detail.name} />
        {detail.videoUrl ? <GradientButton title="▶ Ver video" variant="ghost" onPress={() => openSource(detail.videoUrl as string)} /> : null}
        <MovementBody detail={{ ...detail, steps: detail.steps ?? [], cues: detail.cues ?? [], commonMistakes: detail.commonMistakes ?? [], muscles: detail.muscles ?? [], equipment: detail.equipment ?? [], cautions: detail.cautions ?? [] }} />
        <FeedbackButtons value={reaction} busy={sending} onChange={react} />
        <GradientButton
          title="🤖 Preguntar al asistente"
          variant="ghost"
          onPress={() => router.push({ pathname: "/chat", params: { prompt: `¿Cómo hago bien ${detail.name}?`, t: String(Date.now()) } })}
        />
        <Attribution detail={detail} />
      </ScrollView>
    </View>
  );
}
