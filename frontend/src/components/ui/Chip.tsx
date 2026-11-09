import React from "react";
import { StyleProp, Text, ViewStyle } from "react-native";

import { useTheme } from "../../theme";
import PressableScale from "./PressableScale";

interface Props {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  accessibilityLabel?: string;
}

/** Chip seleccionable (filtros, opciones múltiples). Sin onPress se muestra como etiqueta estática. */
export default function Chip({ label, selected, onPress, disabled, style, accessibilityLabel }: Props) {
  const { colors } = useTheme();
  return (
    <PressableScale
      onPress={onPress}
      disabled={disabled || !onPress}
      scaleTo={0.94}
      accessibilityRole={onPress ? "button" : "text"}
      accessibilityState={{ selected: !!selected, disabled: !!disabled }}
      accessibilityLabel={accessibilityLabel ?? label}
      style={[
        {
          paddingHorizontal: 13,
          paddingVertical: 8,
          borderRadius: 999,
          borderWidth: 1.5,
          borderColor: selected ? colors.primary : colors.border,
          backgroundColor: selected ? colors.primarySoft : colors.card,
          opacity: disabled ? 0.5 : 1,
        },
        style,
      ]}
    >
      <Text style={{ fontSize: 12, fontWeight: "600", color: selected ? colors.primary : colors.textMuted }}>{label}</Text>
    </PressableScale>
  );
}
