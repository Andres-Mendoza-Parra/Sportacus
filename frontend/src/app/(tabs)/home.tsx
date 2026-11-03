import { router, useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import FocusChart from "../../components/charts/FocusChart";
import AnimatedBar from "../../components/ui/AnimatedBar";
import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import GradientHeader from "../../components/ui/GradientHeader";
import { useAnimatedNumber } from "../../components/ui/motion";
import PressableScale from "../../components/ui/PressableScale";
import ProgressRing from "../../components/ui/ProgressRing";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { extractErrorMessage } from "../../services/api";
import { ProgressSummary, fetchProgress } from "../../services/progressService";
import {
  DashboardData,
  DashboardFocus,
  DashboardInsight,
  DashboardMetric,
  fetchDashboard,
} from "../../services/dashboardService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

function StatBox({ value, suffix = "", label }: { value: number; suffix?: string; label: string }) {
  const styles = useThemedStyles(createStyles);
  const animated = Math.round(useAnimatedNumber(value));
  return (
    <View style={styles.stat}>
      <Text style={styles.statVal}>
        {animated}
        {suffix}
      </Text>
      <Text style={styles.statLbl}>{label}</Text>
    </View>
  );
}

/** Gráfico de respaldo cuando el backend no envía "focus": el radar muscular de siempre. */
function fallbackFocus(data: DashboardData): DashboardFocus | null {
  if (data.radar.length === 0) return null;
  return {
    type: "radar",
    title: "Estado muscular",
    subtitle: "Series trabajadas por zona en los últimos 30 días",
    items: data.radar.map((p) => ({ key: p.key, label: p.label, value: p.value })),
  };
}

export default function DashboardScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const [data, setData] = useState<DashboardData | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [progress, setProgress] = useState<ProgressSummary | null>(null);

  const load = useCallback(async () => {
    try {
      setData(await fetchDashboard());
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setIsRefreshing(false);
    }
  }, [toast]);

  // La constancia se carga aparte: si falla, el resto de la pantalla no se ve afectado.
  const loadProgress = useCallback(() => {
    fetchProgress().then(setProgress).catch(() => {});
  }, []);

  // Recarga al volver a la pestaña (p. ej. tras completar un cuestionario o marcar ejercicios).
  useFocusEffect(
    useCallback(() => {
      load();
      loadProgress();
    }, [load, loadProgress]),
  );

  if (!data) return <ScreenSkeleton cards={4} />;

  const focus = data.focus ?? fallbackFocus(data);
  const focusEmpty = !focus || focus.items.every((i) => i.value <= 0);
  const today = data.today;
  const hasRoutine = !!today && today.totalCount > 0;

  return (
    <ScrollView
      style={styles.bg}
      showsVerticalScrollIndicator={false}
      refreshControl={
        <RefreshControl
          refreshing={isRefreshing}
          onRefresh={() => {
            setIsRefreshing(true);
            load();
          }}
          tintColor={colors.primary}
        />
      }
    >
      <GradientHeader rounded style={{ paddingBottom: 28 }}>
        <Animated.View entering={FadeInDown.duration(450)}>
          <Text style={styles.hello}>Hola de nuevo 👋</Text>
          <Text style={styles.name}>{data.displayName}</Text>
          <View style={styles.chipsRow}>
            {data.goalLabel ? (
              <View style={styles.chip}>
                <Text style={{ fontSize: 13 }}>🎯</Text>
                <Text style={styles.chipText} numberOfLines={1}>
                  Meta: {data.goalLabel}
                </Text>
              </View>
            ) : null}
            {data.currentActivity.area ? (
              <View style={styles.chip}>
                <Text style={{ fontSize: 13 }}>🏷️</Text>
                <Text style={styles.chipText} numberOfLines={1}>
                  {data.currentActivity.area}
                </Text>
              </View>
            ) : null}
          </View>
        </Animated.View>

        <Animated.View entering={FadeInDown.delay(120).duration(450)} style={styles.stats}>
          <StatBox value={data.stats.globalScore} label="Score global" />
          <StatBox value={data.strengthPct} suffix="%" label="Perfil afinado" />
          <StatBox value={data.stats.activeModules} label="Módulos activos" />
        </Animated.View>
      </GradientHeader>

      {/* Hoy: avance del checklist de la rutina vigente */}
      <Animated.View entering={FadeInDown.delay(180).duration(450)}>
        <PressableScale style={styles.todayCard} onPress={() => router.push("/routine")}>
          {hasRoutine ? (
            <>
              <ProgressRing pct={today.pct} size={68} stroke={8} />
              <View style={{ flex: 1 }}>
                <Text style={styles.todayKicker}>HOY</Text>
                <Text style={styles.todayTitle} numberOfLines={1}>
                  {today.routineName || "Tu rutina"}
                </Text>
                <Text style={styles.todaySub}>
                  {today.doneCount >= today.totalCount
                    ? "¡Rutina completada! 🎉"
                    : `${today.doneCount} de ${today.totalCount} ejercicios hechos`}
                </Text>
              </View>
            </>
          ) : (
            <>
              <Text style={{ fontSize: 34 }}>🏋️</Text>
              <View style={{ flex: 1 }}>
                <Text style={styles.todayKicker}>HOY</Text>
                <Text style={styles.todayTitle}>Ver mi rutina</Text>
                <Text style={styles.todaySub}>Se adapta cada vez que actualizas tu perfil</Text>
              </View>
            </>
          )}
          <Text style={styles.ctaArrow}>›</Text>
        </PressableScale>
      </Animated.View>

      {/* Constancia: racha y aviso de evaluación pendiente */}
      {progress ? (
        <Animated.View entering={FadeInDown.delay(220).duration(450)}>
          <PressableScale style={styles.todayCard} onPress={() => router.push("/progress")}>
            <Text style={{ fontSize: 34 }}>🔥</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.todayKicker}>CONSTANCIA</Text>
              <Text style={styles.todayTitle} numberOfLines={1}>
                {progress.currentStreak > 0 ? `${progress.currentStreak} ${progress.currentStreak === 1 ? "sesión seguida" : "sesiones seguidas"}` : "Empieza tu racha hoy"}
              </Text>
              <Text style={styles.todaySub}>
                {progress.testsDue ? "📏 Toca repetir tu evaluación física" : `${progress.sessionsThisWeek} esta semana · ${progress.totalSessions} en total`}
              </Text>
            </View>
            <Text style={styles.ctaArrow}>›</Text>
          </PressableScale>
        </Animated.View>
      ) : null}

      {/* Gráfico principal: el tipo depende de la meta del usuario */}
      {focus ? (
        <Animated.View entering={FadeInDown.delay(260).duration(450)}>
          <Text style={styles.sectionTitle}>{focus.title}</Text>
          {focus.subtitle ? <Text style={styles.sectionSub}>{focus.subtitle}</Text> : null}
          <View style={styles.focusCard}>
            {focusEmpty ? (
              <View style={styles.emptyBox}>
                <Text style={{ fontSize: 34 }}>📊</Text>
                <Text style={styles.emptyTitle}>Completa tu perfil para ver esto</Text>
                <Text style={styles.emptyText}>
                  Con más datos tuyos este gráfico se llena con tu información real.
                </Text>
                <GradientButton
                  title="Completar mi perfil"
                  onPress={() => router.push("/questionnaires")}
                  style={{ marginTop: 12, alignSelf: "stretch" }}
                />
              </View>
            ) : (
              <FocusChart focus={focus} />
            )}
          </View>
        </Animated.View>
      ) : null}

      {/* Métricas clave */}
      {data.metrics.length > 0 ? (
        <Animated.View entering={FadeInDown.delay(340).duration(450)}>
          <Text style={styles.sectionTitle}>Tus métricas</Text>
          <View style={styles.metricsGrid}>
            {data.metrics.map((m, idx) => (
              <MetricCard key={m.key} metric={m} delay={idx * 80} />
            ))}
          </View>
        </Animated.View>
      ) : data.progress.length > 0 ? (
        <Animated.View entering={FadeInDown.delay(340).duration(450)}>
          <Text style={styles.sectionTitle}>Progreso por objetivo</Text>
          <View style={styles.progressList}>
            {data.progress.map((item, idx) => (
              <View key={item.key} style={styles.progItem}>
                <View style={[styles.progIcon, { backgroundColor: item.color + "22" }]}>
                  <Text style={{ fontSize: 18 }}>{item.icon}</Text>
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.progName}>{item.label}</Text>
                  <AnimatedBar pct={item.progressPct} color={item.color} delay={350 + idx * 120} style={{ marginTop: 6 }} />
                </View>
                <Text style={[styles.progPct, { color: item.color }]}>{item.progressPct}%</Text>
              </View>
            ))}
          </View>
        </Animated.View>
      ) : null}

      {/* Recomendaciones del momento */}
      {data.insights.length > 0 ? (
        <Animated.View entering={FadeInDown.delay(420).duration(450)}>
          <Text style={styles.sectionTitle}>Para ti</Text>
          <View style={{ marginHorizontal: 16, gap: 10 }}>
            {data.insights.map((ins, idx) => (
              <InsightCard key={`${ins.title}-${idx}`} insight={ins} />
            ))}
          </View>
        </Animated.View>
      ) : null}

      <View style={{ height: 32 }} />
    </ScrollView>
  );
}

function MetricCard({ metric, delay }: { metric: DashboardMetric; delay: number }) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const tone =
    metric.status === "good"
      ? { fg: colors.success, bg: colors.successSoft, label: "En rango" }
      : metric.status === "warn"
        ? { fg: colors.warn, bg: colors.warnSoft, label: "Atención" }
        : { fg: colors.danger, bg: colors.dangerSoft, label: "Bajo" };

  return (
    <View style={styles.metricCard}>
      <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 6 }}>
        <Text style={styles.metricLabel} numberOfLines={1}>
          {metric.label}
        </Text>
        <View style={[styles.statusChip, { backgroundColor: tone.bg }]}>
          <Text style={[styles.statusChipText, { color: tone.fg }]}>{tone.label}</Text>
        </View>
      </View>
      <Text style={styles.metricValue}>
        {metric.value}
        {metric.unit ? <Text style={styles.metricUnit}> {metric.unit}</Text> : null}
      </Text>
      {metric.pct !== undefined && metric.pct !== null ? (
        <AnimatedBar pct={metric.pct} color={tone.fg} height={5} delay={delay} style={{ marginTop: 8 }} />
      ) : null}
      {metric.hint ? <Text style={styles.metricHint}>{metric.hint}</Text> : null}
    </View>
  );
}

function InsightCard({ insight }: { insight: DashboardInsight }) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const tone =
    insight.tone === "good"
      ? { bg: colors.successSoft, bar: colors.success }
      : insight.tone === "warn"
        ? { bg: colors.warnSoft, bar: colors.warn }
        : { bg: colors.infoSoft, bar: colors.info };

  const content = (
    <>
      <View style={[styles.insightBar, { backgroundColor: tone.bar }]} />
      <View style={[styles.insightIcon, { backgroundColor: tone.bg }]}>
        <Text style={{ fontSize: 20 }}>{insight.icon || "💡"}</Text>
      </View>
      <View style={{ flex: 1 }}>
        <Text style={styles.insightTitle}>{insight.title}</Text>
        <Text style={styles.insightText}>{insight.text}</Text>
      </View>
      {insight.route ? <Text style={styles.ctaArrow}>›</Text> : null}
    </>
  );

  if (!insight.route) return <View style={styles.insightCard}>{content}</View>;
  return (
    <PressableScale style={styles.insightCard} onPress={() => router.push(insight.route as never)}>
      {content}
    </PressableScale>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  hello: { color: "rgba(255,255,255,0.8)", fontSize: 13 },
  name: { color: "#fff", fontSize: 24, fontWeight: "700" as const, marginTop: 2 },
  chipsRow: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 8, marginTop: 10 },
  chip: {
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 6,
    backgroundColor: "rgba(255,255,255,0.18)",
    borderRadius: 20,
    paddingVertical: 5,
    paddingHorizontal: 12,
    maxWidth: "100%" as const,
  },
  chipText: { color: "#fff", fontSize: 12, flexShrink: 1 },
  stats: { flexDirection: "row" as const, gap: 10, marginTop: 18 },
  stat: {
    flex: 1,
    backgroundColor: "rgba(255,255,255,0.16)",
    borderRadius: 16,
    paddingVertical: 13,
    paddingHorizontal: 8,
    alignItems: "center" as const,
  },
  statVal: { color: "#fff", fontSize: 24, fontWeight: "700" as const },
  statLbl: { color: "rgba(255,255,255,0.8)", fontSize: 10, marginTop: 2, textAlign: "center" as const },
  todayCard: {
    marginHorizontal: 16,
    marginTop: 18,
    backgroundColor: colors.card,
    borderRadius: 20,
    padding: 16,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 14,
    ...shadow,
  },
  todayKicker: { fontSize: 11, fontWeight: "800" as const, color: colors.primary, letterSpacing: 1 },
  todayTitle: { fontSize: 16, fontWeight: "700" as const, color: colors.text, marginTop: 2 },
  todaySub: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  ctaArrow: { fontSize: 26, color: colors.primary },
  sectionTitle: { fontSize: 16, fontWeight: "700" as const, color: colors.text, marginHorizontal: 16, marginTop: 22, marginBottom: 4 },
  sectionSub: { fontSize: 12, color: colors.textMuted, marginHorizontal: 16, marginBottom: 10 },
  focusCard: {
    marginHorizontal: 16,
    marginTop: 8,
    backgroundColor: colors.card,
    borderRadius: 20,
    padding: 16,
    alignItems: "stretch" as const,
    justifyContent: "center" as const,
    ...shadow,
  },
  emptyBox: { alignItems: "center" as const, paddingVertical: 12 },
  emptyTitle: { fontSize: 15, fontWeight: "700" as const, color: colors.text, marginTop: 8, textAlign: "center" as const },
  emptyText: { fontSize: 12, color: colors.textMuted, marginTop: 4, textAlign: "center" as const, lineHeight: 17 },
  metricsGrid: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 10, marginHorizontal: 16, marginTop: 10 },
  metricCard: {
    flexGrow: 1,
    flexBasis: "46%" as const,
    backgroundColor: colors.card,
    borderRadius: 16,
    padding: 14,
    ...shadow,
  },
  metricLabel: { flex: 1, fontSize: 12, fontWeight: "600" as const, color: colors.textMuted },
  statusChip: { borderRadius: 8, paddingHorizontal: 7, paddingVertical: 2 },
  statusChipText: { fontSize: 10, fontWeight: "700" as const },
  metricValue: { fontSize: 24, fontWeight: "800" as const, color: colors.text, marginTop: 8 },
  metricUnit: { fontSize: 12, fontWeight: "600" as const, color: colors.textMuted },
  metricHint: { fontSize: 11, color: colors.textMuted, marginTop: 6, lineHeight: 15 },
  progressList: { marginHorizontal: 16, marginTop: 10 },
  progItem: {
    backgroundColor: colors.card,
    borderRadius: 16,
    paddingVertical: 13,
    paddingHorizontal: 14,
    marginBottom: 8,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 12,
    ...shadow,
  },
  progIcon: { width: 38, height: 38, borderRadius: 12, alignItems: "center" as const, justifyContent: "center" as const },
  progName: { fontSize: 13, fontWeight: "600" as const, color: colors.text },
  progPct: { fontSize: 13, fontWeight: "700" as const },
  insightCard: {
    backgroundColor: colors.card,
    borderRadius: 16,
    paddingVertical: 14,
    paddingRight: 14,
    paddingLeft: 18,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 12,
    overflow: "hidden" as const,
    ...shadow,
  },
  insightBar: { position: "absolute" as const, left: 0, top: 0, bottom: 0, width: 5 },
  insightIcon: { width: 40, height: 40, borderRadius: 12, alignItems: "center" as const, justifyContent: "center" as const },
  insightTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  insightText: { fontSize: 12, color: colors.textMuted, marginTop: 2, lineHeight: 17 },
});
