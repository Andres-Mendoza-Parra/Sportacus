import { Redirect, Tabs } from "expo-router";
import { Text } from "react-native";

import { useAuth } from "../../context/AuthContext";
import { useWorld } from "../../context/WorldContext";
import { useTheme } from "../../theme";

/**
 * Pestañas del mundo activo: Inicio · Plan · Progreso · Asistente · Cuenta.
 * Se re-tiñen con el color del mundo (ThemeProvider) y el ícono de Inicio es el del mundo.
 */
export default function TabsLayout() {
  const { token, user, isLoading } = useAuth();
  const { active } = useWorld();
  const { colors } = useTheme();

  // Sin sesión no se muestran las pestañas (p. ej. tras cerrar sesión o un 401).
  if (!isLoading && !token) return <Redirect href="/login" />;

  // Recibimiento sin terminar: el punto de entrada decide el paso pendiente.
  if (!isLoading && token && user && !user.onboardingCompleted) return <Redirect href="/" />;

  const tabs = [
    { name: "home", title: "Inicio", icon: active?.icon ?? "🏠" },
    { name: "plan", title: "Plan", icon: "🗓️" },
    { name: "progress", title: "Progreso", icon: "📈" },
    { name: "chat", title: "Asistente", icon: "🤖" },
    { name: "account", title: "Cuenta", icon: "👤" },
  ];

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textFaint,
        tabBarLabelStyle: { fontSize: 10, fontWeight: "600" },
        tabBarStyle: { backgroundColor: colors.tabBar, borderTopColor: colors.border },
        sceneStyle: { backgroundColor: colors.bg },
      }}
    >
      {tabs.map((tab) => (
        <Tabs.Screen
          key={tab.name}
          name={tab.name}
          options={{
            title: tab.title,
            tabBarAccessibilityLabel: tab.title,
            tabBarIcon: ({ focused }) => (
              <Text style={{ fontSize: focused ? 23 : 20, opacity: focused ? 1 : 0.55 }}>{tab.icon}</Text>
            ),
          }}
        />
      ))}
    </Tabs>
  );
}
