import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";

import SyncManager from "../components/navigation/SyncManager";
import { TourProvider } from "../components/tour/TourContext";
import { FeedbackProvider } from "../components/ui/Feedback";
import { AuthProvider } from "../context/AuthContext";
import { WorldProvider } from "../context/WorldContext";
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
        <Stack.Screen name="checkin" options={{ animation: "slide_from_bottom" }} />
        <Stack.Screen name="log-activity" options={{ animation: "slide_from_bottom" }} />
        <Stack.Screen name="session/[id]" options={{ animation: "slide_from_bottom", gestureEnabled: false }} />
        <Stack.Screen name="pain-check/[logId]" options={{ animation: "slide_from_bottom" }} />
        <Stack.Screen name="assessments/[key]" options={{ gestureEnabled: false }} />
      </Stack>
      <SyncManager />
    </>
  );
}

export default function RootLayout() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <WorldProvider>
          <FeedbackProvider>
            <TourProvider>
              <ThemedStack />
            </TourProvider>
          </FeedbackProvider>
        </WorldProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}
