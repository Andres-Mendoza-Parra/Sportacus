import { router } from "expo-router";
import { useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import HealthSummaryCard from "../components/health/HealthSummaryCard";
import { useToast } from "../components/ui/Feedback";
import GradientButton from "../components/ui/GradientButton";
import ScreenHeader from "../components/ui/ScreenHeader";
import { SkeletonBlock } from "../components/ui/Skeleton";
import { ErrorState } from "../components/ui/States";
import { useWorld } from "../context/WorldContext";
import { useLoader, useReloadOnFocus } from "../hooks/useLoader";
import { extractErrorMessage } from "../services/api";
import { confirmClearance, fetchHealthSummary } from "../services/healthService";
import { useTheme } from "../theme";

/** Mi salud: resumen del chequeo, confirmación del aval profesional y repetir el chequeo. */
export default function HealthScreen() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const summary = useLoader(fetchHealthSummary);
  const [confirming, setConfirming] = useState(false);
  const { invalidate } = useWorld();
  useReloadOnFocus(summary.reload);

  async function clearance() {
    setConfirming(true);
    try {
      summary.setData(await confirmClearance());
      invalidate();
      toast.success("Registramos el aval de tu profesional.");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setConfirming(false);
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title="Mi salud" subtitle="Lo que tenemos en cuenta para cuidarte" fallback="/account" />
      <ScrollView
        contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: insets.bottom + 24 }}
        refreshControl={<RefreshControl refreshing={summary.refreshing} onRefresh={summary.reload} tintColor={colors.primary} />}
      >
        {summary.loading ? (
          <SkeletonBlock height={220} radius={18} />
        ) : summary.data ? (
          <HealthSummaryCard summary={summary.data} onConfirmClearance={clearance} confirming={confirming} />
        ) : (
          <ErrorState message={summary.error ?? "Sin datos de salud."} onRetry={summary.reload} />
        )}
        <GradientButton
          title={summary.data?.screened ? "Repetir el chequeo de salud" : "Hacer el chequeo de salud"}
          onPress={() => router.push({ pathname: "/onboarding/health", params: { mode: "edit" } })}
        />
        <Text style={{ fontSize: 12, color: colors.textFaint, lineHeight: 17 }}>
          Repite el chequeo si aparece una lesión nueva, te operan o cambia tu estado de salud. Sportacus no diagnostica ni reemplaza la
          valoración de un profesional.
        </Text>
      </ScrollView>
    </View>
  );
}
