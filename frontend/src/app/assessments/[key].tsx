import { useKeepAwake } from "expo-keep-awake";
import { router, useLocalSearchParams } from "expo-router";
import { useCallback, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import AssessmentIntro from "../../components/assessments/AssessmentIntro";
import AssessmentResultView from "../../components/assessments/AssessmentResultView";
import CountRunner from "../../components/assessments/CountRunner";
import ManualRunner from "../../components/assessments/ManualRunner";
import StopwatchRunner from "../../components/assessments/StopwatchRunner";
import TimerDistanceRunner from "../../components/assessments/TimerDistanceRunner";
import { useConfirm, useToast } from "../../components/ui/Feedback";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { EmptyState, ErrorState } from "../../components/ui/States";
import { useWorld } from "../../context/WorldContext";
import { useLoader } from "../../hooks/useLoader";
import { extractErrorMessage } from "../../services/api";
import { AssessmentResult, AssessmentView } from "../../services/types";
import { fetchAssessments, recordAssessment } from "../../services/worldService";
import { useTheme } from "../../theme";

type Phase = "intro" | "run" | "result";

/** Mantiene la pantalla encendida solo mientras está montado (fase de ejecución). */
function KeepAwake() {
  useKeepAwake();
  return null;
}

/** Ejecutor de un test: introducción → ejecución según `definition.kind` → resultado. */
export default function AssessmentRunnerScreen() {
  const { key } = useLocalSearchParams<{ key: string }>();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const confirm = useConfirm();
  const { invalidate } = useWorld();
  const { data, error, loading, reload } = useLoader(fetchAssessments, [key]);
  const [phase, setPhase] = useState<Phase>("intro");
  const [active, setActive] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<AssessmentResult | null>(null);

  const item: AssessmentView | undefined = data?.find((a) => a.definition.key === key);
  const onActiveChange = useCallback((value: boolean) => setActive(value), []);

  function leave() {
    if (router.canGoBack()) router.back();
    else router.replace("/assessments");
  }

  async function handleBack() {
    if (phase === "result") {
      invalidate();
      return leave();
    }
    if (phase === "run") {
      const ok = await confirm({
        title: "¿Salir del test?",
        message: active ? "El test está en marcha y se perderá el resultado." : "No se guardará ningún resultado.",
        confirmText: "Salir",
        destructive: true,
      });
      if (!ok) return;
    }
    leave();
  }

  async function submit(value: number) {
    if (!item) return;
    setSubmitting(true);
    try {
      const res = await recordAssessment(item.definition.key, value);
      setResult(res);
      setPhase("result");
    } catch (e) {
      toast.error(extractErrorMessage(e));
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <ScreenSkeleton cards={3} />;

  const d = item?.definition;

  function renderRunner() {
    if (!d) return null;
    const common = { submitting, onSubmit: submit, onActiveChange };
    switch (d.kind) {
      case "count":
        return <CountRunner durationS={d.durationS} unit={d.unit} {...common} />;
      case "stopwatch":
        return <StopwatchRunner unit={d.unit} {...common} />;
      case "timer_distance":
        return <TimerDistanceRunner durationS={d.durationS} {...common} />;
      default:
        return <ManualRunner unit={d.unit} submitting={submitting} onSubmit={submit} />;
    }
  }

  return (
    <KeyboardAvoidingView style={{ flex: 1, backgroundColor: colors.bg }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      {phase === "run" ? <KeepAwake /> : null}
      <ScreenHeader title={d?.name ?? "Test"} subtitle={phase === "result" ? "Resultado" : phase === "run" ? "En curso" : "Antes de empezar"} onBack={handleBack} />
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 32 + insets.bottom }} keyboardShouldPersistTaps="handled">
        {error && !data ? (
          <ErrorState message={error} onRetry={reload} />
        ) : !item ? (
          <EmptyState icon="🔍" title="No encontramos este test" body="Puede que no pertenezca a tu mundo activo." actionLabel="Ver los tests" onAction={() => router.replace("/assessments")} />
        ) : phase === "intro" ? (
          <AssessmentIntro item={item} onStart={() => setPhase("run")} />
        ) : phase === "run" ? (
          <View>{renderRunner()}</View>
        ) : result ? (
          <AssessmentResultView
            result={result}
            onDone={() => {
              invalidate();
              leave();
            }}
          />
        ) : null}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
