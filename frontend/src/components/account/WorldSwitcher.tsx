import { router } from "expo-router";
import React, { useState } from "react";
import { ActivityIndicator, Text, View } from "react-native";

import { useWorld } from "../../context/WorldContext";
import { extractErrorMessage } from "../../services/api";
import { MyWorld } from "../../services/types";
import { Theme, useThemedStyles, withAlpha } from "../../theme";
import { useToast } from "../ui/Feedback";
import PressableScale from "../ui/PressableScale";

export const BASE_WORLD = "base";

/** Subtítulo de un mundo inscrito; Mi Base no tiene ruta ni nivel que mostrar. */
function worldSubtitle(w: MyWorld): string {
  const sessions = `${w.sessions} ${w.sessions === 1 ? "sesión" : "sesiones"}`;
  if (w.key === BASE_WORLD) return `Tu punto de partida · ${sessions}`;
  return [w.trackName, `Nivel ${w.level}`, sessions].filter(Boolean).join(" · ");
}

/**
 * Lista de mundos inscritos (Mi Base primero): el activo marcado, los demás se activan con un toque.
 * `onNavigate` se llama justo antes de navegar (p. ej. para cerrar el menú rápido).
 */
export default function WorldSwitcher({ onNavigate }: { onNavigate?: () => void } = {}) {
  const styles = useThemedStyles(createStyles);
  const toast = useToast();
  const { worlds, activate } = useWorld();
  const [switching, setSwitching] = useState<string | null>(null);
  const sorted = [...worlds].sort((a, b) => Number(b.key === BASE_WORLD) - Number(a.key === BASE_WORLD));

  async function choose(world: MyWorld) {
    if (world.active || switching) return;
    setSwitching(world.key);
    try {
      await activate(world.key);
      toast.success(`Ahora estás en ${world.name} ${world.icon}`);
      onNavigate?.();
      router.navigate("/home");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSwitching(null);
    }
  }

  return (
    <View style={{ gap: 10 }}>
      {sorted.map((w) => (
        <PressableScale
          key={w.key}
          onPress={() => choose(w)}
          disabled={w.active}
          accessibilityRole="button"
          accessibilityState={{ selected: w.active }}
          accessibilityLabel={w.active ? `${w.name}, mundo activo` : `Activar ${w.name}`}
          style={[styles.row, { borderColor: w.active ? w.color : "transparent", backgroundColor: withAlpha(w.color, w.active ? 0.14 : 0.07) }]}
        >
          <View style={[styles.icon, { backgroundColor: withAlpha(w.color, 0.2) }]}>
            <Text style={{ fontSize: 22 }}>{w.icon}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.name}>{w.name}</Text>
            <Text style={styles.sub}>{worldSubtitle(w)}</Text>
          </View>
          {switching === w.key ? (
            <ActivityIndicator color={w.color} />
          ) : w.active ? (
            <Text style={[styles.badge, { backgroundColor: w.color }]}>Activo</Text>
          ) : (
            <Text style={[styles.activate, { color: w.color }]}>Activar</Text>
          )}
        </PressableScale>
      ))}
      <PressableScale
        style={styles.add}
        onPress={() => {
          onNavigate?.();
          router.push("/onboarding/choose");
        }}
        accessibilityRole="button"
        accessibilityLabel="Explorar mundos"
      >
        <Text style={styles.addText}>🧭 Explorar mundos</Text>
      </PressableScale>
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  row: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, padding: 12, borderRadius: 16, borderWidth: 1.5 },
  icon: { width: 44, height: 44, borderRadius: 14, alignItems: "center" as const, justifyContent: "center" as const },
  name: { fontSize: 15, fontWeight: "700" as const, color: colors.text },
  sub: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  badge: { color: "#fff", fontSize: 11, fontWeight: "800" as const, paddingHorizontal: 9, paddingVertical: 4, borderRadius: 999, overflow: "hidden" as const },
  activate: { fontSize: 13, fontWeight: "700" as const },
  add: { paddingVertical: 13, borderRadius: 14, borderWidth: 1.5, borderStyle: "dashed" as const, borderColor: colors.border, alignItems: "center" as const },
  addText: { color: colors.primary, fontWeight: "700" as const, fontSize: 14 },
});
