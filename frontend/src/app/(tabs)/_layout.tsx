import { Redirect, Tabs } from "expo-router";
import { Text } from "react-native";

import { useAuth } from "../../context/AuthContext";
import { useTheme } from "../../theme";

const TABS = [
  { name: "home", title: "Inicio", icon: "🏠" },
  { name: "routine", title: "Rutina", icon: "🏋️" },
  { name: "chat", title: "IA", icon: "🤖" },
  { name: "questionnaires", title: "Perfil", icon: "📋" },
  { name: "profile", title: "Cuenta", icon: "👤" },
] as const;

/**
 * Barra de navegación inferior del mockup (Inicio · Rutina · IA · Perfil · Cuenta).
 * Las pantallas de detalle (routine/[id], questionnaires/[key], settings) viven
 * fuera de este grupo y se apilan encima.
 */
export default function TabsLayout() {
  const { token, user, isLoading } = useAuth();
  const { colors } = useTheme();

  // Sin sesión no se muestran las pestañas (p. ej. tras cerrar sesión o un 401).
  if (!isLoading && !token) {
    return <Redirect href="/login" />;
  }

  // Onboarding sin terminar (aún no eligió módulo): no se muestra ninguna pestaña hasta elegirlo,
  // sin importar en qué paso de la cadena de cuestionarios se haya quedado.
  if (!isLoading && token && user && !user.onboardingCompleted) {
    return <Redirect href="/onboarding/module" />;
  }

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
      {TABS.map((tab) => (
        <Tabs.Screen
          key={tab.name}
          name={tab.name}
          options={{
            title: tab.title,
            tabBarIcon: ({ focused }) => (
              <Text style={{ fontSize: focused ? 23 : 20, opacity: focused ? 1 : 0.55 }}>{tab.icon}</Text>
            ),
          }}
        />
      ))}
    </Tabs>
  );
}
