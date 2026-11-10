import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import ProgressRing from "../../components/profile360/ProgressRing";
import AnimatedBar from "../../components/ui/AnimatedBar";
import { Card, SectionTitle } from "../../components/ui/Card";
import NoticeBox from "../../components/ui/NoticeBox";
import PressableScale from "../../components/ui/PressableScale";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { SkeletonBlock } from "../../components/ui/Skeleton";
import { ErrorState } from "../../components/ui/States";
import { useLoader, useReloadOnFocus } from "../../hooks/useLoader";
import { fetchOverview } from "../../services/profile360Service";
import { AreaView, QuestionnaireSummary } from "../../services/types";
import { Theme, useThemedStyles, useTheme } from "../../theme";

/** Perfil 360: anillo global + áreas (🔒 = sensible). Toca un área para ver sus cuestionarios. */
export default function Profile360Screen() {
  const params = useLocalSearchParams<{ area?: string }>();
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const overview = useLoader(fetchOverview);
  const [areaKey, setAreaKey] = useState<string | null>(typeof params.area === "string" ? params.area : null);
  useReloadOnFocus(overview.reload);

  const data = overview.data;
  const area = data?.areas.find((a) => a.key === areaKey) ?? null;

  function openQuestionnaire(a: AreaView, q: QuestionnaireSummary) {
    if (a.handler === "health") {
      router.push({ pathname: "/onboarding/health", params: { mode: "edit" } });
      return;
    }
    router.push({ pathname: "/profile360/[key]", params: { key: q.key } });
  }

  return (
    <View style={styles.bg}>
      <ScreenHeader
        title={area ? `${area.icon} ${area.name}` : "Mi perfil 360"}
        subtitle={area ? `${area.answered} de ${area.total} respondidas` : "A tu ritmo: cada dato afina tu plan"}
        onBack={area ? () => setAreaKey(null) : undefined}
        fallback="/home"
      >
        {!area && data ? (
          <View style={styles.ringRow}>
            <ProgressRing pct={data.completeness.pct} size={84} color="#fff" trackColor="rgba(255,255,255,0.25)" />
            <View style={{ flex: 1 }}>
              <Text style={styles.ringLabel}>{data.completeness.label}</Text>
              <Text style={styles.ringBody}>Nada es obligatorio. Responde lo que quieras, omite lo que no, y vuelve cuando te apetezca.</Text>
            </View>
          </View>
        ) : null}
      </ScreenHeader>

      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: insets.bottom + 24 }}
        refreshControl={<RefreshControl refreshing={overview.refreshing} onRefresh={overview.reload} tintColor={colors.primary} />}
      >
        {overview.loading && !data ? (
          <View style={styles.grid}>
            {[0, 1, 2, 3, 4, 5].map((i) => (
              <SkeletonBlock key={i} height={112} radius={16} style={styles.cell} />
            ))}
          </View>
        ) : !data ? (
          <ErrorState message={overview.error ?? "No pudimos cargar tu perfil."} onRetry={overview.reload} />
        ) : area ? (
          <AreaDetail area={area} onOpen={(q) => openQuestionnaire(area, q)} />
        ) : (
          <View style={styles.grid}>
            {data.areas.map((a) => (
              <PressableScale
                key={a.key}
                onPress={() => setAreaKey(a.key)}
                accessibilityRole="button"
                accessibilityLabel={`${a.name}, ${Math.round(a.pct)} por ciento${a.sensitive ? ", área privada" : ""}`}
                style={[styles.cell, styles.areaCard]}
              >
                <View style={styles.areaTop}>
                  <Text style={{ fontSize: 24 }}>{a.icon}</Text>
                  {a.sensitive ? <Text style={styles.lock}>{a.consented ? "🔐" : "🔒"}</Text> : null}
                </View>
                <Text style={styles.areaName} numberOfLines={2}>
                  {a.name}
                </Text>
                <View style={{ flex: 1 }} />
                <AnimatedBar pct={a.pct} height={6} />
                <Text style={styles.areaPct}>{`${Math.round(a.pct)}%`}</Text>
              </PressableScale>
            ))}
          </View>
        )}
      </ScrollView>
    </View>
  );
}

function AreaDetail({ area, onOpen }: { area: AreaView; onOpen: (q: QuestionnaireSummary) => void }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={{ gap: 12 }}>
      <Card>
        <Text style={styles.description}>{area.description}</Text>
      </Card>
      {area.sensitive ? (
        <NoticeBox
          tone="info"
          icon={area.consented ? "🔐" : "🔒"}
          title="Área privada"
          body={
            area.consented
              ? "Diste permiso para guardar estos datos. Se guardan cifrados y puedes revocarlo (y borrarlos) desde Cuenta › Privacidad."
              : "Antes de preguntarte te explicaremos qué guardamos y cómo lo protegemos. Solo con tu permiso."
          }
        />
      ) : null}
      <SectionTitle style={{ marginTop: 8 }}>Cuestionarios</SectionTitle>
      {area.questionnaires.map((q, i) => (
        <Card key={q.key} delay={i * 60} style={{ padding: 0 }}>
          <PressableScale onPress={() => onOpen(q)} accessibilityRole="button" style={styles.qRow}>
            <View style={{ flex: 1, gap: 3 }}>
              <Text style={styles.qTitle}>{q.title}</Text>
              <Text style={styles.qSub}>
                {[q.subtitle, q.minutes ? `${q.minutes} min` : null, `${q.answered}/${q.total} respondidas`].filter(Boolean).join(" · ")}
              </Text>
              <AnimatedBar pct={q.pct} height={5} style={{ marginTop: 6 }} />
            </View>
            <Text style={styles.qCta}>{q.pct >= 100 ? "Revisar" : q.answered > 0 ? "Seguir" : "Empezar"} ›</Text>
          </PressableScale>
        </Card>
      ))}
    </View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  ringRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 16, marginTop: 16 },
  ringLabel: { color: "#fff", fontSize: 16, fontWeight: "800" as const },
  ringBody: { color: "rgba(255,255,255,0.85)", fontSize: 12, lineHeight: 17, marginTop: 4 },
  grid: { flexDirection: "row" as const, flexWrap: "wrap" as const, justifyContent: "space-between" as const, rowGap: 12 },
  cell: { width: "48.5%" as const },
  areaCard: { minHeight: 128, backgroundColor: colors.card, borderRadius: 16, padding: 12, gap: 6, ...shadow },
  areaTop: { flexDirection: "row" as const, justifyContent: "space-between" as const, alignItems: "flex-start" as const },
  lock: { fontSize: 14 },
  areaName: { fontSize: 13, fontWeight: "700" as const, color: colors.text, lineHeight: 17 },
  areaPct: { fontSize: 11, color: colors.textFaint, fontWeight: "700" as const },
  description: { fontSize: 14, color: colors.text, lineHeight: 20 },
  qRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, padding: 14 },
  qTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  qSub: { fontSize: 12, color: colors.textMuted },
  qCta: { fontSize: 13, fontWeight: "700" as const, color: colors.primary },
});
