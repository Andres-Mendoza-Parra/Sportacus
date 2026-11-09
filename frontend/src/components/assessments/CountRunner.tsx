import React, { useEffect, useState } from "react";
import { Text, View } from "react-native";

import { Theme, useThemedStyles } from "../../theme";
import GradientButton from "../ui/GradientButton";
import { useCountdown } from "../ui/useCountdown";
import ManualRunner from "./ManualRunner";
import { Adjust, CountdownClock, GetReady, TapCounter } from "./RunnerParts";

interface Props {
  durationS?: number | null;
  unit?: string | null;
  submitting: boolean;
  onSubmit: (value: number) => void;
  /** Avisa si el test está en marcha (para pedir confirmación al salir). */
  onActiveChange: (active: boolean) => void;
}

type Stage = "ready" | "getready" | "counting" | "confirm";

/**
 * Test de conteo: con `durationS` hay cuenta 3-2-1, cuenta regresiva y contador de toques;
 * sin tiempo es un contador libre con "Terminar". Al final el usuario confirma o corrige el valor.
 */
export default function CountRunner({ durationS, unit, submitting, onSubmit, onActiveChange }: Props) {
  const styles = useThemedStyles(createStyles);
  const [stage, setStage] = useState<Stage>("ready");
  const [count, setCount] = useState(0);
  const timed = !!durationS && durationS > 0;
  const main = useCountdown(() => setStage("confirm"));
  const ready = useCountdown(() => {
    setStage("counting");
    main.start(durationS ?? 0);
  });

  useEffect(() => {
    onActiveChange(stage === "getready" || stage === "counting");
  }, [stage, onActiveChange]);

  function begin() {
    setCount(0);
    if (timed) {
      setStage("getready");
      ready.start(3);
    } else {
      setStage("counting");
    }
  }

  if (stage === "ready") {
    return (
      <View style={{ gap: 16 }}>
        <Text style={styles.hint}>
          {timed ? `Tendrás ${durationS} segundos. Toca el botón grande en cada repetición.` : "Toca el botón grande en cada repetición y pulsa «Terminar» al acabar."}
        </Text>
        <GradientButton title={timed ? "¡Listo, empezar!" : "Empezar a contar"} onPress={begin} />
      </View>
    );
  }

  if (stage === "getready") return <GetReady remaining={ready.remaining} />;

  if (stage === "counting") {
    return (
      <View style={{ gap: 20 }}>
        {timed ? <CountdownClock remaining={main.remaining} total={main.total} /> : null}
        <TapCounter count={count} onTap={() => setCount((c) => c + 1)} />
        <Adjust value={count} onChange={setCount} />
        {!timed ? <GradientButton title="Terminar" onPress={() => setStage("confirm")} /> : null}
        {timed ? (
          <GradientButton
            title="Terminar antes"
            variant="ghost"
            onPress={() => {
              main.stop();
              setStage("confirm");
            }}
          />
        ) : null}
      </View>
    );
  }

  return (
    <View style={{ gap: 12 }}>
      <Text style={styles.done}>{timed ? "¡Tiempo!" : "¡Listo!"} Revisa tu conteo.</Text>
      <ManualRunner key={count} unit={unit ?? "reps"} initial={count} label="Repeticiones conseguidas" submitting={submitting} onSubmit={onSubmit} />
      <GradientButton title="Repetir el test" variant="ghost" onPress={begin} disabled={submitting} />
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  hint: { fontSize: 14, color: colors.textMuted, textAlign: "center" as const, lineHeight: 20 },
  done: { fontSize: 18, fontWeight: "700" as const, color: colors.text, textAlign: "center" as const },
});
