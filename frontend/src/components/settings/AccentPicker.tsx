import { LinearGradient } from "expo-linear-gradient";
import React from "react";
import { Text, View } from "react-native";

import { useWorld } from "../../context/WorldContext";
import { track } from "../../services/analytics";
import { ACCENTS, ACCENT_KEYS, AccentChoice, Theme, deriveAccent, useThemedStyles, useTheme } from "../../theme";
import { useToast } from "../ui/Feedback";
import GradientButton from "../ui/GradientButton";
import PressableScale from "../ui/PressableScale";

interface Option {
  key: AccentChoice;
  label: string;
  color: string;
  icon?: string;
}

/** Grilla de colores (con ✓ el activo) + vista previa mini que cambia al instante. */
export default function AccentPicker() {
  const styles = useThemedStyles(createStyles);
  const theme = useTheme();
  const toast = useToast();
  const { active } = useWorld();
  const worldHex = active?.color ?? theme.worldColor;

  const options: Option[] = [
    {
      key: "mundo",
      label: "Color de mi mundo",
      color: worldHex ? deriveAccent(worldHex).main : ACCENTS.verde.main,
      icon: active?.icon ?? "🌍",
    },
    ...ACCENT_KEYS.map((key) => ({ key, label: ACCENTS[key].label, color: ACCENTS[key].main })),
  ];

  return (
    <View>
      <View style={styles.grid} accessibilityRole="radiogroup" accessibilityLabel="Color de la app">
        {options.map((o) => {
          const selected = theme.accent === o.key;
          return (
            <PressableScale
              key={o.key}
              onPress={() => {
                theme.setAccent(o.key);
                track("palette_changed", { accent: String(o.key) });
              }}
              scaleTo={0.9}
              style={styles.option}
              accessibilityRole="radio"
              accessibilityLabel={o.key === "mundo" && active ? `Color de mi mundo (${active.name})` : o.label}
              accessibilityState={{ checked: selected }}
            >
              <View style={[styles.swatch, { backgroundColor: o.color }, selected && styles.swatchSelected]}>
                {selected ? <Text style={styles.check}>✓</Text> : o.icon ? <Text style={{ fontSize: 18 }}>{o.icon}</Text> : null}
              </View>
              <Text style={[styles.label, selected && styles.labelSelected]} numberOfLines={2}>
                {o.label}
              </Text>
            </PressableScale>
          );
        })}
      </View>

      <Text style={styles.previewLabel}>Vista previa</Text>
      <View style={styles.preview} accessibilityLabel="Vista previa del color elegido">
        <LinearGradient colors={theme.colors.headerGradient} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.previewHeader}>
          <Text style={styles.previewTitle}>{active ? `${active.icon} ${active.name}` : "Sportacus"}</Text>
          <Text style={styles.previewSub}>Así se verán tus cabeceras</Text>
        </LinearGradient>
        <View style={{ padding: 12, gap: 10 }}>
          <View style={{ flexDirection: "row", gap: 6 }}>
            <View style={styles.previewChip}>
              <Text style={styles.previewChipText}>Chip activo</Text>
            </View>
            <View style={[styles.previewChip, { backgroundColor: theme.colors.cardAlt }]}>
              <Text style={[styles.previewChipText, { color: theme.colors.textMuted }]}>Otro</Text>
            </View>
          </View>
          <GradientButton title="Botón principal" onPress={() => toast.info("¡Así se verá tu app!")} />
        </View>
      </View>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  grid: { flexDirection: "row" as const, flexWrap: "wrap" as const, rowGap: 14, marginTop: 4 },
  option: { width: "20%" as const, minWidth: 64, alignItems: "center" as const, gap: 6 },
  swatch: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center" as const,
    justifyContent: "center" as const,
    borderWidth: 3,
    borderColor: "transparent",
  },
  swatchSelected: { borderColor: colors.text },
  check: { color: "#fff", fontWeight: "800" as const, fontSize: 18 },
  label: { fontSize: 11, color: colors.textMuted, textAlign: "center" as const },
  labelSelected: { color: colors.text, fontWeight: "700" as const },
  previewLabel: { fontSize: 12, color: colors.textMuted, fontWeight: "600" as const, marginTop: 18, marginBottom: 8 },
  preview: { borderRadius: 16, overflow: "hidden" as const, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.bg },
  previewHeader: { paddingVertical: 14, paddingHorizontal: 14 },
  previewTitle: { color: "#fff", fontSize: 16, fontWeight: "800" as const },
  previewSub: { color: "rgba(255,255,255,0.85)", fontSize: 12, marginTop: 2 },
  previewChip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 999, backgroundColor: colors.primarySoft },
  previewChipText: { fontSize: 12, fontWeight: "700" as const, color: colors.primary },
});
