import React, { useState } from "react";
import { StyleProp, Text, TextInput, View, ViewStyle } from "react-native";

import { useTheme } from "../../theme";
import { parseDecimal } from "./format";

interface Props {
  label: string;
  value: number | undefined;
  onChange: (value: number | undefined) => void;
  unit?: string;
  /** Texto pequeño bajo la etiqueta (p. ej. "sugerido"). */
  hint?: string;
  decimal?: boolean;
  editable?: boolean;
  style?: StyleProp<ViewStyle>;
}

/** Campo numérico compacto (acepta coma decimal) para series, cargas, distancias… */
export default function NumberField({ label, value, onChange, unit, hint, decimal = true, editable = true, style }: Props) {
  const { colors } = useTheme();
  const [text, setText] = useState(value == null ? "" : String(value).replace(".", ","));
  const [lastValue, setLastValue] = useState(value);

  // Si el valor cambia desde fuera (p. ej. al terminar un cronómetro), se refleja en el texto.
  if (value !== lastValue) {
    setLastValue(value);
    if (parseDecimal(text) !== value) setText(value == null ? "" : String(value).replace(".", ","));
  }

  return (
    <View style={[{ flex: 1, minWidth: 64 }, style]}>
      <Text style={{ fontSize: 10, fontWeight: "700", color: colors.textFaint, textTransform: "uppercase" }}>
        {label}
        {hint ? <Text style={{ color: colors.primary, textTransform: "none" }}> · {hint}</Text> : null}
      </Text>
      <View
        style={{
          flexDirection: "row",
          alignItems: "center",
          marginTop: 4,
          borderWidth: 1.5,
          borderColor: colors.border,
          borderRadius: 12,
          backgroundColor: editable ? colors.inputBg : colors.cardAlt,
          paddingHorizontal: 10,
        }}
      >
        <TextInput
          accessibilityLabel={label}
          value={text}
          editable={editable}
          keyboardType={decimal ? "decimal-pad" : "number-pad"}
          placeholder="—"
          placeholderTextColor={colors.textFaint}
          onChangeText={(t) => {
            setText(t);
            const n = parseDecimal(t);
            setLastValue(n);
            onChange(n);
          }}
          style={{ flex: 1, paddingVertical: 9, fontSize: 17, fontWeight: "700", color: colors.text, minWidth: 30 }}
        />
        {unit ? <Text style={{ fontSize: 12, color: colors.textMuted }}>{unit}</Text> : null}
      </View>
    </View>
  );
}
