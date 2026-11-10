import { useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import GoalsSection from "../components/goals/GoalsSection";
import Chip from "../components/ui/Chip";
import ScreenHeader from "../components/ui/ScreenHeader";
import { useWorld } from "../context/WorldContext";
import { useTheme } from "../theme";

/** Tus metas: por mundo inscrito (por defecto el activo), con progreso y "Marcar como lograda". */
export default function GoalsScreen() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const { worlds, active } = useWorld();
  const [picked, setPicked] = useState<string | null>(null);
  const world = picked ?? active?.key ?? null;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title="Tus metas" subtitle="Lo que quieres lograr y cuánto te falta" />
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: insets.bottom + 32, maxWidth: 720, width: "100%", alignSelf: "center" }}>
        {worlds.length > 1 ? (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingBottom: 14 }}>
            {worlds.map((w) => (
              <Chip key={w.key} label={`${w.icon} ${w.name}`} selected={w.key === world} onPress={() => setPicked(w.key)} />
            ))}
          </ScrollView>
        ) : null}
        <GoalsSection key={world ?? "activo"} world={world} />
        <Text style={{ fontSize: 12, color: colors.textMuted, textAlign: "center", marginTop: 16, lineHeight: 17 }}>
          Las metas medibles se marcan solas al cumplirlas. Las demás, márcalas tú cuando sientas que las lograste.
        </Text>
      </ScrollView>
    </View>
  );
}
