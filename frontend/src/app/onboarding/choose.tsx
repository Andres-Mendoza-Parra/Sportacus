import { router } from "expo-router";
import { ScrollView, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import ChoiceCard from "../../components/onboarding/ChoiceCard";
import ScreenHeader from "../../components/ui/ScreenHeader";
import { useTheme } from "../../theme";

/** Explorar mundos (opcional): ver la galería o hacer el test de descubrimiento. Mi Base sigue siempre ahí. */
export default function ExploreWorlds() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader
        title="Explorar mundos"
        subtitle="Cuando quieras, sin prisa. Tu Base y tu progreso se quedan contigo."
        fallback="/home"
      />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 14, paddingBottom: insets.bottom + 24 }}>
        <ChoiceCard
          icon="🧭"
          title="Ya sé lo que quiero"
          body="Mira todos los mundos y elige el tuyo."
          onPress={() => router.push("/onboarding/gallery")}
        />
        <ChoiceCard
          icon="✨"
          title="Ayúdame a elegir"
          body="Responde unas preguntas y te recomendamos el mundo que mejor encaja contigo, explicando por qué."
          onPress={() => router.push("/onboarding/discovery")}
          delay={90}
        />
        <Text style={{ fontSize: 12, color: colors.textFaint, textAlign: "center", marginTop: 8, lineHeight: 17 }}>
          Puedes inscribirte en varios mundos y volver a Mi Base cuando quieras desde el selector de mundos en tu Cuenta.
        </Text>
      </ScrollView>
    </View>
  );
}
