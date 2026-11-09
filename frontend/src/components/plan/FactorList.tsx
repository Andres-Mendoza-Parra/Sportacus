import React, { useState } from "react";
import { Text, View } from "react-native";
import Animated, { FadeIn } from "react-native-reanimated";

import { Factor } from "../../services/types";
import { useTheme } from "../../theme";
import PressableScale from "../ui/PressableScale";

interface Props {
  factors: Factor[];
  title?: string;
  /** Empieza plegado mostrando solo el primero. */
  collapsible?: boolean;
}

/** Lista de factores explicables ("por qué este plan"). */
export default function FactorList({ factors, title = "¿Por qué este plan?", collapsible = true }: Props) {
  const { colors } = useTheme();
  const [open, setOpen] = useState(!collapsible);
  if (factors.length === 0) return null;
  const shown = open ? factors : factors.slice(0, 1);

  return (
    <View style={{ gap: 8 }}>
      <Text style={{ fontSize: 13, fontWeight: "700", color: colors.text }}>{title}</Text>
      {shown.map((f) => (
        <Animated.View key={f.key + f.text} entering={FadeIn.duration(250)} style={{ flexDirection: "row", gap: 8 }}>
          <Text style={{ color: colors.primary, fontSize: 13 }}>•</Text>
          <Text style={{ flex: 1, fontSize: 13, color: colors.textMuted, lineHeight: 19 }}>{f.text}</Text>
        </Animated.View>
      ))}
      {collapsible && factors.length > 1 ? (
        <PressableScale onPress={() => setOpen((o) => !o)} accessibilityRole="button" style={{ alignSelf: "flex-start", paddingVertical: 4 }}>
          <Text style={{ color: colors.primary, fontSize: 12, fontWeight: "700" }}>{open ? "Ver menos" : `Ver ${factors.length - 1} motivos más`}</Text>
        </PressableScale>
      ) : null}
    </View>
  );
}
