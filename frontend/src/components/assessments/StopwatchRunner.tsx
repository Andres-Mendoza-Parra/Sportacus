import React, { useEffect } from "react";
import { Text, View } from "react-native";

import { formatNumber } from "../../constants/labels";
import { formatStopwatch, useStopwatch } from "../../hooks/useStopwatch";
import { Theme, useThemedStyles } from "../../theme";
import GradientButton from "../ui/GradientButton";

interface Props {
  unit?: string | null;
  submitting: boolean;
  onSubmit: (value: number) => void;
  onActiveChange: (active: boolean) => void;
}

/** Test de cronómetro: el valor son los segundos medidos (redondeados a décimas). */
export default function StopwatchRunner({ unit, submitting, onSubmit, onActiveChange }: Props) {
  const styles = useThemedStyles(createStyles);
  const { elapsed, running, start, stop, reset } = useStopwatch();
  const measured = Math.round(elapsed * 10) / 10;
  const hasValue = !running && measured > 0;

  useEffect(() => {
    onActiveChange(running);
  }, [running, onActiveChange]);

  return (
    <View style={{ gap: 18 }}>
      <Text style={styles.clock} accessibilityLabel={`${formatNumber(measured)} segundos`}>
        {formatStopwatch(elapsed)}
      </Text>
      {running ? (
        <GradientButton title="⏹ Detener" variant="danger" onPress={stop} />
      ) : hasValue ? (
        <>
          <Text style={styles.result}>
            Resultado: {formatNumber(measured)} {unit ?? "s"}
          </Text>
          <GradientButton title="Guardar resultado" onPress={() => onSubmit(measured)} loading={submitting} />
          <GradientButton title="Repetir" variant="ghost" onPress={reset} disabled={submitting} />
        </>
      ) : (
        <GradientButton title="▶ Iniciar cronómetro" onPress={start} />
      )}
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  clock: { fontSize: 64, fontWeight: "800" as const, color: colors.text, textAlign: "center" as const, fontVariant: ["tabular-nums" as const], marginTop: 20 },
  result: { fontSize: 16, fontWeight: "600" as const, color: colors.primary, textAlign: "center" as const },
});
