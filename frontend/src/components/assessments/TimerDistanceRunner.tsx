import React, { useEffect, useState } from "react";
import { Text, View } from "react-native";

import { formatDuration } from "../../constants/labels";
import { Theme, useThemedStyles } from "../../theme";
import GradientButton from "../ui/GradientButton";
import { useCountdown } from "../ui/useCountdown";
import ManualRunner from "./ManualRunner";
import { CountdownClock, GetReady } from "./RunnerParts";

interface Props {
  durationS?: number | null;
  submitting: boolean;
  onSubmit: (value: number) => void;
  onActiveChange: (active: boolean) => void;
}

type Stage = "ready" | "getready" | "running" | "distance";

/** Temporizador de `durationS` y, al terminar, el usuario escribe la distancia recorrida en metros. */
export default function TimerDistanceRunner({ durationS, submitting, onSubmit, onActiveChange }: Props) {
  const styles = useThemedStyles(createStyles);
  const total = durationS && durationS > 0 ? durationS : 720;
  const [stage, setStage] = useState<Stage>("ready");
  const main = useCountdown(() => setStage("distance"));
  const ready = useCountdown(() => {
    setStage("running");
    main.start(total);
  });

  useEffect(() => {
    onActiveChange(stage === "getready" || stage === "running");
  }, [stage, onActiveChange]);

  if (stage === "ready") {
    return (
      <View style={{ gap: 16 }}>
        <Text style={styles.hint}>Tendrás {formatDuration(total)}. Recorre la mayor distancia que puedas y al final anota cuántos metros hiciste.</Text>
        <GradientButton
          title="¡Listo, empezar!"
          onPress={() => {
            setStage("getready");
            ready.start(3);
          }}
        />
        <GradientButton title="Ya lo hice, anotar distancia" variant="ghost" onPress={() => setStage("distance")} />
      </View>
    );
  }

  if (stage === "getready") return <GetReady remaining={ready.remaining} />;

  if (stage === "running") {
    return (
      <View style={{ gap: 20 }}>
        <CountdownClock remaining={main.remaining} total={main.total} />
        <Text style={styles.hint}>¡Mantén el ritmo! Te avisamos al terminar.</Text>
        <GradientButton
          title="Terminar antes"
          variant="ghost"
          onPress={() => {
            main.stop();
            setStage("distance");
          }}
        />
      </View>
    );
  }

  return <ManualRunner unit="m" label="¿Cuántos metros recorriste?" submitting={submitting} onSubmit={onSubmit} />;
}

const createStyles = ({ colors }: Theme) => ({
  hint: { fontSize: 14, color: colors.textMuted, textAlign: "center" as const, lineHeight: 20 },
});
