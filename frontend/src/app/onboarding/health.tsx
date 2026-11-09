import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import FormScreen from "../../components/forms/FormScreen";
import HealthSummaryCard from "../../components/health/HealthSummaryCard";
import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { parseMode } from "../../navigation/onboarding";
import { extractErrorMessage } from "../../services/api";
import { confirmClearance, fetchScreeningForm, saveScreening } from "../../services/healthService";
import { Answers, HealthSummary } from "../../services/types";
import { useTheme } from "../../theme";

/** Paso 2: chequeo de salud universal → resultado (riesgo, avisos y aval profesional). */
export default function HealthStep() {
  const { mode: rawMode } = useLocalSearchParams<{ mode?: string }>();
  const mode = parseMode(rawMode);
  const toast = useToast();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [result, setResult] = useState<HealthSummary | null>(null);
  const [confirming, setConfirming] = useState(false);

  async function submit(answers: Answers) {
    try {
      setResult(await saveScreening(answers));
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
  }

  async function clearance() {
    setConfirming(true);
    try {
      setResult(await confirmClearance());
      toast.success("Gracias. Registramos el aval de tu profesional.");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setConfirming(false);
    }
  }

  function finish() {
    if (mode === "edit") {
      toast.success("Chequeo actualizado. Ajustamos tus planes.");
      if (router.canGoBack()) router.back();
      else router.replace("/account");
      return;
    }
    router.replace("/onboarding/choose");
  }

  function exit() {
    if (router.canGoBack()) router.back();
    else router.replace(mode === "edit" ? "/account" : "/onboarding/profile");
  }

  if (!result) {
    return <FormScreen load={fetchScreeningForm} submitLabel="Ver resultado" onSubmit={submit} onExit={exit} exitIcon={mode === "edit" ? "close" : "back"} />;
  }

  const blocked = result.redFlags || (result.requiresClearance && !result.clearanceConfirmed);
  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title="Tu resultado" subtitle="Así cuidaremos de ti" back={false} />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: insets.bottom + 24 }}>
        <HealthSummaryCard summary={result} onConfirmClearance={clearance} confirming={confirming} />
        {blocked ? (
          <Text style={{ fontSize: 13, color: colors.textMuted, lineHeight: 19 }}>
            Puedes seguir y elegir tu mundo: te recomendaremos opciones suaves y seguras, y te avisaremos antes de cualquier actividad intensa.
          </Text>
        ) : null}
        <Text style={{ fontSize: 12, color: colors.textFaint, lineHeight: 17 }}>
          Sportacus no diagnostica ni reemplaza la valoración de un profesional de la salud. Tus respuestas se guardan cifradas.
        </Text>
        <GradientButton title={mode === "edit" ? "Listo" : "Continuar"} onPress={finish} />
        <GradientButton title="Corregir respuestas" variant="ghost" onPress={() => setResult(null)} />
      </ScrollView>
    </View>
  );
}
