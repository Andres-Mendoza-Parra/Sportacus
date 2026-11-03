import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useState } from "react";
import { ActivityIndicator, ScrollView, Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import GradientHeader from "../../components/ui/GradientHeader";
import { useAuth } from "../../context/AuthContext";
import { extractErrorMessage } from "../../services/api";
import { RoutineOption, fetchRoutineOptions, selectRoutine } from "../../services/routineService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

/**
 * Elige el módulo (disciplina) del usuario: gimnasio, calistenia, running, yoga…
 * Se usa dos veces con la misma pantalla:
 * - Onboarding (sin `mode`): paso obligatorio tras el registro, antes de entrar a la app.
 * - Cambio posterior (`?mode=switch`): desde la cuenta, para elegir otro enfoque cuando quiera.
 */
export default function ModuleOnboardingScreen() {
  const { mode } = useLocalSearchParams<{ mode?: string }>();
  const isSwitch = mode === "switch";
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const { completeOnboarding } = useAuth();

  const [options, setOptions] = useState<RoutineOption[] | null>(null);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  useEffect(() => {
    fetchRoutineOptions()
      .then(setOptions)
      .catch((error) => toast.error(extractErrorMessage(error)));
  }, [toast]);

  async function confirm(activityId: string, name: string) {
    setConfirmingId(activityId);
    try {
      await selectRoutine(activityId);
      if (isSwitch) {
        toast.success(`Ahora entrenas con: ${name}`);
        if (router.canGoBack()) router.back();
        else router.replace("/home");
      } else {
        await completeOnboarding();
        router.replace("/home");
      }
    } catch (error) {
      toast.error(extractErrorMessage(error));
      setConfirmingId(null);
    }
  }

  return (
    <View style={styles.bg}>
      <GradientHeader rounded style={{ paddingBottom: 24 }}>
        <Animated.View entering={FadeInDown.duration(400)}>
          <Text style={styles.kicker}>{isSwitch ? "CAMBIAR MÓDULO" : "ÚLTIMO PASO"}</Text>
          <Text style={styles.title}>{isSwitch ? "Elige tu nuevo enfoque" : "¿En qué quieres enfocarte?"}</Text>
          <Text style={styles.subtitle}>
            Con esto armamos tu rutina y tu biblioteca de ejercicios: solo verás lo que aplica a tu disciplina.
          </Text>
        </Animated.View>
      </GradientHeader>

      {!options ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <ScrollView contentContainerStyle={{ padding: 16, gap: 12 }} showsVerticalScrollIndicator={false}>
          {options.map((opt, idx) => (
            <Animated.View key={opt.activityId} entering={FadeInDown.delay(80 + idx * 60).duration(380)}>
              <View style={[styles.card, idx === 0 && styles.cardTop]}>
                {idx === 0 ? <Text style={styles.topBadge}>⭐ Recomendado para ti</Text> : null}
                <View style={styles.cardHead}>
                  <Text style={styles.cardName}>{opt.name}</Text>
                  <View style={styles.scorePill}>
                    <Text style={styles.scorePillText}>{Math.round(opt.score)}</Text>
                  </View>
                </View>
                <Text style={styles.cardRationale} numberOfLines={3}>
                  {opt.rationale || opt.description}
                </Text>
                <Text style={styles.cardMeta}>
                  {[opt.intensityLabel, opt.durationMin ? `${opt.durationMin} min` : null, opt.environment]
                    .filter(Boolean)
                    .join(" · ")}
                </Text>
                {opt.selected && isSwitch ? (
                  <Text style={styles.current}>✓ Tu módulo actual</Text>
                ) : (
                  <GradientButton
                    title="Elegir este enfoque"
                    style={{ marginTop: 12 }}
                    loading={confirmingId === opt.activityId}
                    disabled={confirmingId !== null}
                    onPress={() => confirm(opt.activityId, opt.name)}
                  />
                )}
              </View>
            </Animated.View>
          ))}
          <View style={{ height: 24 }} />
        </ScrollView>
      )}
    </View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  kicker: { color: "rgba(255,255,255,0.8)", fontSize: 11, fontWeight: "800" as const, letterSpacing: 1 },
  title: { color: "#fff", fontSize: 24, fontWeight: "800" as const, marginTop: 4 },
  subtitle: { color: "rgba(255,255,255,0.85)", fontSize: 13, marginTop: 8, lineHeight: 19 },
  card: { backgroundColor: colors.card, borderRadius: 18, padding: 16, borderWidth: 1.5, borderColor: "transparent", ...shadow },
  cardTop: { borderColor: colors.primary },
  topBadge: { color: colors.primary, fontSize: 12, fontWeight: "700" as const, marginBottom: 8 },
  cardHead: { flexDirection: "row" as const, alignItems: "center" as const, gap: 10 },
  cardName: { flex: 1, fontSize: 17, fontWeight: "800" as const, color: colors.text },
  scorePill: { backgroundColor: colors.primary, borderRadius: 12, paddingHorizontal: 10, paddingVertical: 4 },
  scorePillText: { color: "#fff", fontWeight: "700" as const, fontSize: 13 },
  cardRationale: { fontSize: 13, color: colors.textMuted, marginTop: 8, lineHeight: 18 },
  cardMeta: { fontSize: 11, color: colors.textFaint, marginTop: 8 },
  current: { color: colors.primary, fontWeight: "700" as const, marginTop: 12, fontSize: 13 },
});
