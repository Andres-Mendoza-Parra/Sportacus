import React from "react";
import { Text, View } from "react-native";

import { BlockLog, BlockView, SessionUi } from "../../../services/types";
import { useTheme } from "../../../theme";
import PressableScale from "../../ui/PressableScale";

/** Props comunes de cada renderizador de bloque (uno por `prescription.type`). */
export interface BlockRendererProps {
  block: BlockView;
  entries: BlockLog[];
  onChange: (entries: BlockLog[]) => void;
  ui: SessionUi;
  speak: (text: string) => void;
}

/** Botón grande de acción del reproductor (iniciar, pausar, terminar…). */
export function BigButton({
  label,
  onPress,
  tone = "primary",
  disabled,
}: {
  label: string;
  onPress: () => void;
  tone?: "primary" | "ghost" | "danger";
  disabled?: boolean;
}) {
  const { colors } = useTheme();
  const bg = tone === "primary" ? colors.primary : tone === "danger" ? colors.danger : colors.cardAlt;
  const fg = tone === "ghost" ? colors.text : "#fff";
  return (
    <PressableScale
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      style={{ flex: 1, minHeight: 50, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: bg, opacity: disabled ? 0.5 : 1 }}
    >
      <Text style={{ color: fg, fontSize: 15, fontWeight: "700" }}>{label}</Text>
    </PressableScale>
  );
}

/** Casilla "Serie hecha" de una fila de serie. */
export function SetDoneToggle({ done, onToggle, label }: { done: boolean; onToggle: () => void; label: string }) {
  const { colors } = useTheme();
  return (
    <PressableScale
      onPress={onToggle}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: done }}
      accessibilityLabel={label}
      style={{
        width: 48,
        height: 48,
        borderRadius: 14,
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: done ? colors.primary : colors.cardAlt,
        borderWidth: 1.5,
        borderColor: done ? colors.primary : colors.border,
      }}
    >
      <Text style={{ fontSize: 20, color: done ? "#fff" : colors.textFaint, fontWeight: "800" }}>✓</Text>
    </PressableScale>
  );
}

/** Reloj grande centrado con etiqueta. */
export function BigClock({ value, caption, color }: { value: string; caption?: string; color?: string }) {
  const { colors } = useTheme();
  return (
    <View style={{ alignItems: "center", paddingVertical: 8 }}>
      <Text accessibilityLiveRegion="polite" style={{ fontSize: 56, fontWeight: "800", color: color ?? colors.primary, fontVariant: ["tabular-nums"] }}>
        {value}
      </Text>
      {caption ? <Text style={{ fontSize: 13, color: colors.textMuted }}>{caption}</Text> : null}
    </View>
  );
}

/** Reemplaza la entrada `index` de la lista con `patch`. */
export function patchEntry(entries: BlockLog[], index: number, patch: Partial<BlockLog>): BlockLog[] {
  return entries.map((e, i) => (i === index ? { ...e, ...patch } : e));
}
