import { router, useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import Sparkline from "../components/charts/Sparkline";
import { useToast } from "../components/ui/Feedback";
import GradientHeader from "../components/ui/GradientHeader";
import AnimatedBar from "../components/ui/AnimatedBar";
import PressableScale from "../components/ui/PressableScale";
import { ScreenSkeleton } from "../components/ui/Skeleton";
import { extractErrorMessage } from "../services/api";
import { isServedFromCache } from "../services/cache";
import { MetricSeries, ProgressSummary, fetchProgress } from "../services/progressService";
import { Theme, useThemedStyles, useTheme } from "../theme";

const EVOLUTION: { key: string; label: string; icon: string }[] = [
  { key: "peso_kg", label: "Peso", icon: "⚖️" },
  { key: "global_score", label: "Score global", icon: "📈" },
  { key: "profile_strength", label: "Perfil afinado", icon: "🎚️" },
];

function formatDelta(series: MetricSeries): string {
  const first = series.points[0].value;
  const last = series.points[series.points.length - 1].value;
  const diff = Math.round((last - first) * 10) / 10;
  if (series.points.length < 2) return "Primera medición";
  return `${diff > 0 ? "+" : ""}${diff} ${series.unit ?? ""} desde el inicio`;
}

export default function ProgressScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const [data, setData] = useState<ProgressSummary | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [offline, setOffline] = useState(false);

  const load = useCallback(async () => {
    try {
      setData(await fetchProgress());
      setOffline(isServedFromCache("progress"));
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setRefreshing(false);
    }
  }, [toast]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  if (!data) return <ScreenSkeleton />;
  const maxWeek = Math.max(1, ...data.weeks.map((w) => w.sessions));
  const seriesByKey = new Map(data.series.map((s) => [s.key, s]));
  const testSeries = data.series.filter((s) => s.key.startsWith("test_"));

  return (
    <ScrollView
      style={styles.bg}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} tintColor={colors.primary} />}
    >
      <GradientHeader>
        <View style={styles.headTop}>
          <PressableScale style={styles.backBtn} onPress={() => (router.canGoBack() ? router.back() : router.replace("/home"))}>
            <Text style={styles.backText}>←</Text>
          </PressableScale>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>Mi progreso</Text>
            <Text style={styles.subtitle}>Constancia, logros y evolución</Text>
          </View>
        </View>
        <Animated.View entering={FadeInDown.duration(400)} style={styles.statsRow}>
          <Stat icon="🔥" value={data.currentStreak} label="Racha" />
          <Stat icon="📅" value={data.sessionsThisWeek} label="Esta semana" />
          <Stat icon="🏆" value={data.longestStreak} label="Mejor racha" />
          <Stat icon="✅" value={data.totalSessions} label="Sesiones" />
        </Animated.View>
      </GradientHeader>

      {offline ? <Text style={styles.offline}>📴 Sin conexión: mostrando tus últimos datos guardados.</Text> : null}

      {data.totalSessions === 0 ? (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Aún no hay sesiones</Text>
          <Text style={styles.muted}>Completa tu primer entrenamiento con «Entrenar ahora» en la pestaña Rutina y aquí verás tu racha y logros.</Text>
        </View>
      ) : null}

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Sesiones por semana</Text>
        <View style={styles.bars}>
          {data.weeks.map((w) => (
            <View key={w.label} style={styles.barCol}>
              <Text style={styles.barVal}>{w.sessions || ""}</Text>
              <View style={styles.barTrack}>
                <View style={[styles.barFill, { height: `${(w.sessions / maxWeek) * 100}%` }]} />
              </View>
              <Text style={styles.barLabel}>{w.label}</Text>
            </View>
          ))}
        </View>
        {data.streakWeeks > 0 ? <Text style={styles.muted}>🗓️ {data.streakWeeks} {data.streakWeeks === 1 ? "semana" : "semanas"} seguidas entrenando</Text> : null}
        {data.avgRpe != null ? <Text style={styles.muted}>Esfuerzo promedio reciente: {data.avgRpe.toFixed(1)} / 10</Text> : null}
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Retos</Text>
        {data.challenges.map((c) => (
          <View key={c.key} style={{ marginTop: 10 }}>
            <View style={styles.rowBetween}>
              <Text style={styles.itemTitle}>{c.completed ? "✅ " : ""}{c.title}</Text>
              <Text style={styles.muted}>{c.current}/{c.target}</Text>
            </View>
            <Text style={styles.muted}>{c.description}</Text>
            <AnimatedBar pct={(c.current / c.target) * 100} height={7} style={{ marginTop: 6 }} />
          </View>
        ))}
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Logros</Text>
        <View style={styles.grid}>
          {data.achievements.map((a) => (
            <View key={a.key} style={[styles.badge, !a.achieved && styles.badgeLocked]}>
              <Text style={{ fontSize: 28, opacity: a.achieved ? 1 : 0.35 }}>{a.icon}</Text>
              <Text style={styles.badgeTitle} numberOfLines={1}>{a.title}</Text>
              <Text style={styles.badgeSub} numberOfLines={2}>{a.achieved ? a.description : `${a.current}/${a.target}`}</Text>
            </View>
          ))}
        </View>
      </View>

      {EVOLUTION.some((e) => seriesByKey.has(e.key)) ? (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Evolución</Text>
          <Text style={styles.muted}>Se actualiza cada vez que modificas tu perfil.</Text>
          {EVOLUTION.map((e) => {
            const s = seriesByKey.get(e.key);
            if (!s) return null;
            const last = s.points[s.points.length - 1].value;
            return (
              <View key={e.key} style={styles.evoRow}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.itemTitle}>{e.icon} {e.label}</Text>
                  <Text style={styles.evoValue}>{last} <Text style={styles.muted}>{s.unit ?? ""}</Text></Text>
                  <Text style={styles.muted}>{formatDelta(s)}</Text>
                </View>
                <Sparkline values={s.points.map((p) => p.value)} color={colors.primary} width={130} />
              </View>
            );
          })}
        </View>
      ) : null}

      <PressableScale style={[styles.card, styles.testsCard]} onPress={() => router.push("/tests")}>
        <Text style={{ fontSize: 28 }}>📏</Text>
        <View style={{ flex: 1 }}>
          <Text style={styles.itemTitle}>Evaluación física</Text>
          <Text style={styles.muted}>
            {data.testsDue
              ? "Ya pasaron 4 semanas: repite tus pruebas para ver cuánto mejoraste."
              : testSeries.length > 0
                ? `${testSeries.length} ${testSeries.length === 1 ? "prueba registrada" : "pruebas registradas"}. Toca para ver tu evolución.`
                : "Mide tu fuerza, resistencia y flexibilidad con pruebas guiadas."}
          </Text>
        </View>
        {data.testsDue ? <Text style={styles.dueDot}>●</Text> : <Text style={styles.arrow}>›</Text>}
      </PressableScale>

      {data.recent.length > 0 ? (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Últimas sesiones</Text>
          {data.recent.map((s, i) => (
            <View key={`${s.date}-${i}`} style={styles.sessionRow}>
              <Text style={styles.itemTitle}>{s.date}</Text>
              <Text style={styles.muted}>
                {[s.durationMin ? `${s.durationMin} min` : null, s.rpe ? `esfuerzo ${s.rpe}/10` : null].filter(Boolean).join(" · ") || "Sesión"}
              </Text>
            </View>
          ))}
        </View>
      ) : null}

      <View style={{ height: 40 }} />
    </ScrollView>
  );
}

function Stat({ icon, value, label }: { icon: string; value: number; label: string }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={styles.stat}>
      <Text style={{ fontSize: 18 }}>{icon}</Text>
      <Text style={styles.statVal}>{value}</Text>
      <Text style={styles.statLbl}>{label}</Text>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  headTop: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  backBtn: { width: 38, height: 38, borderRadius: 19, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center" as const, justifyContent: "center" as const },
  backText: { color: "#fff", fontSize: 18 },
  title: { color: "#fff", fontSize: 22, fontWeight: "800" as const },
  subtitle: { color: "rgba(255,255,255,0.8)", fontSize: 13 },
  statsRow: { flexDirection: "row" as const, gap: 8, marginTop: 16 },
  stat: { flex: 1, backgroundColor: "rgba(255,255,255,0.15)", borderRadius: 14, paddingVertical: 10, alignItems: "center" as const },
  statVal: { color: "#fff", fontSize: 20, fontWeight: "800" as const },
  statLbl: { color: "rgba(255,255,255,0.8)", fontSize: 10 },
  offline: { margin: 16, marginBottom: 0, color: colors.warn, fontSize: 12 },
  card: { backgroundColor: colors.card, borderRadius: 18, padding: 16, marginHorizontal: 16, marginTop: 14, borderWidth: 1, borderColor: colors.border },
  cardTitle: { color: colors.text, fontSize: 16, fontWeight: "700" as const },
  muted: { color: colors.textMuted, fontSize: 12, marginTop: 4, lineHeight: 17 },
  itemTitle: { color: colors.text, fontSize: 14, fontWeight: "600" as const },
  rowBetween: { flexDirection: "row" as const, justifyContent: "space-between" as const, alignItems: "center" as const },
  bars: { flexDirection: "row" as const, alignItems: "flex-end" as const, gap: 6, height: 120, marginTop: 12 },
  barCol: { flex: 1, alignItems: "center" as const, height: "100%" as const, justifyContent: "flex-end" as const },
  barVal: { color: colors.textMuted, fontSize: 10, marginBottom: 2 },
  barTrack: { width: "100%" as const, flex: 1, justifyContent: "flex-end" as const, backgroundColor: colors.track, borderRadius: 6, overflow: "hidden" as const },
  barFill: { backgroundColor: colors.primary, borderRadius: 6, minHeight: 2 },
  barLabel: { color: colors.textFaint, fontSize: 9, marginTop: 4 },
  grid: { flexDirection: "row" as const, flexWrap: "wrap" as const, gap: 10, marginTop: 12 },
  badge: { width: "30%" as const, flexGrow: 1, backgroundColor: colors.primarySoft, borderRadius: 14, padding: 10, alignItems: "center" as const, gap: 2 },
  badgeLocked: { backgroundColor: colors.cardAlt },
  badgeTitle: { color: colors.text, fontSize: 12, fontWeight: "700" as const },
  badgeSub: { color: colors.textMuted, fontSize: 10, textAlign: "center" as const },
  evoRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 8, marginTop: 14 },
  evoValue: { color: colors.text, fontSize: 22, fontWeight: "800" as const },
  testsCard: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  arrow: { color: colors.textFaint, fontSize: 22 },
  dueDot: { color: colors.warn, fontSize: 16 },
  sessionRow: { flexDirection: "row" as const, justifyContent: "space-between" as const, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: colors.border },
});
