import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import PainSlider from "../../components/session/checks/PainSlider";
import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { useWorld } from "../../context/WorldContext";
import { errorStatus, extractErrorMessage } from "../../services/api";
import { reportNextDayPain } from "../../services/worldService";
import { useTheme } from "../../theme";

/** Chequeo de dolor a las 24 h de una sesión (modelo de monitorización del dolor). */
export default function PainCheckScreen() {
  const { logId } = useLocalSearchParams<{ logId: string }>();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const { invalidate } = useWorld();
  const [pain, setPain] = useState<number | undefined>(undefined);
  const [saving, setSaving] = useState(false);

  async function save() {
    if (pain == null) {
      toast.info("Mueve la barra para indicar tu dolor (0 si no tienes).");
      return;
    }
    setSaving(true);
    try {
      await reportNextDayPain(String(logId), pain);
      toast.success(pain <= 3 ? "¡Gracias! Vas por buen camino." : "Gracias: ajustaremos tu plan con este dato.");
      invalidate();
      if (router.canGoBack()) router.back();
      else router.replace("/home");
    } catch (error) {
      if (errorStatus(error) === 409) {
        // Ya registrado o fuera de plazo (72 h): es un caso normal, no un error.
        toast.info(extractErrorMessage(error));
        if (router.canGoBack()) router.back();
        else router.replace("/home");
        return;
      }
      toast.error(extractErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title="Chequeo de 24 horas" subtitle="Cómo respondió tu cuerpo a la última sesión" />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 14 }}>
        <Text style={{ fontSize: 22, fontWeight: "800", color: colors.text }}>¿Cómo amaneciste?</Text>
        <Text style={{ fontSize: 14, color: colors.textMuted, lineHeight: 20 }}>
          Indica el dolor que sientes hoy en la zona que trabajaste. Si es 3 o menos, seguimos avanzando; si es mayor, ajustamos la carga.
        </Text>
        <PainSlider value={pain} onChange={setPain} label="Dolor de hoy" />
      </ScrollView>
      <View style={{ padding: 16, paddingBottom: 16 + insets.bottom, backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border }}>
        <GradientButton title="Guardar" onPress={save} loading={saving} />
      </View>
    </View>
  );
}
