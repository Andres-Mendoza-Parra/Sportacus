import { router } from "expo-router";
import { useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";

import LinkRow from "../../components/account/LinkRow";
import ProfileSummary from "../../components/account/ProfileSummary";
import WorldSwitcher from "../../components/account/WorldSwitcher";
import HealthSummaryCard from "../../components/health/HealthSummaryCard";
import { Card, SectionTitle } from "../../components/ui/Card";
import { useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import GradientHeader from "../../components/ui/GradientHeader";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { ErrorState } from "../../components/ui/States";
import { useWorld } from "../../context/WorldContext";
import { useAccountActions } from "../../hooks/useAccountActions";
import { useLoader, useReloadOnFocus } from "../../hooks/useLoader";
import { extractErrorMessage } from "../../services/api";
import { confirmClearance } from "../../services/healthService";
import { fetchAccount } from "../../services/profileService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

/** Pestaña Cuenta: datos personales, salud, mis mundos (cambio de mundo activo) y acciones de cuenta. */
export default function AccountScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const { active, version } = useWorld();
  const account = useLoader(fetchAccount, [version]);
  const actions = useAccountActions();
  const [confirming, setConfirming] = useState(false);
  useReloadOnFocus(account.reload);

  async function handleClearance() {
    setConfirming(true);
    try {
      const health = await confirmClearance();
      if (account.data) account.setData({ ...account.data, health });
      toast.success("Gracias por confirmarlo. ¡A entrenar con tranquilidad!");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setConfirming(false);
    }
  }

  const data = account.data;
  if (account.loading && !data) return <ScreenSkeleton cards={4} />;
  if (!data) return <ErrorState message={account.error ?? "Sin datos"} onRetry={account.reload} style={{ flex: 1, justifyContent: "center" }} />;

  const initials = (data.name || data.email).trim().slice(0, 2).toUpperCase();

  return (
    <ScrollView
      style={styles.bg}
      contentContainerStyle={{ paddingBottom: 36 }}
      refreshControl={<RefreshControl refreshing={account.refreshing} onRefresh={account.reload} tintColor={colors.primary} />}
    >
      <GradientHeader rounded>
        <View style={styles.headRow}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>{initials}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.name}>{data.name}</Text>
            <Text style={styles.email}>{data.email}</Text>
            {active ? <Text style={styles.world}>{`${active.icon} ${active.name} · ${active.trackName}`}</Text> : null}
          </View>
        </View>
      </GradientHeader>

      <View style={styles.body}>
        <SectionTitle>Mis mundos</SectionTitle>
        <Card>
          <WorldSwitcher />
        </Card>

        <SectionTitle>Mi perfil</SectionTitle>
        <Card>
          <ProfileSummary account={data} />
          <GradientButton
            title="✏️ Editar perfil"
            variant="ghost"
            style={{ marginTop: 14 }}
            onPress={() => router.push({ pathname: "/onboarding/profile", params: { mode: "edit" } })}
          />
        </Card>

        <SectionTitle>Salud</SectionTitle>
        <HealthSummaryCard summary={data.health} compact onConfirmClearance={handleClearance} confirming={confirming} />
        <View style={styles.row}>
          <GradientButton title="Ver detalle" variant="ghost" style={{ flex: 1 }} onPress={() => router.push("/health")} />
          <GradientButton
            title="Repetir chequeo"
            variant="ghost"
            style={{ flex: 1 }}
            onPress={() => router.push({ pathname: "/onboarding/health", params: { mode: "edit" } })}
          />
        </View>

        <SectionTitle>Más</SectionTitle>
        <Card style={{ paddingVertical: 4 }}>
          <LinkRow icon="📚" title="Biblioteca" subtitle="Movimientos de tu mundo" onPress={() => router.push("/library")} />
          <LinkRow icon="🧪" title="Tests" subtitle="Mide tu progreso" onPress={() => router.push("/assessments")} />
          <LinkRow icon="⚙️" title="Ajustes" subtitle="Tema, unidades, recordatorios y contraseña" onPress={() => router.push("/settings")} last />
        </Card>

        <SectionTitle>Privacidad y datos</SectionTitle>
        <Card style={{ paddingVertical: 4 }}>
          <LinkRow icon="📥" title="Exportar mis datos" subtitle="Descarga una copia en JSON" onPress={actions.exportData} loading={actions.exporting} />
          <LinkRow icon="🚪" title="Cerrar sesión" onPress={actions.signOut} />
          <LinkRow icon="🗑️" title="Eliminar mi cuenta" subtitle="Borrado total y permanente" onPress={actions.removeAccount} loading={actions.deleting} danger last />
        </Card>
      </View>
    </ScrollView>
  );
}

const createStyles = ({ colors }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  headRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 14 },
  avatar: { width: 60, height: 60, borderRadius: 30, backgroundColor: "rgba(255,255,255,0.22)", alignItems: "center" as const, justifyContent: "center" as const },
  avatarText: { color: "#fff", fontSize: 22, fontWeight: "800" as const },
  name: { color: "#fff", fontSize: 20, fontWeight: "800" as const },
  email: { color: "rgba(255,255,255,0.8)", fontSize: 12, marginTop: 1 },
  world: { color: "#fff", fontSize: 12, fontWeight: "700" as const, marginTop: 6 },
  body: { paddingHorizontal: 16 },
  row: { flexDirection: "row" as const, gap: 10, marginTop: 10 },
});
