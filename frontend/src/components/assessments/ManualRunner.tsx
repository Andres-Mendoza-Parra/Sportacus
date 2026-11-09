import React, { useState } from "react";
import { Text, TextInput, View } from "react-native";

import { formatNumber } from "../../constants/labels";
import { Theme, useTheme, useThemedStyles } from "../../theme";
import { Card } from "../ui/Card";
import GradientButton from "../ui/GradientButton";
import { parseDecimal } from "./shared";

interface Props {
  unit?: string | null;
  /** Valor propuesto (p. ej. el conteo o el tiempo medido) que el usuario puede corregir. */
  initial?: number | null;
  label?: string;
  submitting: boolean;
  onSubmit: (value: number) => void;
}

/** Entrada numérica del resultado (acepta coma decimal) y botón para guardarlo. */
export default function ManualRunner({ unit, initial, label = "Escribe tu resultado", submitting, onSubmit }: Props) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const [text, setText] = useState(initial != null ? formatNumber(initial) : "");
  const value = parseDecimal(text);
  const valid = value !== null && value >= 0;

  return (
    <View style={{ gap: 14 }}>
      <Card>
        <Text style={styles.label}>{label}</Text>
        <View style={styles.inputRow}>
          <TextInput
            style={styles.input}
            value={text}
            onChangeText={setText}
            keyboardType="decimal-pad"
            placeholder="0"
            placeholderTextColor={colors.textFaint}
            accessibilityLabel={label}
            autoFocus={initial == null}
            maxLength={8}
          />
          {unit ? <Text style={styles.unit}>{unit}</Text> : null}
        </View>
        {text !== "" && !valid ? <Text style={styles.error}>Escribe un número válido</Text> : null}
      </Card>
      <GradientButton title="Guardar resultado" onPress={() => valid && onSubmit(value as number)} disabled={!valid} loading={submitting} />
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  label: { fontSize: 13, color: colors.textMuted, fontWeight: "600" as const, marginBottom: 8 },
  inputRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 10 },
  input: { flex: 1, fontSize: 40, fontWeight: "800" as const, color: colors.primary, paddingVertical: 4 },
  unit: { fontSize: 16, color: colors.textMuted },
  error: { fontSize: 12, color: colors.danger, marginTop: 6 },
});
