import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";

import { FeedbackProvider } from "../components/ui/Feedback";
import { AuthProvider } from "../context/AuthContext";
import { initNotificationHandler } from "../services/reminders";
import { ThemeProvider, useTheme } from "../theme";

function ThemedStack() {
  const { colors } = useTheme();

  useEffect(() => {
    initNotificationHandler().catch(() => {});
  }, []);

  return (
    <>
      <StatusBar style="light" />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.bg }, animation: "slide_from_right" }}>
        <Stack.Screen name="index" />
        <Stack.Screen name="login" options={{ animation: "fade" }} />
        <Stack.Screen name="register" />
        <Stack.Screen name="(tabs)" options={{ animation: "fade" }} />
        <Stack.Screen name="onboarding/module" options={{ animation: "fade", gestureEnabled: false }} />
        <Stack.Screen name="questionnaires/[key]" />
        <Stack.Screen name="routine/[id]" />
        <Stack.Screen name="settings" />
        <Stack.Screen name="workout" options={{ animation: "slide_from_bottom", gestureEnabled: false }} />
        <Stack.Screen name="progress" />
        <Stack.Screen name="tests" />
        <Stack.Screen name="plan" />
        <Stack.Screen name="library/index" />
        <Stack.Screen name="workshop/index" />
        <Stack.Screen name="workshop/pick-exercise" />
      </Stack>
    </>
  );
}

export default function RootLayout() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <FeedbackProvider>
          <ThemedStack />
        </FeedbackProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}
