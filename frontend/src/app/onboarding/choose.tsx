import { router, useLocalSearchParams } from "expo-router";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import ChoiceCard from "../../components/onboarding/ChoiceCard";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { useTheme } from "../../theme";

/** Paso 3: "¿Ya sabes qué quieres?" → galería o test de descubrimiento. ?mode=add viene de Cuenta. */
export default function ChooseStep() {
  const { mode } = useLocalSearchParams<{ mode?: string }>();
  const adding = mode === "add";
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader
        title={adding ? "Añadir un mundo" : "¿Ya sabes qué quieres?"}
        subtitle={adding ? "Tus otros mundos conservan todo su progreso." : "Cada mundo es un entrenamiento completo, pensado para su disciplina."}
        back={adding}
        fallback="/account"
      />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: insets.bottom + 24 }}>
        <ChoiceCard
          icon="🧭"
          title="Sí, ya sé lo que quiero"
          body="Mira todos los mundos y elige el tuyo."
          onPress={() => router.push({ pathname: "/onboarding/gallery", params: adding ? { mode: "add" } : {} })}
        />
        <ChoiceCard
          icon="✨"
          title="Ayúdame a elegir"
          body="Responde 6 preguntas y te recomendamos el mundo que mejor encaja contigo, explicando por qué."
          onPress={() => router.push({ pathname: "/onboarding/discovery", params: adding ? { mode: "add" } : {} })}
          delay={90}
        />
        <Text style={{ fontSize: 12, color: colors.textFaint, textAlign: "center", marginTop: 8, lineHeight: 17 }}>
          Puedes inscribirte en varios mundos y cambiar el activo cuando quieras desde tu Cuenta.
        </Text>
      </ScrollView>
    </View>
  );
}
