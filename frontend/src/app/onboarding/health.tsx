import { router } from "expo-router";
import { useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import FormScreen from "../../components/forms/FormScreen";
import HealthConsentGate from "../../components/health/HealthConsentGate";
import HealthSummaryCard from "../../components/health/HealthSummaryCard";
import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { useWorld } from "../../context/WorldContext";
import { extractErrorMessage } from "../../services/api";
import { confirmClearance, fetchScreeningForm, saveScreening } from "../../services/healthService";
import { Answers, HealthSummary } from "../../services/types";
import { useTheme } from "../../theme";

/** Chequeo de salud con el consentimiento de datos de salud por delante (si falta). */
export default function HealthRoute() {
  return (
    <HealthConsentGate title="Chequeo de salud">
      <HealthStep />
    </HealthConsentGate>
  );
}

/** Chequeo de salud (opcional, desde Mi Base, Perfil 360 o Cuenta) → resultado (riesgo, avisos y aval). */
function HealthStep() {
  const toast = useToast();
  const { invalidate } = useWorld();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [result, setResult] = useState<HealthSummary | null>(null);
  const [confirming, setConfirming] = useState(false);

  async function submit(answers: Answers) {
    try {
      setResult(await saveScreening(answers));
      // El plan depende del chequeo: las pestañas se recargan (y el servidor lo regenera si hace falta).
      invalidate();
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
  }

  async function clearance() {
    setConfirming(true);
    try {
      setResult(await confirmClearance());
      invalidate();
      toast.success("Gracias. Registramos el aval de tu profesional.");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setConfirming(false);
    }
  }

  function finish() {
    toast.success("Chequeo guardado. Ajustamos tus planes.");
    if (router.canGoBack()) router.back();
    else router.replace("/home");
  }

  function exit() {
    if (router.canGoBack()) router.back();
    else router.replace("/home");
  }

  if (!result) {
    return <FormScreen load={fetchScreeningForm} submitLabel="Ver resultado" onSubmit={submit} onExit={exit} exitIcon="close" />;
  }

  const blocked = result.redFlags || (result.requiresClearance && !result.clearanceConfirmed);
  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title="Tu resultado" subtitle="Así cuidaremos de ti" back={false} />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: insets.bottom + 24 }}>
        <HealthSummaryCard summary={result} onConfirmClearance={clearance} confirming={confirming} />
        {blocked ? (
          <Text style={{ fontSize: 13, color: colors.textMuted, lineHeight: 19 }}>
            Puedes seguir entrenando: te recomendaremos opciones suaves y seguras, y te avisaremos antes de cualquier actividad intensa.
          </Text>
        ) : null}
        <Text style={{ fontSize: 12, color: colors.textFaint, lineHeight: 17 }}>
          Sportacus no diagnostica ni reemplaza la valoración de un profesional de la salud. Tus respuestas se guardan cifradas.
        </Text>
        <GradientButton title="Listo" onPress={finish} />
        <GradientButton title="Corregir respuestas" variant="ghost" onPress={() => setResult(null)} />
      </ScrollView>
    </View>
  );
}
