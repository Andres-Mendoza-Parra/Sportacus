import { Redirect, Tabs } from "expo-router";
import { Text, View } from "react-native";

import AccountGuards from "../../components/navigation/AccountGuards";
import ConnectionBanner from "../../components/navigation/ConnectionBanner";
import QuickMenu, { QUICK_MENU_SPACE } from "../../components/navigation/QuickMenu";
import TourOverlay from "../../components/tour/TourOverlay";
import { HeaderRightInsetContext } from "../../components/ui/GradientHeader";
import { useAuth } from "../../context/AuthContext";
import { useWorld } from "../../context/WorldContext";
import { useTheme } from "../../theme";

/**
 * Pestañas del mundo activo: Inicio · Plan · Progreso · Asistente · Cuenta.
 * Se re-tiñen con el color del mundo (ThemeProvider) y el ícono de Inicio es el del mundo.
 * El botón ☰ del menú rápido queda fijo arriba a la derecha; las cabeceras le reservan su espacio.
 */
export default function TabsLayout() {
  const { token, isLoading } = useAuth();
  const { active } = useWorld();
  const { colors } = useTheme();

  // Sin sesión no se muestran las pestañas (p. ej. tras cerrar sesión o un 401).
  if (!isLoading && !token) return <Redirect href="/login" />;

  const tabs = [
    { name: "home", title: "Inicio", icon: active?.icon ?? "🏠" },
    { name: "plan", title: "Plan", icon: "🗓️" },
    { name: "progress", title: "Progreso", icon: "📈" },
    { name: "chat", title: "Asistente", icon: "🤖" },
    { name: "account", title: "Cuenta", icon: "👤" },
  ];

  return (
    <HeaderRightInsetContext.Provider value={QUICK_MENU_SPACE}>
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
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
        <QuickMenu />
        <ConnectionBanner />
        <TourOverlay />
        <AccountGuards />
      </View>
    </HeaderRightInsetContext.Provider>
  );
}
