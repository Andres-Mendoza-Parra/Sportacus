import React from "react";
import { Text, View } from "react-native";

import { useTheme } from "../../../theme";
import { BigButton, BlockRendererProps } from "./shared";

/** Prescripción desconocida para esta versión de la app: basta con marcarla como hecha. */
export default function GenericBlock({ entries, onChange }: BlockRendererProps) {
  const { colors } = useTheme();
  const entry = entries[0] ?? { completed: false };
  return (
    <View style={{ gap: 10 }}>
      <Text style={{ fontSize: 13, color: colors.textMuted, textAlign: "center" }}>Sigue las indicaciones del bloque y márcalo al terminar.</Text>
      <BigButton
        label={entry.completed ? "✓ Hecho (toca para desmarcar)" : "Marcar como hecho"}
        tone={entry.completed ? "ghost" : "primary"}
        onPress={() => onChange([{ ...entry, completed: !entry.completed }])}
      />
    </View>
  );
}
