import Slider from "@react-native-community/slider";
import React, { useState } from "react";
import { Text, TextInput, View } from "react-native";

import { Theme, useThemedStyles, useTheme } from "../../theme";

function fmt(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(1).replace(".", ",");
}

interface RangeProps {
  min: number;
  max: number;
  step: number;
  unit: string;
  value: number;
  touched: boolean;
  onChange: (v: number) => void;
}

/** Deslizador con paso y unidad. Sin tocar se muestra atenuado (el padre confirma el valor al continuar). */
export function RangeInput({ min, max, step, unit, value, touched, onChange }: RangeProps) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  return (
    <View style={styles.rangeWrap}>
      <Text style={[styles.rangeVal, !touched && { opacity: 0.55 }]}>
        {fmt(value)} <Text style={styles.unit}>{unit}</Text>
      </Text>
      <Slider
        style={{ width: "100%", height: 40 }}
        minimumValue={min}
        maximumValue={max}
        step={step}
        value={value}
        onValueChange={(v) => onChange(Math.round(v / step) * step)}
        minimumTrackTintColor={colors.primary}
        maximumTrackTintColor={colors.track}
        thumbTintColor={colors.primary}
        accessibilityLabel={`Valor: ${fmt(value)} ${unit}`}
      />
      <Text style={styles.minMax}>{`${fmt(min)} – ${fmt(max)} ${unit}`}</Text>
    </View>
  );
}

interface NumberProps {
  value: number | undefined;
  unit?: string | null;
  min?: number | null;
  max?: number | null;
  onChange: (v: number | undefined) => void;
}

/** Campo numérico grande; acepta coma o punto decimal. */
export function NumberInput({ value, unit, min, max, onChange }: NumberProps) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  // El padre lo monta con key = id de la pregunta, así que el texto inicial basta.
  const [text, setText] = useState(value === undefined ? "" : String(value).replace(".", ","));

  const range = min != null && max != null ? `Entre ${fmt(min)} y ${fmt(max)}${unit ? ` ${unit}` : ""}` : null;
  return (
    <View style={{ gap: 6 }}>
      <View style={styles.inputCard}>
        <TextInput
          style={styles.numberInput}
          keyboardType="decimal-pad"
          placeholder="0"
          placeholderTextColor={colors.textFaint}
          value={text}
          accessibilityLabel={unit ? `Valor en ${unit}` : "Valor"}
          onChangeText={(t) => {
            const cleaned = t.replace(/[^0-9.,]/g, "");
            setText(cleaned);
            if (cleaned === "") return onChange(undefined);
            const n = Number(cleaned.replace(",", "."));
            if (!Number.isNaN(n)) onChange(n);
          }}
        />
        {unit ? <Text style={styles.unit}>{unit}</Text> : null}
      </View>
      {range ? <Text style={styles.minMax}>{range}</Text> : null}
    </View>
  );
}

/** Da formato AAAA-MM-DD mientras se escribe (solo dígitos; los guiones se ponen solos). */
export function formatDateDraft(raw: string): string {
  const digits = raw.replace(/\D/g, "").slice(0, 8);
  if (digits.length <= 4) return digits;
  if (digits.length <= 6) return `${digits.slice(0, 4)}-${digits.slice(4)}`;
  return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6)}`;
}

/** Fecha AAAA-MM-DD con teclado numérico (sin dependencias nativas de calendario). */
export function DateInput({ value, onChange }: { value: string; onChange: (v: string | undefined) => void }) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  return (
    <View style={{ gap: 6 }}>
      <View style={styles.inputCard}>
        <Text style={{ fontSize: 22 }}>📅</Text>
        <TextInput
          style={[styles.numberInput, { fontSize: 26 }]}
          keyboardType="number-pad"
          placeholder="AAAA-MM-DD"
          placeholderTextColor={colors.textFaint}
          value={value}
          maxLength={10}
          accessibilityLabel="Fecha en formato año, mes y día"
          onChangeText={(t) => {
            const next = formatDateDraft(t);
            onChange(next === "" ? undefined : next);
          }}
        />
      </View>
      <Text style={styles.minMax}>Año-mes-día, por ejemplo 2026-12-31</Text>
    </View>
  );
}

/** Texto libre con límite de longitud. */
export function TextAnswer({ value, maxLength, onChange }: { value: string; maxLength: number; onChange: (v: string) => void }) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  return (
    <View style={{ gap: 6 }}>
      <TextInput
        style={styles.textInput}
        multiline
        placeholder="Escribe aquí…"
        placeholderTextColor={colors.textFaint}
        value={value}
        onChangeText={onChange}
        maxLength={maxLength}
        accessibilityLabel="Respuesta"
      />
      <Text style={[styles.minMax, { textAlign: "right" }]}>{`${value.length}/${maxLength}`}</Text>
    </View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  rangeWrap: { backgroundColor: colors.card, borderRadius: 18, padding: 20, alignItems: "center" as const, ...shadow },
  rangeVal: { fontSize: 36, fontWeight: "700" as const, color: colors.primary, marginBottom: 8 },
  unit: { fontSize: 15, color: colors.textMuted, fontWeight: "400" as const },
  minMax: { fontSize: 11, color: colors.textFaint, marginTop: 4 },
  inputCard: { flexDirection: "row" as const, alignItems: "center" as const, gap: 10, backgroundColor: colors.card, borderRadius: 18, padding: 16, ...shadow },
  numberInput: { flex: 1, fontSize: 32, fontWeight: "700" as const, color: colors.primary },
  textInput: {
    minHeight: 110,
    textAlignVertical: "top" as const,
    backgroundColor: colors.card,
    borderRadius: 16,
    padding: 14,
    fontSize: 14,
    color: colors.text,
    borderWidth: 1.5,
    borderColor: colors.border,
  },
});
