import { LinearGradient } from "expo-linear-gradient";
import { router } from "expo-router";
import { ScrollView, Text, View } from "react-native";
import Animated, { FadeInDown, FadeInUp } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import GradientButton from "../../components/ui/GradientButton";
import PressableScale from "../../components/ui/PressableScale";
import { useAuth } from "../../context/AuthContext";
import { useTheme } from "../../theme";

const STEPS = [
  { icon: "🙋", title: "Cuéntanos de ti", body: "Edad, tiempo disponible y dónde puedes entrenar." },
  { icon: "🩺", title: "Tu salud primero", body: "Un chequeo rápido para cuidarte y evitar lo que no te conviene." },
  { icon: "🌍", title: "Elige tu mundo", body: "Gimnasio, calistenia, running, salud o fútbol. Si dudas, te ayudamos a elegir." },
  { icon: "🗓️", title: "Tu plan, listo", body: "Creamos un plan a tu medida y te acompañamos sesión a sesión." },
];

/** Bienvenida del recibimiento: explica los pasos antes de empezar el perfil. */
export default function WelcomeScreen() {
  const { colors } = useTheme();
  const { user, logout } = useAuth();
  const insets = useSafeAreaInsets();
  const firstName = (user?.name ?? "").trim().split(" ")[0];

  return (
    <LinearGradient colors={[colors.primaryDeep, colors.primaryDark, colors.primary]} style={{ flex: 1 }}>
      <ScrollView contentContainerStyle={{ padding: 24, paddingTop: insets.top + 32, paddingBottom: insets.bottom + 24, flexGrow: 1 }}>
        <Animated.View entering={FadeInUp.duration(500)}>
          <Text style={{ fontSize: 48 }}>👋</Text>
          <Text style={{ color: "#fff", fontSize: 28, fontWeight: "800", marginTop: 10 }}>
            {firstName ? `¡Hola, ${firstName}!` : "¡Hola!"}
          </Text>
          <Text style={{ color: "rgba(255,255,255,0.88)", fontSize: 15, marginTop: 8, lineHeight: 22 }}>
            Sportacus te guía en el mundo de la actividad física que elijas. Antes, vamos a conocerte en cuatro pasos.
          </Text>
        </Animated.View>

        <View style={{ gap: 12, marginTop: 28, flex: 1 }}>
          {STEPS.map((s, i) => (
            <Animated.View
              key={s.title}
              entering={FadeInDown.delay(150 + i * 90).duration(400)}
              style={{ flexDirection: "row", gap: 14, alignItems: "center", backgroundColor: "rgba(255,255,255,0.13)", borderRadius: 18, padding: 14 }}
            >
              <Text style={{ fontSize: 26 }}>{s.icon}</Text>
              <View style={{ flex: 1 }}>
                <Text style={{ color: "#fff", fontWeight: "700", fontSize: 15 }}>{`${i + 1}. ${s.title}`}</Text>
                <Text style={{ color: "rgba(255,255,255,0.82)", fontSize: 12, marginTop: 2, lineHeight: 17 }}>{s.body}</Text>
              </View>
            </Animated.View>
          ))}
        </View>

        <View style={{ marginTop: 28, gap: 12 }}>
          <GradientButton title="Empezar" onPress={() => router.push("/onboarding/profile")} />
          <PressableScale
            onPress={async () => {
              await logout();
              router.replace("/login");
            }}
            accessibilityRole="button"
            style={{ alignSelf: "center", padding: 8 }}
          >
            <Text style={{ color: "rgba(255,255,255,0.8)", fontSize: 13 }}>Usar otra cuenta</Text>
          </PressableScale>
        </View>
      </ScrollView>
    </LinearGradient>
  );
}
