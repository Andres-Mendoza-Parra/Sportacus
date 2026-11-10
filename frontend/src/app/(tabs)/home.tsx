import { router } from "expo-router";
import React, { useEffect, useRef, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";

import WorldHeroHeader from "../../components/home/WorldHeroHeader";
import PlanUpdateNotice, { planUpdatedRecently } from "../../components/plan/PlanUpdateNotice";
import { ScrollRef, TourTarget, useTourScroll } from "../../components/tour/TourContext";
import PressableScale from "../../components/ui/PressableScale";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { ErrorState } from "../../components/ui/States";
import WidgetList from "../../components/widgets/WidgetList";
import { useWorld } from "../../context/WorldContext";
import { useLoader, useReloadOnFocus } from "../../hooks/useLoader";
import { loadRawPref, savePref } from "../../services/prefs";
import { PlanView } from "../../services/types";
import { fetchDashboard, fetchPlan } from "../../services/worldService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

const PLAN_NOTICE_PREF = "plan_notice_seen";

/**
 * Inicio del mundo activo (Mi Base incluida): cabecera con racha, accesos rápidos y
 * widgets dirigidos por el servidor (check-in, pregunta del Perfil 360, sugerencias…).
 */
export default function HomeScreen() {
  const { colors, worldColor, setWorldColor } = useTheme();
  const styles = useThemedStyles(createStyles);
  const { active, version } = useWorld();
  const { data, error, loading, refreshing, reload } = useLoader(fetchDashboard, [version]);
  const scrollRef = useRef<ScrollRef>(null);
  const onTourScroll = useTourScroll("home", scrollRef);
  useReloadOnFocus(reload);
  const [plan, setPlan] = useState<PlanView | null>(null);
  const [seenNotice, setSeenNotice] = useState<string | null>(null);

  // La cabecera del mundo trae su color: si la lista de mundos aún no llegó (o difiere), manda el inicio.
  const headerColor = data?.world.color;
  const headerKey = data?.world.key;
  useEffect(() => {
    if (!headerColor || headerColor === worldColor) return;
    if (!active || active.key === headerKey) setWorldColor(headerColor);
  }, [headerColor, headerKey, worldColor, active, setWorldColor]);

  // Aviso de plan actualizado: se pide el plan DESPUÉS del inicio (no compiten por regenerarlo).
  useEffect(() => {
    if (!data) return;
    let alive = true;
    Promise.all([fetchPlan().catch(() => null), loadRawPref<string | null>(PLAN_NOTICE_PREF, null)]).then(([p, seen]) => {
      if (!alive) return;
      setPlan(p);
      setSeenNotice(seen);
    });
    return () => {
      alive = false;
    };
  }, [data]);

  if (loading && !data) return <ScreenSkeleton cards={4} />;
  if (!data) {
    return <ErrorState message={error ?? "Intenta de nuevo."} onRetry={reload} style={{ flex: 1, justifyContent: "center", backgroundColor: colors.bg }} />;
  }

  const isBase = data.world.key === "base";
  // Si el servidor ya trae su propio aviso del plan en el inicio, no se repite.
  const serverNotice = data.widgets.some((w) => w.type === "notice" && w.key.includes("plan"));
  const noticeId = plan ? `${plan.id}:${plan.updatedAt ?? ""}` : null;
  const showPlanNotice = !serverNotice && plan?.updateReason && planUpdatedRecently(plan) && noticeId !== seenNotice;

  return (
    <ScrollView
      ref={scrollRef}
      onScroll={onTourScroll}
      scrollEventThrottle={32}
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingBottom: 32 }}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={reload} tintColor={colors.primary} colors={[colors.primary]} />}
      showsVerticalScrollIndicator={false}
    >
      <TourTarget id="home-header">
        <WorldHeroHeader greeting={data.greeting} world={data.world} streak={data.streak} />
      </TourTarget>
      <View style={{ padding: 16, gap: 14 }}>
        {showPlanNotice && plan?.updateReason ? (
          <PlanUpdateNotice
            reason={plan.updateReason}
            onDismiss={() => {
              setSeenNotice(noticeId);
              savePref(PLAN_NOTICE_PREF, noticeId).catch(() => {});
            }}
          />
        ) : null}
        <TourTarget id="home-actions" style={styles.actions}>
          <QuickAction icon="➕" label="Registrar actividad" onPress={() => router.push("/log-activity")} />
          {isBase ? (
            <QuickAction icon="🧭" label="Explorar mundos" onPress={() => router.push("/onboarding/choose")} />
          ) : (
            <QuickAction icon="🧩" label="Mi perfil 360" onPress={() => router.push("/profile360")} />
          )}
        </TourTarget>
        <WidgetList widgets={data.widgets} />
      </View>
    </ScrollView>
  );
}

function QuickAction({ icon, label, onPress }: { icon: string; label: string; onPress: () => void }) {
  const styles = useThemedStyles(createStyles);
  return (
    <PressableScale onPress={onPress} accessibilityRole="button" accessibilityLabel={label} style={styles.action}>
      <Text style={{ fontSize: 18 }}>{icon}</Text>
      <Text style={styles.actionText} numberOfLines={1}>
        {label}
      </Text>
    </PressableScale>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  actions: { flexDirection: "row" as const, gap: 10 },
  action: {
    flex: 1,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 8,
    minHeight: 48,
    paddingVertical: 12,
    paddingHorizontal: 12,
    borderRadius: 14,
    backgroundColor: colors.card,
    ...shadow,
  },
  actionText: { flex: 1, fontSize: 13, fontWeight: "700" as const, color: colors.text },
});
