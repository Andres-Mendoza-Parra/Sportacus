import { router, useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { useToast } from "../../components/ui/Feedback";
import GradientHeader from "../../components/ui/GradientHeader";
import PressableScale from "../../components/ui/PressableScale";
import ProgressRing from "../../components/ui/ProgressRing";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { extractErrorMessage } from "../../services/api";
import { ProfileSuggestion, fetchProfile } from "../../services/profileService";
import {
  QuestionnaireList,
  QuestionnaireSummary,
  fetchQuestionnaireList,
} from "../../services/questionnaireService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

export default function QuestionnaireListScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const [data, setData] = useState<QuestionnaireList | null>(null);
  const [strength, setStrength] = useState<number | null>(null);
  const [suggestions, setSuggestions] = useState<ProfileSuggestion[]>([]);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      setData(await fetchQuestionnaireList());
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setIsRefreshing(false);
    }
    // La fuerza del perfil y las sugerencias son un extra: si fallan, la lista sigue funcionando.
    try {
      const profile = await fetchProfile();
      setStrength(profile.profileStrengthPct);
      setSuggestions(profile.suggestions);
    } catch {
      setStrength(null);
      setSuggestions([]);
    }
  }, [toast]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  if (!data) return <ScreenSkeleton cards={5} />;

  const meter = strength ?? data.overallCompletionPct;
  const statusStyle = (status: QuestionnaireSummary["status"]) =>
    status === "completo"
      ? { bg: colors.successSoft, color: colors.success, label: "Completo" }
      : status === "parcial"
        ? { bg: colors.warnSoft, color: colors.warn, label: "Parcial" }
        : { bg: colors.cardAlt, color: colors.textFaint, label: "Sin iniciar" };

  let cardIndex = 0;

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
      <GradientHeader>
        <Animated.View entering={FadeInDown.duration(400)}>
          <Text style={styles.title}>Mis datos</Text>
          <Text style={styles.subtitle}>Entre más completes, más afinadas serán tus recomendaciones</Text>
          <View style={styles.meterCard}>
            <ProgressRing pct={meter} size={64} stroke={7} color="#fff" trackColor="rgba(255,255,255,0.25)" labelColor="#fff" />
            <View style={{ flex: 1 }}>
              <Text style={styles.meterTitle}>Fuerza del perfil</Text>
              <Text style={styles.meterSub}>
                {meter >= 90
                  ? "¡Excelente! Tu rutina está muy afinada."
                  : meter >= 50
                    ? "Buen avance. Aún puedes afinar más tu rutina."
                    : "Completa las plantillas para personalizar tu rutina."}
              </Text>
            </View>
          </View>
        </Animated.View>
      </GradientHeader>

      {suggestions.length > 0 ? (
        <Animated.View entering={FadeInDown.delay(100).duration(400)}>
          <Text style={styles.groupTitle}>Sugerencias para mejorar</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 16, gap: 10 }}>
            {suggestions.map((s) => (
              <PressableScale key={s.questionnaireKey} style={styles.suggestion} onPress={() => router.push(`/questionnaires/${s.questionnaireKey}`)}>
                <View style={styles.suggestionBadge}>
                  <Text style={styles.suggestionBadgeText}>+{s.impactPct}%</Text>
                </View>
                <Text style={styles.suggestionTitle} numberOfLines={1}>
                  {s.title}
                </Text>
                <Text style={styles.suggestionReason} numberOfLines={3}>
                  {s.reason}
                </Text>
              </PressableScale>
            ))}
          </ScrollView>
        </Animated.View>
      ) : null}

      {data.groups.map((group) => (
        <View key={group.groupTitle}>
          <Text style={styles.groupTitle}>{group.groupTitle}</Text>
          <View style={styles.items}>
            {group.items.map((item) => {
              const st = statusStyle(item.status);
              const delay = 150 + cardIndex++ * 60;
              return (
                <Animated.View key={item.key} entering={FadeInDown.delay(Math.min(delay, 700)).duration(380)}>
                  <PressableScale style={styles.item} onPress={() => router.push(`/questionnaires/${item.key}`)}>
                    <View style={styles.ringWrap}>
                      <ProgressRing pct={item.completionPct} size={50} stroke={5} color={item.status === "completo" ? colors.primary : colors.warn} fontSize={20} label={item.icon} />
                    </View>
                    <View style={styles.itemBody}>
                      <Text style={styles.itemName}>{item.title}</Text>
                      <Text style={styles.itemDesc} numberOfLines={2}>
                        {item.description}
                      </Text>
                      {item.totalCount > 0 ? (
                        <Text style={styles.itemCount}>
                          {item.answeredCount}/{item.totalCount} datos · {item.completionPct}%
                        </Text>
                      ) : null}
                    </View>
                    <View style={[styles.statusBadge, { backgroundColor: st.bg }]}>
                      <Text style={[styles.statusText, { color: st.color }]}>{st.label}</Text>
                    </View>
                  </PressableScale>
                </Animated.View>
              );
            })}
          </View>
        </View>
      ))}

      <View style={{ height: 32 }} />
    </ScrollView>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  title: { color: "#fff", fontSize: 22, fontWeight: "700" as const },
  subtitle: { color: "rgba(255,255,255,0.8)", fontSize: 13, marginTop: 4 },
  meterCard: {
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 14,
    backgroundColor: "rgba(255,255,255,0.16)",
    borderRadius: 18,
    padding: 14,
    marginTop: 16,
  },
  meterTitle: { color: "#fff", fontSize: 15, fontWeight: "700" as const },
  meterSub: { color: "rgba(255,255,255,0.85)", fontSize: 12, marginTop: 3, lineHeight: 17 },
  groupTitle: {
    fontSize: 11,
    color: colors.textFaint,
    fontWeight: "700" as const,
    textTransform: "uppercase" as const,
    letterSpacing: 0.8,
    marginHorizontal: 16,
    marginTop: 20,
    marginBottom: 8,
  },
  suggestion: { width: 200, backgroundColor: colors.card, borderRadius: 16, padding: 14, ...shadow },
  suggestionBadge: { alignSelf: "flex-start" as const, backgroundColor: colors.primarySoft, borderRadius: 8, paddingHorizontal: 8, paddingVertical: 2, marginBottom: 8 },
  suggestionBadgeText: { color: colors.success, fontSize: 11, fontWeight: "700" as const },
  suggestionTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  suggestionReason: { fontSize: 12, color: colors.textMuted, marginTop: 4, lineHeight: 17 },
  items: { marginHorizontal: 16, gap: 10 },
  item: {
    backgroundColor: colors.card,
    borderRadius: 18,
    padding: 14,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 12,
    ...shadow,
  },
  ringWrap: { width: 50, height: 50 },
  itemBody: { flex: 1, minWidth: 0 },
  itemName: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  itemDesc: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  itemCount: { fontSize: 11, color: colors.textFaint, marginTop: 4 },
  statusBadge: { borderRadius: 8, paddingHorizontal: 8, paddingVertical: 3 },
  statusText: { fontSize: 11, fontWeight: "700" as const },
});
