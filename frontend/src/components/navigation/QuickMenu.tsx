import { Href, router, usePathname } from "expo-router";
import React, { useEffect, useState } from "react";
import { Modal, Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from "react-native";
import Animated, { FadeIn, FadeInRight, FadeOut } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useWorld } from "../../context/WorldContext";
import { track } from "../../services/analytics";
import { fetchOverview } from "../../services/profile360Service";
import { Theme, useThemedStyles, useTheme } from "../../theme";
import WorldSwitcher from "../account/WorldSwitcher";
import { TourTarget, useTour } from "../tour/TourContext";
import PressableScale from "../ui/PressableScale";

/** Ancho que reservan las cabeceras de las pestañas para el botón (ver HeaderRightInsetContext). */
export const QUICK_MENU_SPACE = 48;

interface MenuItem {
  icon: string;
  title: string;
  hint: string;
  href?: Href;
  /** Las pestañas se abren con navigate (no apilan pantallas). */
  tab?: boolean;
  /** Acción en lugar de navegación (p. ej. volver a ver el recorrido). */
  action?: "tour";
}

const TRAIN: MenuItem[] = [
  { icon: "➕", title: "Registrar actividad", hint: "Anota una sesión libre o fuera del plan", href: "/log-activity" },
  { icon: "📚", title: "Biblioteca", hint: "Técnica y variantes de cada ejercicio", href: "/library" },
  { icon: "🧪", title: "Tests físicos", hint: "Mide tu nivel y compáralo con tus marcas", href: "/assessments" },
  { icon: "🗓️", title: "Mi plan", hint: "Tu semana de entrenamiento", href: "/plan", tab: true },
];

const APP: MenuItem[] = [
  { icon: "🤖", title: "Asistente", hint: "Pregunta lo que quieras sobre tu entrenamiento", href: "/chat", tab: true },
  { icon: "⚙️", title: "Ajustes", hint: "Tema, colores, unidades y recordatorios", href: "/settings" },
  { icon: "👤", title: "Cuenta", hint: "Tus datos, tu salud y tus mundos", href: "/account", tab: true },
  { icon: "🧭", title: "Ver recorrido", hint: "Repasa en un minuto cómo funciona la app", action: "tour" },
  { icon: "🔐", title: "Política de datos", hint: "Cómo tratamos y protegemos tus datos", href: "/legal/politica" },
  { icon: "📄", title: "Términos", hint: "Condiciones de uso de Sportacus", href: "/legal/terminos" },
];

/**
 * Botón ☰ fijo arriba a la derecha de las pestañas y su panel lateral con accesos rápidos agrupados.
 * Es un modal propio (sin dependencias nativas nuevas), así funciona igual en web, iOS y Android.
 */
export default function QuickMenu() {
  const styles = useThemedStyles(createStyles);
  const insets = useSafeAreaInsets();
  const [open, setOpen] = useState(false);

  return (
    <>
      <TourTarget id="quick-menu" style={[styles.fabWrap, { top: insets.top + 14 }]}>
        <PressableScale
          onPress={() => {
            track("menu_opened");
            setOpen(true);
          }}
          accessibilityRole="button"
          accessibilityLabel="Abrir menú rápido"
          accessibilityHint="Muestra accesos a todas las secciones de la app"
          accessibilityState={{ expanded: open }}
          hitSlop={6}
          style={styles.fab}
        >
          <Text style={styles.fabIcon}>☰</Text>
        </PressableScale>
      </TourTarget>
      <Modal visible={open} transparent animationType="none" onRequestClose={() => setOpen(false)} statusBarTranslucent>
        {open ? <MenuPanel onClose={() => setOpen(false)} /> : null}
      </Modal>
    </>
  );
}

function MenuPanel({ onClose }: { onClose: () => void }) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const { active } = useWorld();
  const pathname = usePathname();
  const [profilePct, setProfilePct] = useState<number | null>(null);
  const tour = useTour();

  useEffect(() => {
    let alive = true;
    fetchOverview()
      .then((o) => alive && setProfilePct(Math.round(o.completeness.pct)))
      .catch(() => {
        // sin conexión: el acceso se muestra sin porcentaje
      });
    return () => {
      alive = false;
    };
  }, []);

  const you: MenuItem[] = [
    { icon: "☀️", title: "Check-in de hoy", hint: "Cómo llegas hoy: ajustamos tu sesión", href: "/checkin" },
    { icon: "🩺", title: "Chequeo de salud", hint: "Revisa o actualiza tu información de salud", href: "/health" },
    {
      icon: "🧩",
      title: "Mi perfil 360",
      hint: profilePct != null ? `Completado al ${profilePct} %: cuanto más sepamos, mejor tu plan` : "Cuanto más sepamos de ti, mejor tu plan",
      href: "/profile360",
    },
    { icon: "🎯", title: "Tus metas", hint: "Lo que quieres lograr y cuánto te falta", href: "/goals" },
    { icon: "🏅", title: "Logros", hint: "Tus logros de todos los mundos", href: "/achievements" },
    { icon: "📈", title: "Progreso", hint: "Métricas, sesiones y logros de tu mundo", href: "/progress", tab: true },
  ];

  function go(item: MenuItem) {
    onClose();
    if (item.action === "tour") {
      tour.start();
      return;
    }
    if (!item.href) return;
    if (item.tab) router.navigate(item.href);
    else router.push(item.href);
  }

  const panelWidth = Math.min(360, Math.round(width * 0.88));

  return (
    <View style={StyleSheet.absoluteFill}>
      <Animated.View entering={FadeIn.duration(160)} exiting={FadeOut.duration(120)} style={StyleSheet.absoluteFill}>
        <Pressable
          style={[StyleSheet.absoluteFill, { backgroundColor: colors.overlay }]}
          onPress={onClose}
          accessibilityRole="button"
          accessibilityLabel="Cerrar menú rápido"
        />
      </Animated.View>
      <Animated.View
        entering={FadeInRight.duration(220)}
        style={[styles.panel, { width: panelWidth, paddingTop: insets.top + 12 }]}
        accessibilityViewIsModal
        accessibilityLabel="Menú rápido"
      >
        <View style={styles.panelHead}>
          <Text style={styles.panelTitle} accessibilityRole="header">
            Menú rápido
          </Text>
          <PressableScale onPress={onClose} accessibilityRole="button" accessibilityLabel="Cerrar menú" hitSlop={8} style={styles.close}>
            <Text style={{ fontSize: 16, color: colors.text }}>✕</Text>
          </PressableScale>
        </View>
        <ScrollView contentContainerStyle={{ paddingHorizontal: 14, paddingBottom: 24 + insets.bottom }} showsVerticalScrollIndicator={false}>
          <Group title="Entrenar" items={TRAIN} pathname={pathname} onPick={go} />
          <Group title="Tú" items={you} pathname={pathname} onPick={go} />

          <Text style={styles.group} accessibilityRole="header">
            Mundos
          </Text>
          {active ? (
            <Text style={styles.activeWorld}>
              Estás en {active.icon} {active.name}. Toca otro mundo para cambiar.
            </Text>
          ) : null}
          <WorldSwitcher onNavigate={onClose} />

          <Group title="App" items={APP} pathname={pathname} onPick={go} />
        </ScrollView>
      </Animated.View>
    </View>
  );
}

function Group({ title, items, pathname, onPick }: { title: string; items: MenuItem[]; pathname: string; onPick: (item: MenuItem) => void }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View>
      <Text style={styles.group} accessibilityRole="header">
        {title}
      </Text>
      <View style={styles.groupCard}>
        {items.map((item, i) => {
          const current = typeof item.href === "string" && pathname === item.href;
          return (
            <PressableScale
              key={item.title}
              onPress={() => onPick(item)}
              accessibilityRole="button"
              accessibilityLabel={item.title}
              accessibilityHint={item.hint}
              accessibilityState={{ selected: current }}
              style={[styles.item, i > 0 && styles.itemBorder, current && styles.itemCurrent]}
            >
              <View style={styles.itemIcon}>
                <Text style={{ fontSize: 18 }}>{item.icon}</Text>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.itemTitle, current && styles.itemTitleCurrent]}>{item.title}</Text>
                <Text style={styles.itemHint} numberOfLines={2}>
                  {item.hint}
                </Text>
              </View>
              <Text style={styles.chevron}>›</Text>
            </PressableScale>
          );
        })}
      </View>
    </View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  fabWrap: { position: "absolute" as const, right: 14, zIndex: 50, elevation: 6, borderRadius: 13 },
  fab: {
    width: 44,
    height: 44,
    borderRadius: 13,
    alignItems: "center" as const,
    justifyContent: "center" as const,
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadow,
  },
  fabIcon: { fontSize: 19, color: colors.text, fontWeight: "700" as const },
  panel: {
    position: "absolute" as const,
    top: 0,
    right: 0,
    bottom: 0,
    backgroundColor: colors.bg,
    borderTopLeftRadius: 24,
    borderBottomLeftRadius: 24,
    boxShadow: "-4px 0px 24px rgba(0,0,0,0.25)",
  },
  panelHead: { flexDirection: "row" as const, alignItems: "center" as const, paddingHorizontal: 18, paddingBottom: 6 },
  panelTitle: { flex: 1, fontSize: 20, fontWeight: "800" as const, color: colors.text },
  close: { width: 36, height: 36, borderRadius: 12, backgroundColor: colors.cardAlt, alignItems: "center" as const, justifyContent: "center" as const },
  group: {
    fontSize: 11,
    color: colors.textFaint,
    fontWeight: "700" as const,
    textTransform: "uppercase" as const,
    letterSpacing: 0.8,
    marginTop: 18,
    marginBottom: 8,
    marginLeft: 4,
  },
  groupCard: { backgroundColor: colors.card, borderRadius: 16, overflow: "hidden" as const, ...shadow },
  item: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, paddingVertical: 11, paddingHorizontal: 12 },
  itemBorder: { borderTopWidth: 1, borderTopColor: colors.border },
  itemCurrent: { backgroundColor: colors.primarySoft },
  itemIcon: { width: 36, height: 36, borderRadius: 11, backgroundColor: colors.cardAlt, alignItems: "center" as const, justifyContent: "center" as const },
  itemTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  itemTitleCurrent: { color: colors.primary },
  itemHint: { fontSize: 12, color: colors.textMuted, marginTop: 1, lineHeight: 16 },
  chevron: { fontSize: 20, color: colors.textFaint },
  activeWorld: { fontSize: 12, color: colors.textMuted, marginBottom: 8, marginLeft: 4 },
});
