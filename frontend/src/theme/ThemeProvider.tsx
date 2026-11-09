import AsyncStorage from "@react-native-async-storage/async-storage";
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { ImageStyle, StyleSheet, TextStyle, ViewStyle, useColorScheme } from "react-native";

import { ACCENTS, Colors, ResolvedMode, ThemePreference, buildColors, cardShadow, deriveAccent } from "./tokens";

export type WeightUnit = "kg" | "lb";
export type HeightUnit = "cm" | "ft";

interface Prefs {
  preference: ThemePreference;
  weightUnit: WeightUnit;
  heightUnit: HeightUnit;
}

const DEFAULT_PREFS: Prefs = { preference: "system", weightUnit: "kg", heightUnit: "cm" };
const PREFS_KEY = "sportacus_prefs";
/** Último color de mundo aplicado: evita el destello del color por defecto al abrir la app. */
const WORLD_COLOR_KEY = "sportacus_world_color";

export interface Theme extends Prefs {
  mode: ResolvedMode;
  isDark: boolean;
  colors: Colors;
  shadow: { boxShadow: string };
  /** Color (hex) del mundo activo que tiñe la app; null = acento por defecto. */
  worldColor: string | null;
  setPreference: (p: ThemePreference) => void;
  setWeightUnit: (u: WeightUnit) => void;
  setHeightUnit: (u: HeightUnit) => void;
  setWorldColor: (hex: string | null) => void;
  formatWeight: (kg: number | null | undefined) => string;
  formatHeight: (cm: number | null | undefined) => string;
}

const ThemeContext = createContext<Theme | undefined>(undefined);

function round(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

/**
 * Proveedor de tema y preferencias de visualización (tema claro/oscuro y unidades).
 * El color principal lo pone el mundo activo (WorldContext → setWorldColor); la preferencia
 * claro/oscuro del usuario se respeta siempre. Todo vive solo en el dispositivo (AsyncStorage).
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const system = useColorScheme();
  const [prefs, setPrefs] = useState<Prefs>(DEFAULT_PREFS);
  const [worldColor, setWorldColorState] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const [raw, color] = await Promise.all([AsyncStorage.getItem(PREFS_KEY), AsyncStorage.getItem(WORLD_COLOR_KEY)]);
        if (raw) {
          const parsed = JSON.parse(raw) as Partial<Prefs>;
          setPrefs({
            preference: parsed.preference ?? DEFAULT_PREFS.preference,
            weightUnit: parsed.weightUnit ?? DEFAULT_PREFS.weightUnit,
            heightUnit: parsed.heightUnit ?? DEFAULT_PREFS.heightUnit,
          });
        }
        if (color) setWorldColorState((current) => current ?? color);
      } catch {
        // preferencias corruptas: se usan las de fábrica
      }
    })();
  }, []);

  const update = useCallback((patch: Partial<Prefs>) => {
    setPrefs((prev) => {
      const next = { ...prev, ...patch };
      AsyncStorage.setItem(PREFS_KEY, JSON.stringify(next)).catch(() => {});
      return next;
    });
  }, []);

  const setWorldColor = useCallback((hex: string | null) => {
    setWorldColorState(hex);
    if (hex) AsyncStorage.setItem(WORLD_COLOR_KEY, hex).catch(() => {});
    else AsyncStorage.removeItem(WORLD_COLOR_KEY).catch(() => {});
  }, []);

  const value = useMemo<Theme>(() => {
    const mode: ResolvedMode =
      prefs.preference === "system" ? (system === "dark" ? "dark" : "light") : prefs.preference;
    const accent = worldColor ? deriveAccent(worldColor) : ACCENTS.verde;
    return {
      ...prefs,
      mode,
      isDark: mode === "dark",
      colors: buildColors(mode, accent),
      shadow: cardShadow(mode),
      worldColor,
      setPreference: (preference) => update({ preference }),
      setWeightUnit: (weightUnit) => update({ weightUnit }),
      setHeightUnit: (heightUnit) => update({ heightUnit }),
      setWorldColor,
      formatWeight: (kg) => {
        if (kg === null || kg === undefined) return "—";
        return prefs.weightUnit === "kg" ? `${round(kg)} kg` : `${round(kg * 2.20462)} lb`;
      },
      formatHeight: (cm) => {
        if (cm === null || cm === undefined) return "—";
        if (prefs.heightUnit === "cm") return `${round(cm)} cm`;
        const totalIn = cm / 2.54;
        const ft = Math.floor(totalIn / 12);
        return `${ft}′ ${Math.round(totalIn - ft * 12)}″`;
      },
    };
  }, [prefs, system, update, worldColor, setWorldColor]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): Theme {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme debe usarse dentro de un <ThemeProvider>");
  return ctx;
}

/** Crea estilos dependientes del tema; se recalculan solo cuando cambia el tema. */
export function useThemedStyles<T extends { [K in keyof T]: ViewStyle | TextStyle | ImageStyle }>(factory: (theme: Theme) => T): T {
  const theme = useTheme();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  return useMemo(() => StyleSheet.create(factory(theme)), [theme]);
}
