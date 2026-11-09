import { router } from "expo-router";
import { useMemo, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";

import FactorList from "../../components/plan/FactorList";
import PlanSessionCard from "../../components/plan/PlanSessionCard";
import { Card } from "../../components/ui/Card";
import Chip from "../../components/ui/Chip";
import { useConfirm, useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import GradientHeader from "../../components/ui/GradientHeader";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { EmptyState, ErrorState } from "../../components/ui/States";
import { useWorld } from "../../context/WorldContext";
import { useLoader, useReloadOnFocus } from "../../hooks/useLoader";
import { extractErrorMessage } from "../../services/api";
import { fetchPlan, regeneratePlan } from "../../services/worldService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

/** Pestaña Plan: semana en curso del plan del mundo activo, por qué se armó así y regeneración. */
export default function PlanScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const confirm = useConfirm();
  const { active, version, invalidate } = useWorld();
  const plan = useLoader(fetchPlan, [version]);
  const [week, setWeek] = useState<number | null>(null);
  const [regenerating, setRegenerating] = useState(false);

  useReloadOnFocus(plan.reload);

  const data = plan.data;
  const currentWeek = data?.currentWeek ?? 0;
  const shownWeek = week ?? currentWeek;
  const sessions = useMemo(
    () =>
      (data?.sessions ?? [])
        .filter((s) => s.weekIndex === shownWeek)
        .sort((a, b) => (a.weekday ?? 8) - (b.weekday ?? 8) || a.dayIndex - b.dayIndex),
    [data, shownWeek],
  );
  const doneCount = sessions.filter((s) => s.done).length;

  async function handleRegenerate() {
    const ok = await confirm({
      title: "¿Regenerar tu plan?",
      message: "Armaremos un plan nuevo con tus datos actuales. Tu historial y tu progreso se conservan.",
      confirmText: "Regenerar",
    });
    if (!ok) return;
    setRegenerating(true);
    try {
      const next = await regeneratePlan();
      plan.setData(next);
      setWeek(null);
      invalidate();
      toast.success("¡Plan nuevo listo!");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setRegenerating(false);
    }
  }

  if (plan.loading && !data) return <ScreenSkeleton cards={4} />;
  if (!data) return <ErrorState message={plan.error ?? "Sin datos"} onRetry={plan.reload} style={{ flex: 1, justifyContent: "center" }} />;

  return (
    <View style={styles.bg}>
      <ScrollView
        contentContainerStyle={{ paddingBottom: 32 }}
        refreshControl={<RefreshControl refreshing={plan.refreshing} onRefresh={plan.reload} tintColor={colors.primary} />}
      >
        <GradientHeader rounded>
          <Text style={styles.kicker}>
            {active ? `${active.icon} ${active.name} · ${active.trackName}` : "Tu plan"}
          </Text>
          <Text style={styles.title}>{data.title}</Text>
          {data.summary ? <Text style={styles.summary}>{data.summary}</Text> : null}
          <View style={styles.headStats}>
            {data.weeks > 1 ? <Text style={styles.pill}>Semana {currentWeek + 1} de {data.weeks}</Text> : null}
            <Text style={styles.pill}>
              {doneCount}/{sessions.length} sesiones {shownWeek === currentWeek ? "esta semana" : ""}
            </Text>
          </View>
        </GradientHeader>

        {data.weeks > 1 ? (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.weeks}>
            {Array.from({ length: data.weeks }).map((_, i) => (
              <Chip
                key={i}
                label={i === currentWeek ? `Semana ${i + 1} · actual` : `Semana ${i + 1}`}
                selected={i === shownWeek}
                onPress={() => setWeek(i)}
              />
            ))}
          </ScrollView>
        ) : null}

        <View style={styles.list}>
          {sessions.length === 0 ? (
            <EmptyState icon="🗓️" title="Sin sesiones esta semana" body="Regenera el plan para armar una semana nueva." />
          ) : (
            sessions.map((s, i) => (
              <PlanSessionCard
                key={s.id}
                session={s}
                index={i}
                isCurrentWeek={shownWeek === currentWeek}
                onPress={() => router.push({ pathname: "/session/[id]", params: { id: s.id } })}
              />
            ))
          )}
        </View>

        {data.rationale.length > 0 ? (
          <Card style={styles.card} delay={120}>
            <FactorList factors={data.rationale} />
          </Card>
        ) : null}

        <View style={{ paddingHorizontal: 16, marginTop: 18, gap: 10 }}>
          <GradientButton title="🔄 Regenerar plan" variant="ghost" onPress={handleRegenerate} loading={regenerating} />
          <Text style={styles.hint}>El plan se ajusta solo a medida que registras sesiones; regenéralo si cambió tu disponibilidad o tu objetivo.</Text>
        </View>
      </ScrollView>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  kicker: { color: "rgba(255,255,255,0.85)", fontSize: 12, fontWeight: "600" as const },
  title: { color: "#fff", fontSize: 22, fontWeight: "800" as const, marginTop: 4 },
  summary: { color: "rgba(255,255,255,0.85)", fontSize: 13, marginTop: 6, lineHeight: 19 },
  headStats: { flexDirection: "row" as const, gap: 8, marginTop: 12, flexWrap: "wrap" as const },
  pill: { color: "#fff", fontSize: 12, fontWeight: "700" as const, backgroundColor: "rgba(255,255,255,0.18)", paddingHorizontal: 10, paddingVertical: 5, borderRadius: 999, overflow: "hidden" as const },
  weeks: { paddingHorizontal: 16, paddingTop: 14, gap: 8 },
  list: { paddingHorizontal: 16, paddingTop: 16, gap: 10 },
  card: { marginHorizontal: 16, marginTop: 18 },
  hint: { fontSize: 12, color: colors.textFaint, textAlign: "center" as const, lineHeight: 17 },
});
