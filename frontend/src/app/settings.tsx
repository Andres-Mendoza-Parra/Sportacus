import Constants from "expo-constants";
import { ScrollView, Text, View } from "react-native";

import AccountSection from "../components/settings/AccountSection";
import RemindersSection from "../components/settings/RemindersSection";
import { Segment, SettingsSection, createStyles } from "../components/settings/SettingsSection";
import GradientButton from "../components/ui/GradientButton";
import ScreenHeader from "../components/ui/ScreenHeader";
import { useWorld } from "../context/WorldContext";
import { useAccountActions } from "../hooks/useAccountActions";
import { HeightUnit, ThemePreference, WeightUnit, useThemedStyles, useTheme } from "../theme";

const THEME_OPTIONS: { value: ThemePreference; label: string }[] = [
  { value: "system", label: "🌗 Sistema" },
  { value: "light", label: "☀️ Claro" },
  { value: "dark", label: "🌙 Oscuro" },
];
const WEIGHT_OPTIONS: { value: WeightUnit; label: string }[] = [
  { value: "kg", label: "Kilogramos (kg)" },
  { value: "lb", label: "Libras (lb)" },
];
const HEIGHT_OPTIONS: { value: HeightUnit; label: string }[] = [
  { value: "cm", label: "Centímetros (cm)" },
  { value: "ft", label: "Pies y pulgadas" },
];

/** Ajustes: tema, unidades, recordatorios, cuenta y datos. El color lo pone el mundo activo. */
export default function SettingsScreen() {
  const s = useThemedStyles(createStyles);
  const theme = useTheme();
  const { active } = useWorld();
  const actions = useAccountActions();
  const version = Constants.expoConfig?.version ?? "1.0.0";

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bg }}>
      <ScreenHeader title="Ajustes" subtitle="Personaliza la app a tu gusto" fallback="/account" />
      <ScrollView contentContainerStyle={{ paddingBottom: 40 }} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
        <SettingsSection title="Apariencia" delay={0}>
          <Text style={s.label}>Tema</Text>
          <Segment options={THEME_OPTIONS} value={theme.preference} onChange={theme.setPreference} />
          <Text style={[s.rowSub, { marginTop: 10 }]}>
            {active
              ? `El color de la app lo pone tu mundo activo (${active.icon} ${active.name}). Cambia de mundo en Cuenta → Mis mundos.`
              : "El color de la app lo pone tu mundo activo."}
          </Text>
        </SettingsSection>

        <RemindersSection delay={60} />

        <SettingsSection title="Unidades" delay={120}>
          <Text style={s.label}>Peso</Text>
          <Segment options={WEIGHT_OPTIONS} value={theme.weightUnit} onChange={theme.setWeightUnit} />
          <Text style={[s.label, { marginTop: 14 }]}>Altura</Text>
          <Segment options={HEIGHT_OPTIONS} value={theme.heightUnit} onChange={theme.setHeightUnit} />
          <Text style={[s.rowSub, { marginTop: 8 }]}>Solo cambia cómo se muestran tus medidas; los formularios y las cargas siguen en kg y cm.</Text>
        </SettingsSection>

        <AccountSection delay={180} />

        <SettingsSection title="Privacidad y datos" delay={240}>
          <Text style={s.rowSub}>
            Tus datos de salud se guardan cifrados, se usan solo para personalizar tu plan y no se comparten con terceros.
          </Text>
          <GradientButton title="📥 Exportar mis datos" variant="ghost" onPress={actions.exportData} loading={actions.exporting} style={{ marginTop: 12 }} />
          <GradientButton title="Eliminar mi cuenta" variant="danger" onPress={actions.removeAccount} loading={actions.deleting} style={{ marginTop: 10 }} />
        </SettingsSection>

        <SettingsSection title="Acerca de" delay={300}>
          <Text style={s.rowTitle}>Sportacus · versión {version}</Text>
          <Text style={[s.rowSub, { marginTop: 6 }]}>
            Las recomendaciones son orientativas y no sustituyen la valoración de un profesional de la salud.
          </Text>
        </SettingsSection>
      </ScrollView>
    </View>
  );
}
