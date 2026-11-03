import { Image } from "expo-image";
import { LinearGradient } from "expo-linear-gradient";
import { router, useLocalSearchParams } from "expo-router";
import * as WebBrowser from "expo-web-browser";
import { useEffect, useState } from "react";
import { Pressable, ScrollView, Text, View, useWindowDimensions } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import CheckBox from "../../components/ui/CheckBox";
import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import PressableScale from "../../components/ui/PressableScale";
import RestTimer from "../../components/ui/RestTimer";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { extractErrorMessage } from "../../services/api";
import { addToRoutine, fetchLibraryDetail } from "../../services/libraryService";
import {
  ExerciseDetail,
  MuscleView,
  Reaction,
  fetchExerciseDetail,
  sendExerciseFeedback,
  setExerciseDone,
} from "../../services/routineService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

const MAX_WIDTH = 640;

/** Silueta del cuerpo con el/los músculo(s) resaltados encima; sobre fondo blanco para que se lean en modo oscuro. */
function MuscleDiagram({ view, size, styles }: { view: MuscleView; size: number; styles: ReturnType<typeof createStyles> }) {
  const dim = { width: size, height: size * 1.45 };
  return (
    <View style={[styles.muscleDiagram, dim]}>
      <Image source={{ uri: view.baseUrl }} style={[styles.muscleLayer, dim]} contentFit="contain" />
      {view.overlayUrls.map((url) => (
        <Image key={url} source={{ uri: url }} style={[styles.muscleLayer, dim]} contentFit="contain" />
      ))}
    </View>
  );
}

export default function ExerciseDetailScreen() {
  const { id, lib } = useLocalSearchParams<{ id: string; lib?: string }>();
  // lib=1: se está viendo un ejercicio de la biblioteca (aún no pertenece a la rutina).
  const isLibrary = lib === "1";
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const insets = useSafeAreaInsets();
  const { width: windowWidth } = useWindowDimensions();
  const heroWidth = Math.min(windowWidth, MAX_WIDTH);

  const [detail, setDetail] = useState<ExerciseDetail | null>(null);
  const [isLogging, setIsLogging] = useState(false);
  const [page, setPage] = useState(0);
  const [isAdding, setIsAdding] = useState(false);

  useEffect(() => {
    if (!id) return;
    (async () => {
      try {
        setDetail(isLibrary ? await fetchLibraryDetail(id) : await fetchExerciseDetail(id));
      } catch (error) {
        toast.error(extractErrorMessage(error));
        router.back();
      }
    })();
  }, [id, isLibrary, toast]);

  async function handleAddToRoutine() {
    if (!id) return;
    setIsAdding(true);
    try {
      await addToRoutine(id);
      toast.success("Añadido a tu rutina");
      router.replace("/routine");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setIsAdding(false);
    }
  }

  function askAssistantAbout() {
    if (!detail) return;
    router.push({
      pathname: "/chat",
      params: { prompt: `Explícame cómo hacer bien «${detail.name}», los errores comunes y cómo adaptarlo si me cuesta.`, t: String(Date.now()) },
    });
  }

  /** Marca o desmarca el ejercicio en el checklist (optimista, con rollback si falla). */
  async function handleToggleDone() {
    if (!id || !detail) return;
    const previous = detail.done;
    const next = !previous;
    setDetail({ ...detail, done: next });
    setIsLogging(true);
    try {
      const result = await setExerciseDone(id, next);
      setDetail((prev) => (prev ? { ...prev, done: result.done } : prev));
      if (result.done) toast.success("¡Hecho! Marcado en tu checklist ✅");
    } catch (error) {
      setDetail((prev) => (prev ? { ...prev, done: previous } : prev));
      toast.error(extractErrorMessage(error));
    } finally {
      setIsLogging(false);
    }
  }

  async function handleReaction(reaction: Reaction) {
    if (!id || !detail) return;
    const previous = detail.reaction;
    const next = previous === reaction ? null : reaction;
    setDetail({ ...detail, reaction: next });
    if (next === null) return;
    try {
      await sendExerciseFeedback(id, next);
      toast.success(next === "like" ? "¡Anotado! Te mostraremos más como este." : "Anotado. Evitaremos recomendártelo.");
    } catch (error) {
      setDetail((prev) => (prev ? { ...prev, reaction: previous } : prev));
      toast.error(extractErrorMessage(error));
    }
  }

  if (!detail) return <ScreenSkeleton cards={3} />;

  const hasImages = detail.imageUrls.length > 0;
  const steps = detail.instructions;

  return (
    <View style={styles.bg}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ alignItems: "center" }}>
        <View style={{ width: heroWidth }}>
          {hasImages ? (
            <View>
              <ScrollView
                horizontal
                pagingEnabled
                showsHorizontalScrollIndicator={false}
                onMomentumScrollEnd={(e) => setPage(Math.round(e.nativeEvent.contentOffset.x / heroWidth))}
              >
                {detail.imageUrls.map((url, idx) => (
                  <View key={url + idx} style={{ width: heroWidth, height: 260, backgroundColor: "#fff" }}>
                    <Image source={{ uri: url }} style={{ width: "100%", height: "100%" }} contentFit="contain" transition={250} />
                  </View>
                ))}
              </ScrollView>
              {detail.imageUrls.length > 1 ? (
                <View style={styles.dots}>
                  {detail.imageUrls.map((_, i) => (
                    <View key={i} style={[styles.dot, i === page && styles.dotActive]} />
                  ))}
                </View>
              ) : null}
            </View>
          ) : (
            <LinearGradient colors={colors.headerGradient} style={styles.heroFallback}>
              {detail.muscleViews.length > 0 ? (
                <View style={styles.muscleImages}>
                  {detail.muscleViews.map((view) => (
                    <MuscleDiagram key={view.baseUrl} view={view} size={110} styles={styles} />
                  ))}
                </View>
              ) : (
                <Text style={{ fontSize: 76 }}>🏋️</Text>
              )}
            </LinearGradient>
          )}

          <PressableScale style={[styles.backBtn, { top: insets.top + 10 }]} onPress={() => router.back()}>
            <Text style={styles.backBtnText}>←</Text>
          </PressableScale>
        </View>

        <View style={[styles.body, { width: heroWidth }]}>
          <Animated.View entering={FadeInDown.duration(400)}>
            <View style={{ flexDirection: "row", alignItems: "center", gap: 14 }}>
              <CheckBox checked={detail.done} onToggle={handleToggleDone} size={36} disabled={isLogging} />
              <Text style={[styles.name, { flex: 1 }, detail.done && { textDecorationLine: "line-through", opacity: 0.6 }]}>{detail.name}</Text>
            </View>
            <View style={styles.chipsRow}>
              {detail.muscleGroupTag ? <Chip text={detail.muscleGroupTag} bg={colors.primarySoft} color={colors.success} /> : null}
              {detail.difficultyTag ? <Chip text={detail.difficultyTag} bg={colors.warnSoft} color={colors.warn} /> : null}
              {detail.environmentTag ? <Chip text={detail.environmentTag} bg={colors.infoSoft} color={colors.info} /> : null}
            </View>
            {detail.detailLabel ? <Text style={styles.detailLabel}>{detail.detailLabel}</Text> : null}
          </Animated.View>

          {detail.description ? (
            <Block delay={80} title="📖 Descripción">
              <Text style={styles.paragraph}>{detail.description}</Text>
            </Block>
          ) : null}

          {detail.videoUrl ? (
            <Animated.View entering={FadeInDown.delay(100).duration(400)}>
              <PressableScale style={styles.videoBtn} onPress={() => WebBrowser.openBrowserAsync(detail.videoUrl!)}>
                <Text style={{ fontSize: 24 }}>▶️</Text>
                <View style={{ flex: 1 }}>
                  <Text style={styles.videoTitle}>Ver video demostrativo</Text>
                  <Text style={styles.videoSub}>Se abre en el navegador</Text>
                </View>
              </PressableScale>
            </Animated.View>
          ) : null}

          {steps.length > 0 ? (
            <Block delay={140} title="✅ Ejecución paso a paso">
              {steps.map((step, idx) => (
                <View key={idx} style={styles.stepItem}>
                  <View style={styles.stepNum}>
                    <Text style={styles.stepNumText}>{idx + 1}</Text>
                  </View>
                  <Text style={styles.stepText}>{step}</Text>
                </View>
              ))}
            </Block>
          ) : null}

          {detail.tips.length > 0 ? (
            <Block delay={180} title="💡 Consejos">
              {detail.tips.map((tip, idx) => (
                <Text key={idx} style={styles.tip}>
                  • {tip}
                </Text>
              ))}
            </Block>
          ) : null}

          {detail.musclesPrimary.length > 0 || detail.musclesSecondary.length > 0 ? (
            <Block delay={220} title="💪 Músculos trabajados">
              <View style={styles.musclesRow}>
                {detail.musclesPrimary.map((m) => (
                  <View key={m} style={[styles.muscleChip, styles.muscleChipPrimary]}>
                    <Text style={styles.muscleChipTextPrimary}>{m}</Text>
                  </View>
                ))}
                {detail.musclesSecondary.map((m) => (
                  <View key={m} style={styles.muscleChip}>
                    <Text style={styles.muscleChipText}>{m}</Text>
                  </View>
                ))}
              </View>
              {detail.muscleViews.length > 0 ? (
                <View style={[styles.muscleImages, { marginTop: 12 }]}>
                  {detail.muscleViews.map((view) => (
                    <MuscleDiagram key={view.baseUrl} view={view} size={120} styles={styles} />
                  ))}
                </View>
              ) : null}
            </Block>
          ) : null}

          {detail.equipment ? (
            <Block delay={260} title="🎒 Equipo necesario">
              <Text style={styles.paragraph}>{detail.equipment}</Text>
            </Block>
          ) : null}

          {detail.contraindications ? (
            <Block delay={300} title="⚠️ Consideraciones">
              <Text style={styles.paragraph}>{detail.contraindications}</Text>
            </Block>
          ) : null}

          <View style={{ marginBottom: 10 }}>
            <RestTimer suggested={Number(/Descansa (\d+) s/.exec(detail.tips.join(" "))?.[1] ?? 60)} />
          </View>

          <GradientButton title="🤖 Preguntar al asistente sobre este ejercicio" variant="ghost" onPress={askAssistantAbout} style={{ marginBottom: 10 }} />

          {isLibrary ? (
            <GradientButton title="➕ Añadir a mi rutina" onPress={handleAddToRoutine} loading={isAdding} style={{ marginTop: 6 }} />
          ) : null}

          {!isLibrary ? (
          <>
          <Animated.View entering={FadeInDown.delay(340).duration(400)} style={styles.feedbackRow}>
            <Text style={styles.feedbackLabel}>¿Te sirve este ejercicio?</Text>
            <Pressable
              onPress={() => handleReaction("like")}
              style={[styles.reactBtn, detail.reaction === "like" && styles.reactOn]}
            >
              <Text style={{ fontSize: 18 }}>👍</Text>
            </Pressable>
            <Pressable
              onPress={() => handleReaction("dislike")}
              style={[styles.reactBtn, detail.reaction === "dislike" && styles.reactOff]}
            >
              <Text style={{ fontSize: 18 }}>👎</Text>
            </Pressable>
          </Animated.View>

          <GradientButton
            title={detail.done ? "Desmarcar (aún no lo hice)" : "✓ Marcar como hecho"}
            variant={detail.done ? "ghost" : "primary"}
            onPress={handleToggleDone}
            loading={isLogging}
            style={{ marginTop: 6 }}
          />
          </>
          ) : null}

          {detail.mediaSource ? <Text style={styles.credit}>Imágenes y descripción: {detail.mediaSource}</Text> : null}
          <View style={{ height: 32 }} />
        </View>
      </ScrollView>
    </View>
  );
}

function Chip({ text, bg, color }: { text: string; bg: string; color: string }) {
  return (
    <View style={{ backgroundColor: bg, paddingHorizontal: 12, paddingVertical: 5, borderRadius: 10 }}>
      <Text style={{ color, fontSize: 12, fontWeight: "600" }}>{text}</Text>
    </View>
  );
}

function Block({ title, delay, children }: { title: string; delay: number; children: React.ReactNode }) {
  const styles = useThemedStyles(createStyles);
  return (
    <Animated.View entering={FadeInDown.delay(delay).duration(400)} style={styles.infoBlock}>
      <Text style={styles.infoBlockTitle}>{title}</Text>
      {children}
    </Animated.View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  heroFallback: { height: 230, alignItems: "center" as const, justifyContent: "center" as const },
  muscleImages: { flexDirection: "row" as const, gap: 12, alignItems: "center" as const, justifyContent: "center" as const },
  muscleDiagram: { backgroundColor: "#fff", borderRadius: 14, overflow: "hidden" as const },
  muscleLayer: { position: "absolute" as const, top: 0, left: 0 },
  dots: { position: "absolute" as const, bottom: 10, left: 0, right: 0, flexDirection: "row" as const, justifyContent: "center" as const, gap: 6 },
  dot: { width: 7, height: 7, borderRadius: 4, backgroundColor: "rgba(0,0,0,0.25)" },
  dotActive: { backgroundColor: colors.primary, width: 18 },
  backBtn: {
    position: "absolute" as const,
    left: 14,
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: "rgba(0,0,0,0.45)",
    alignItems: "center" as const,
    justifyContent: "center" as const,
  },
  backBtnText: { color: "#fff", fontSize: 18 },
  body: { padding: 16 },
  name: { fontSize: 22, fontWeight: "700" as const, color: colors.text, marginBottom: 10 },
  chipsRow: { flexDirection: "row" as const, gap: 8, flexWrap: "wrap" as const, marginBottom: 6 },
  detailLabel: { fontSize: 13, color: colors.textMuted, marginTop: 4, marginBottom: 14 },
  infoBlock: { backgroundColor: colors.card, borderRadius: 16, padding: 14, marginBottom: 10, ...shadow },
  infoBlockTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text, marginBottom: 10 },
  paragraph: { fontSize: 13, color: colors.textMuted, lineHeight: 20 },
  tip: { fontSize: 13, color: colors.textMuted, lineHeight: 20, marginBottom: 4 },
  stepItem: { flexDirection: "row" as const, gap: 10, alignItems: "flex-start" as const, marginBottom: 10 },
  stepNum: { width: 24, height: 24, borderRadius: 12, backgroundColor: colors.primary, alignItems: "center" as const, justifyContent: "center" as const, marginTop: 1 },
  stepNumText: { color: "#fff", fontSize: 12, fontWeight: "700" as const },
  stepText: { flex: 1, fontSize: 13, color: colors.textMuted, lineHeight: 20 },
  musclesRow: { flexDirection: "row" as const, gap: 8, flexWrap: "wrap" as const },
  muscleChip: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 8, backgroundColor: colors.cardAlt },
  muscleChipPrimary: { backgroundColor: colors.primarySoft },
  muscleChipText: { fontSize: 12, color: colors.textMuted },
  muscleChipTextPrimary: { fontSize: 12, color: colors.success, fontWeight: "600" as const },
  videoBtn: {
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 12,
    backgroundColor: colors.infoSoft,
    borderRadius: 16,
    padding: 14,
    marginBottom: 10,
  },
  videoTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  videoSub: { fontSize: 12, color: colors.textMuted, marginTop: 1 },
  feedbackRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 10, marginVertical: 8 },
  feedbackLabel: { flex: 1, fontSize: 13, color: colors.textMuted, fontWeight: "600" as const },
  reactBtn: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 12,
    backgroundColor: colors.cardAlt,
    borderWidth: 1.5,
    borderColor: "transparent",
  },
  reactOn: { backgroundColor: colors.successSoft, borderColor: colors.primary },
  reactOff: { backgroundColor: colors.dangerSoft, borderColor: colors.danger },
  credit: { fontSize: 11, color: colors.textFaint, textAlign: "center" as const, marginTop: 14 },
});
