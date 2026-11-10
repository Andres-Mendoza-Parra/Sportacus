import { router, useLocalSearchParams } from "expo-router";
import { useMemo, useState } from "react";
import { ScrollView, Text, View } from "react-native";
import Animated, { FadeInDown, ZoomIn } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { changedAnswers, initialAnswers } from "../../components/forms/formLogic";
import FormWizard from "../../components/forms/FormWizard";
import ConsentSheet from "../../components/profile360/ConsentSheet";
import NextItemCard from "../../components/profile360/NextItemCard";
import ProgressRing from "../../components/profile360/ProgressRing";
import { Card, SectionTitle } from "../../components/ui/Card";
import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { EmptyState, ErrorState } from "../../components/ui/States";
import { useWorld } from "../../context/WorldContext";
import { useLoader } from "../../hooks/useLoader";
import { extractErrorMessage } from "../../services/api";
import { fetchQuestionnaire, saveQuestionnaire, setConsent, skipQuestion } from "../../services/profile360Service";
import { Answers, NextItem, SaveResult } from "../../services/types";
import { useTheme } from "../../theme";

/**
 * Un cuestionario del Perfil 360. Áreas sensibles sin permiso → primero el consentimiento.
 * Todo se puede omitir; "Prefiero no decir" se registra al momento; se guarda solo lo que cambió.
 */
export default function QuestionnaireScreen() {
  const { key } = useLocalSearchParams<{ key: string }>();
  const { colors } = useTheme();
  const toast = useToast();
  const { invalidate } = useWorld();
  const view = useLoader(() => fetchQuestionnaire(key), [key]);
  const [consenting, setConsenting] = useState(false);
  const [declinedCount, setDeclinedCount] = useState(0);
  const [result, setResult] = useState<SaveResult | null>(null);

  const initial = useMemo(() => (view.data ? initialAnswers(view.data.form) : {}), [view.data]);

  function exit() {
    if (router.canGoBack()) router.back();
    else router.replace("/profile360");
  }

  async function accept() {
    if (!view.data) return;
    setConsenting(true);
    try {
      await setConsent(view.data.areaKey, true);
      toast.success("Gracias por tu confianza. Puedes revocarlo cuando quieras.");
      await view.reload();
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setConsenting(false);
    }
  }

  async function decline(questionId: string) {
    try {
      await skipQuestion(questionId, "no_decir");
      setDeclinedCount((n) => n + 1);
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
  }

  async function submit(answers: Answers) {
    const changed = changedAnswers(initial, answers);
    if (Object.keys(changed).length === 0) {
      toast.info(declinedCount > 0 ? "Listo, lo tendremos en cuenta." : "No hubo cambios. ¡Vuelve cuando quieras!");
      exit();
      return;
    }
    try {
      const saved = await saveQuestionnaire(key, changed);
      invalidate();
      setResult(saved);
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
  }

  if (view.loading && !view.data) return <ScreenSkeleton cards={4} />;
  const data = view.data;
  if (!data) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <ScreenHeader title="Cuestionario" fallback="/profile360" />
        <ErrorState message={view.error ?? "Cuestionario no disponible."} onRetry={view.reload} />
      </View>
    );
  }

  if (result) return <SavedView result={result} areaName={data.areaName} onClose={exit} />;

  if (data.sensitive && !data.consented) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <ScreenHeader title={data.form.title} subtitle={data.areaName} fallback="/profile360" />
        <ConsentSheet areaName={data.areaName} loading={consenting} onAccept={accept} onDecline={exit} />
      </View>
    );
  }

  if (data.form.questions.length === 0) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <ScreenHeader title={data.form.title} subtitle={data.areaName} fallback="/profile360" />
        <EmptyState icon="🧩" title="No hay preguntas por ahora" body="Vuelve más adelante." actionLabel="Volver" onAction={exit} />
      </View>
    );
  }

  return (
    <FormWizard
      key={data.key}
      spec={data.form}
      meta={data.meta}
      optional
      onDecline={decline}
      submitLabel="Guardar"
      onSubmit={submit}
      onExit={exit}
      exitIcon="close"
    />
  );
}

function SavedView({ result, areaName, onClose }: { result: SaveResult; areaName: string; onClose: () => void }) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [next, setNext] = useState<NextItem | null>(result.next[0] ?? null);

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title="¡Guardado!" subtitle={areaName} back={false} />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: insets.bottom + 24 }}>
        <Animated.View entering={ZoomIn.springify().damping(14)} style={{ alignItems: "center", gap: 8, paddingVertical: 8 }}>
          <ProgressRing pct={result.completeness.pct} size={110} caption="tu perfil" />
          <Text style={{ fontSize: 16, fontWeight: "800", color: colors.text }}>{result.completeness.label}</Text>
          <Text style={{ fontSize: 13, color: colors.textMuted }}>
            {`Guardamos ${result.saved} ${result.saved === 1 ? "respuesta" : "respuestas"}.`}
          </Text>
        </Animated.View>

        {result.highlights.length > 0 ? (
          <Card delay={80} style={{ gap: 6 }}>
            {result.highlights.map((h) => (
              <Text key={h} style={{ fontSize: 13, color: colors.text, lineHeight: 19 }}>
                {`✨ ${h}`}
              </Text>
            ))}
          </Card>
        ) : null}

        {next ? (
          <Animated.View entering={FadeInDown.delay(140).duration(350)}>
            <SectionTitle style={{ marginTop: 4 }}>¿Una más?</SectionTitle>
            <Card>
              <NextItemCard key={next.id} item={next} onDone={(n) => setNext(n)} />
            </Card>
          </Animated.View>
        ) : null}

        <GradientButton title="Listo" onPress={onClose} />
      </ScrollView>
    </View>
  );
}
