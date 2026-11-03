import { LinearGradient } from "expo-linear-gradient";
import React, { useEffect } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from "react-native";
import Animated, {
  FadeInDown,
  FadeInUp,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSequence,
  withTiming,
} from "react-native-reanimated";

import { useTheme } from "../theme";

/** Fondo degradado, logo flotante y tarjeta para login / registro. */
export default function AuthShell({ subtitle, children }: { subtitle: string; children: React.ReactNode }) {
  const { colors, shadow } = useTheme();
  const float = useSharedValue(0);

  useEffect(() => {
    float.value = withRepeat(withSequence(withTiming(-8, { duration: 1400 }), withTiming(0, { duration: 1400 })), -1);
  }, [float]);

  const logoStyle = useAnimatedStyle(() => ({ transform: [{ translateY: float.value }] }));

  return (
    <LinearGradient colors={[colors.primaryDeep, colors.primaryDark, colors.primary]} style={{ flex: 1 }}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <ScrollView
          contentContainerStyle={{ flexGrow: 1, alignItems: "center", paddingTop: 64, paddingBottom: 32, paddingHorizontal: 24 }}
          keyboardShouldPersistTaps="handled"
        >
          <Animated.View
            entering={FadeInDown.duration(500)}
            style={[
              logoStyle,
              {
                width: 80,
                height: 80,
                borderRadius: 26,
                backgroundColor: "#fff",
                alignItems: "center",
                justifyContent: "center",
                marginBottom: 14,
                boxShadow: "0px 10px 24px rgba(0,0,0,0.25)",
              },
            ]}
          >
            <Text style={{ fontSize: 38 }}>🏃</Text>
          </Animated.View>
          <Animated.Text entering={FadeInDown.delay(100).duration(500)} style={{ color: "#fff", fontSize: 28, fontWeight: "700", letterSpacing: 2 }}>
            SPORTACUS
          </Animated.Text>
          <Animated.Text
            entering={FadeInDown.delay(180).duration(500)}
            style={{ color: "rgba(255,255,255,0.8)", fontSize: 13, marginTop: 4, marginBottom: 28, textAlign: "center" }}
          >
            {subtitle}
          </Animated.Text>

          <Animated.View
            entering={FadeInUp.delay(250).springify().damping(18)}
            style={[{ backgroundColor: colors.card, borderRadius: 24, padding: 24, width: "100%", maxWidth: 420 }, shadow]}
          >
            {children}
          </Animated.View>

          <View style={{ height: 8 }} />
          <Text style={{ color: "rgba(255,255,255,0.7)", fontSize: 12, marginTop: 16, textAlign: "center" }}>
            Al continuar aceptas los términos y política de privacidad
          </Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </LinearGradient>
  );
}
