import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { openSource } from "../../components/assessments/shared";
import MovementMedia from "../../components/library/MovementMedia";
import { LevelDots } from "../../components/library/MovementRow";
import { Attribution, ListCard } from "../../components/library/MovementSections";
import { prescriptionChips } from "../../components/session/format";
import AnimatedBar from "../../components/ui/AnimatedBar";
import { Card, CardTitle, SectionTitle } from "../../components/ui/Card";
import Chip from "../../components/ui/Chip";
import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import NoticeBox, { noticeColors, toNoticeKind } from "../../components/ui/NoticeBox";
import ScreenHeader, { HeaderIconButton } from "../../components/ui/ScreenHeader";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { ErrorState } from "../../components/ui/States";
import { blockTypeIcon, blockTypeLabel, humanize } from "../../constants/labels";
import { useWorld } from "../../context/WorldContext";
import { useLoader } from "../../hooks/useLoader";
import { track } from "../../services/analytics";
import { extractErrorMessage } from "../../services/api";
import { BlockDetail, ProgressionInfo, Reason } from "../../services/types";
import { fetchBlockDetail, setFavorite, swapBlock } from "../../services/worldService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

/**
 * Detalle de un bloque del plan: qué hacer (tu dosis), por qué está en TU plan, cómo hacerlo,
 * errores comunes, precauciones personales, progresión y acciones (cambiar, preguntar, favorito).
 */
export default function BlockDetailScreen() {
  const { id, world } = useLocalSearchParams<{ id: string; world?: string }>();
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const { data, error, loading, reload, setData } = useLoader(() => fetchBlockDetail(String(id), world ?? null), [id, world]);
  const [swapping, setSwapping] = useState(false);
  const { active, invalidate } = useWorld();
  const worldKey = world ?? active?.key ?? null;
  const loadedId = data?.block.id ?? null;
  const hasWhy = (data?.why.length ?? 0) > 0;

  useEffect(() => {
    if (!loadedId) return;
    track("block_detail_opened", { world: worldKey ?? undefined });
    if (hasWhy) track("why_viewed", { world: worldKey ?? undefined });
  }, [loadedId, hasWhy, worldKey]);

  if (loading && !data) return <ScreenSkeleton cards={4} />;
  if (!data) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <ScreenHeader title="Ejercicio" fallback="/plan" />
        <ErrorState message={error ?? "No encontramos este ejercicio."} onRetry={reload} />
      </View>
    );
  }

  const { block, movement } = data;
  const name = movement?.name ?? block.movementName ?? block.title;
  const images = movement?.imageUrls?.length ? movement.imageUrls : block.imageUrl ? [block.imageUrl] : [];
  const muscleViews = movement?.muscleViews ?? [];
  const muscles = movement?.muscles ?? [];
  const equipment = movement?.equipment ?? [];
  const cautions = movement?.cautions ?? [];

  const detail: BlockDetail = data;

  async function toggleFavorite() {
    const current = detail.movement;
    if (!current) return;
    const next = !current.favorite;
    const patch = (fav: boolean) =>
      setData((prev) => {
        const base: BlockDetail = prev ?? detail;
        return base.movement ? { ...base, movement: { ...base.movement, favorite: fav } } : base;
      });
    patch(next);
    try {
      await setFavorite(current.key, next);
      toast.success(next ? "Guardado en favoritos ★" : "Quitado de favoritos");
    } catch (e) {
      patch(!next);
      toast.error(extractErrorMessage(e));
    }
  }

  async function swap() {
    setSwapping(true);
    try {
      const next = await swapBlock(block.id, worldKey);
      toast.success(`Cambiamos «${block.title}» por «${next.title}»`);
      await reload();
      invalidate();
    } catch (e) {
      toast.error(extractErrorMessage(e));
    } finally {
      setSwapping(false);
    }
  }

  function ask() {
    router.push({
      pathname: "/chat",
      params: { prompt: `¿Por qué tengo ${name} en mi plan y cómo lo hago bien?`, t: String(Date.now()) },
    });
  }

  return (
    <View style={styles.bg}>
      <ScreenHeader
        title={block.title}
        subtitle={[blockTypeLabel(block.blockType), data.sessionTitle].filter(Boolean).join(" · ")}
        fallback="/plan"
        right={
          movement ? (
            <HeaderIconButton
              icon={movement.favorite ? "★" : "☆"}
              label={movement.favorite ? "Quitar de favoritos" : "Añadir a favoritos"}
              onPress={toggleFavorite}
            />
          ) : null
        }
      />
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 32 + insets.bottom }} showsVerticalScrollIndicator={false}>
        {/* 1. Medios */}
        {images.length + muscleViews.length > 0 ? (
          <MovementMedia imageUrls={images} muscleViews={muscleViews} name={name} />
        ) : (
          <View style={styles.hero} accessible accessibilityLabel={`${blockTypeLabel(block.blockType)}: ${name}`}>
            <Text style={{ fontSize: 56 }}>{blockTypeIcon(block.blockType)}</Text>
            {movement?.pattern ? <Text style={styles.heroText}>{humanize(movement.pattern)}</Text> : null}
          </View>
        )}
        {movement?.videoUrl ? (
          <GradientButton title="▶ Ver video" variant="ghost" onPress={() => openSource(movement.videoUrl as string)} style={{ marginTop: 10 }} />
        ) : null}

        {/* 2. Título y ficha */}
        <View style={{ marginTop: 16, gap: 4 }}>
          <Text style={styles.kicker}>{blockTypeLabel(block.blockType)}</Text>
          <Text style={styles.title}>{block.title}</Text>
          {name !== block.title ? <Text style={styles.subtitle}>{name}</Text> : null}
        </View>
        <View style={styles.chips}>
          {muscles.map((m) => (
            <Chip key={`m-${m}`} label={`💪 ${humanize(m)}`} />
          ))}
          {movement?.impact ? <Chip label={`Impacto ${humanize(movement.impact).toLowerCase()}`} /> : null}
          {movement ? (equipment.length ? equipment.map((e) => <Chip key={`e-${e}`} label={`🏋️ ${humanize(e)}`} />) : <Chip label="Sin equipo" />) : null}
        </View>
        {movement?.level ? (
          <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginTop: 10 }}>
            <Text style={styles.factLabel}>Nivel</Text>
            <LevelDots level={movement.level} />
          </View>
        ) : null}

        {movement && !movement.allowed ? (
          <NoticeBox
            tone="danger"
            title="No recomendado para ti"
            body="Según tu chequeo de salud, este movimiento puede cargar una zona sensible. Cámbialo por una alternativa o consulta a un profesional."
            style={{ marginTop: 14 }}
          />
        ) : null}

        {/* 3. Tu dosis */}
        <SectionTitle>Tu dosis</SectionTitle>
        <Card style={styles.doseCard}>
          <View style={[styles.chips, { marginTop: 0 }]}>
            {prescriptionChips(block.prescription).map((c) => (
              <View key={c} style={styles.dosePill}>
                <Text style={styles.dosePillText}>{c}</Text>
              </View>
            ))}
          </View>
          {data.dose.length > 0 ? (
            <View style={{ marginTop: 12, gap: 8 }}>
              {data.dose.map((line, i) => (
                <View key={i} style={styles.item}>
                  <Text style={styles.bullet}>✔</Text>
                  <Text style={[styles.itemText, { fontSize: 14, lineHeight: 20 }]}>{line}</Text>
                </View>
              ))}
            </View>
          ) : null}
          {block.prescription.cue ? <Text style={[styles.itemText, { marginTop: 10 }]}>🎯 {block.prescription.cue}</Text> : null}
          {block.notes ? <Text style={[styles.muted, { marginTop: 8 }]}>{block.notes}</Text> : null}
        </Card>

        {/* 4. Por qué */}
        {data.why.length > 0 ? (
          <>
            <SectionTitle>¿Por qué este ejercicio?</SectionTitle>
            <View style={{ gap: 10 }}>
              {data.why.map((reason, i) => (
                <WhyCard key={i} reason={reason} main={i === 0} delay={i * 50} />
              ))}
            </View>
          </>
        ) : null}

        {/* 5. Cómo hacerlo */}
        {movement && (movement.description || movement.steps?.length || movement.cues?.length) ? (
          <>
            <SectionTitle>Cómo hacerlo</SectionTitle>
            <View style={{ gap: 12 }}>
              {movement.description ? (
                <Card>
                  <Text style={styles.desc}>{movement.description}</Text>
                </Card>
              ) : null}
              <ListCard title="Paso a paso" items={movement.steps ?? []} numbered delay={0} />
              <ListCard title="Claves de técnica" items={movement.cues ?? []} icon="✅" delay={40} />
            </View>
          </>
        ) : null}

        {/* 6. Errores comunes */}
        {movement?.commonMistakes?.length ? (
          <>
            <SectionTitle>Errores comunes</SectionTitle>
            <ListCard title="Evita esto" items={movement.commonMistakes} icon="❌" delay={0} />
          </>
        ) : null}

        {/* 7. Ten en cuenta */}
        {data.considerations.length + cautions.length > 0 ? (
          <>
            <SectionTitle>Ten en cuenta</SectionTitle>
            <View style={{ gap: 8 }}>
              {data.considerations.map((c, i) => (
                <NoticeBox key={`r${i}`} tone={toNoticeKind(c.tone)} icon={c.icon} title={c.title} body={c.text} />
              ))}
              {cautions.map((c, i) => (
                <NoticeBox key={`c${i}`} tone="warn" body={c} />
              ))}
            </View>
          </>
        ) : null}

        {/* 8. Progresión */}
        {data.progression ? (
          <>
            <SectionTitle>Tu progresión</SectionTitle>
            <ProgressionCard progression={data.progression} />
          </>
        ) : null}

        {/* 9. Acciones */}
        <View style={{ gap: 10, marginTop: 22 }}>
          {!block.progressionKey ? <GradientButton title="🔄 Cambiar ejercicio" variant="ghost" onPress={swap} loading={swapping} /> : null}
          <GradientButton title="🤖 Preguntar al asistente" variant="ghost" onPress={ask} />
          {movement ? (
            <GradientButton
              title={movement.favorite ? "★ En tus favoritos" : "☆ Guardar en favoritos"}
              variant="ghost"
              onPress={toggleFavorite}
            />
          ) : null}
        </View>
        {movement ? (
          <View style={{ marginTop: 16 }}>
            <Attribution detail={movement} />
          </View>
        ) : null}
      </ScrollView>
    </View>
  );
}

/** Razón de "¿Por qué este ejercicio?"; la principal va destacada con el color del tema. */
function WhyCard({ reason, main, delay }: { reason: Reason; main: boolean; delay: number }) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const tone = noticeColors(toNoticeKind(reason.tone), colors);
  return (
    <Card delay={delay} style={[styles.why, main ? styles.whyMain : { borderLeftColor: tone.fg }]}>
      <Text style={{ fontSize: main ? 26 : 20 }}>{reason.icon}</Text>
      <View style={{ flex: 1, gap: 2 }}>
        {main ? <Text style={styles.whyKicker}>Razón principal</Text> : null}
        <Text style={[styles.whyTitle, main && { fontSize: 15 }]}>{reason.title}</Text>
        <Text style={styles.itemText}>{reason.text}</Text>
      </View>
    </Card>
  );
}

function ProgressionCard({ progression }: { progression: ProgressionInfo }) {
  const styles = useThemedStyles(createStyles);
  const pct = progression.total > 0 ? (progression.step / progression.total) * 100 : 0;
  return (
    <Card>
      <CardTitle title={`Escalón ${progression.step} de ${progression.total} en ${progression.name}`} subtitle={`Ahora: ${progression.stepName}`} />
      <AnimatedBar pct={pct} height={8} />
      <View style={{ gap: 6, marginTop: 12 }}>
        {progression.nextStepName ? (
          <Text style={styles.itemText}>
            <Text style={{ fontWeight: "700" }}>Siguiente: </Text>
            {progression.nextStepName}
          </Text>
        ) : (
          <Text style={styles.itemText}>🏆 Estás en el último escalón de esta progresión.</Text>
        )}
        {progression.unlock ? (
          <Text style={styles.itemText}>
            <Text style={{ fontWeight: "700" }}>Para avanzar: </Text>
            {progression.unlock}
          </Text>
        ) : null}
      </View>
    </Card>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  hero: {
    height: 170,
    borderRadius: 18,
    backgroundColor: colors.primarySoft,
    alignItems: "center" as const,
    justifyContent: "center" as const,
    gap: 6,
  },
  heroText: { fontSize: 13, fontWeight: "700" as const, color: colors.primary },
  kicker: { fontSize: 11, fontWeight: "700" as const, color: colors.primary, textTransform: "uppercase" as const, letterSpacing: 0.8 },
  title: { fontSize: 24, fontWeight: "800" as const, color: colors.text },
  subtitle: { fontSize: 14, color: colors.textMuted },
  chips: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 6, marginTop: 12 },
  factLabel: { fontSize: 11, color: colors.textFaint, fontWeight: "700" as const, textTransform: "uppercase" as const, letterSpacing: 0.6 },
  doseCard: { borderWidth: 1.5, borderColor: colors.primary },
  dosePill: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 999, backgroundColor: colors.primarySoft },
  dosePillText: { fontSize: 13, fontWeight: "700" as const, color: colors.primary },
  item: { flexDirection: "row" as const, gap: 8, alignItems: "flex-start" as const },
  bullet: { fontSize: 13, color: colors.primary, fontWeight: "800" as const, minWidth: 16, marginTop: 2 },
  itemText: { flex: 1, fontSize: 13, color: colors.text, lineHeight: 19 },
  muted: { fontSize: 13, color: colors.textMuted, lineHeight: 19 },
  desc: { fontSize: 14, color: colors.text, lineHeight: 21 },
  why: { flexDirection: "row" as const, gap: 12, alignItems: "flex-start" as const, padding: 14, borderLeftWidth: 4 },
  whyMain: { backgroundColor: colors.primarySoft, borderLeftColor: colors.primary },
  whyKicker: { fontSize: 10, fontWeight: "800" as const, color: colors.primary, textTransform: "uppercase" as const, letterSpacing: 0.8 },
  whyTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
});
