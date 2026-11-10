import React from "react";
import { StyleProp, Text, View, ViewStyle } from "react-native";

import { useTheme } from "../../theme";
import PressableScale from "./PressableScale";

interface Props {
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** Texto o contenido (puede incluir enlaces). */
  children: React.ReactNode;
  /** Etiqueta accesible (texto plano) de la casilla. */
  label: string;
  error?: string | null;
  required?: boolean;
  style?: StyleProp<ViewStyle>;
}

/** Casilla de verificación accesible (zona táctil ≥ 44 px) con texto y error en línea. */
export default function CheckRow({ checked, onChange, children, label, error, required, style }: Props) {
  const { colors } = useTheme();
  return (
    <View style={[{ marginBottom: 10 }, style]}>
      <View style={{ flexDirection: "row", alignItems: "flex-start", gap: 10 }}>
        <PressableScale
          onPress={() => onChange(!checked)}
          accessibilityRole="checkbox"
          accessibilityState={{ checked }}
          accessibilityLabel={required ? `${label} (obligatorio)` : label}
          hitSlop={6}
          style={{ width: 44, height: 44, alignItems: "center", justifyContent: "center", marginLeft: -8, marginTop: -10 }}
        >
          <View
            style={{
              width: 24,
              height: 24,
              borderRadius: 7,
              borderWidth: 2,
              borderColor: error ? colors.danger : checked ? colors.primary : colors.textMuted,
              backgroundColor: checked ? colors.primary : "transparent",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            {checked ? <Text style={{ color: colors.onPrimary, fontSize: 14, fontWeight: "800", lineHeight: 16 }}>✓</Text> : null}
          </View>
        </PressableScale>
        <View style={{ flex: 1 }}>{children}</View>
      </View>
      {error ? <Text style={{ fontSize: 12, color: colors.danger, marginTop: 2, marginLeft: 36 }}>{error}</Text> : null}
    </View>
  );
}
