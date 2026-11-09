import React from "react";
import { Text, View } from "react-native";

import { CheckValues } from "../../hooks/useSessionLog";
import { PostCheck, PreCheck } from "../../services/types";
import { useTheme } from "../../theme";
import MoodPicker from "./checks/MoodPicker";
import PainSlider from "./checks/PainSlider";
import RpePicker from "./checks/RpePicker";
import NumberField from "./NumberField";

interface Props {
  checks: (PreCheck | PostCheck)[];
  values: CheckValues;
  onChange: (next: CheckValues) => void;
  stage: "pre" | "post";
}

/** Chequeos de antes o después de la sesión, según lo que pida el mundo (ui.preChecks / ui.postChecks). */
export default function ChecksForm({ checks, values, onChange, stage }: Props) {
  const { colors, shadow } = useTheme();
  const set = (patch: CheckValues) => onChange({ ...values, ...patch });

  return (
    <View style={{ gap: 14 }}>
      {checks.map((check) => {
        switch (check) {
          case "pain":
            return (
              <PainSlider
                key={check}
                value={values.pain}
                onChange={(pain) => set({ pain })}
                label={stage === "pre" ? "¿Cuánto dolor tienes ahora?" : "¿Cuánto dolor sientes al terminar?"}
              />
            );
          case "mood":
            return (
              <MoodPicker
                key={check}
                value={values.mood}
                onChange={(mood) => set({ mood })}
                label={stage === "pre" ? "¿Cómo llegas hoy?" : "¿Cómo te sientes al terminar?"}
              />
            );
          case "rpe":
            return <RpePicker key={check} value={values.rpe} onChange={(rpe) => set({ rpe })} />;
          case "distance":
          case "duration":
            return (
              <View key={check} style={[{ backgroundColor: colors.card, borderRadius: 18, padding: 16, gap: 8 }, shadow]}>
                <Text style={{ fontSize: 16, fontWeight: "700", color: colors.text }}>
                  {check === "distance" ? "¿Qué distancia recorriste?" : "¿Cuánto duró la sesión?"}
                </Text>
                {check === "distance" ? (
                  <NumberField label="Distancia" unit="km" value={values.distanceKm} onChange={(distanceKm) => set({ distanceKm })} />
                ) : (
                  <NumberField
                    label="Duración"
                    unit="min"
                    decimal={false}
                    value={values.durationMin}
                    onChange={(d) => set({ durationMin: d == null ? undefined : Math.round(d) })}
                  />
                )}
              </View>
            );
          default:
            return null;
        }
      })}
    </View>
  );
}
