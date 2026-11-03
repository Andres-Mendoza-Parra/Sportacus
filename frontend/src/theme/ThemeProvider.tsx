import AsyncStorage from "@react-native-async-storage/async-storage";
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { ImageStyle, StyleSheet, TextStyle, ViewStyle, useColorScheme } from "react-native";

import {
  AccentKey,
  Colors,
  ResolvedMode,
  ThemePreference,
  buildColors,
  cardShadow,
} from "./tokens";

export type WeightUnit = "kg" | "lb";
export type HeightUnit = "cm" | "ft";

interface Prefs {
  preference: ThemePreference;
  accent: AccentKey;
  weightUnit: WeightUnit;
  heightUnit: HeightUnit;
}

const DEFAULT_PREFS: Prefs = { preference: "system", accent: "verde", weightUnit: "kg", heightUnit: "cm" };
const PREFS_KEY = "sportacus_prefs";

export interface Theme extends Prefs {
  mode: ResolvedMode;
  isDark: boolean;
  colors: Colors;
  shadow: { boxShadow: string };
  setPreference: (p: ThemePreference) => void;
  setAccent: (a: AccentKey) => void;
  setWeightUnit: (u: WeightUnit) => void;
  setHeightUnit: (u: HeightUnit) => void;
  formatWeight: (kg: number | null | undefined) => string;
  formatHeight: (cm: number | null | undefined) => string;
}

const ThemeContext = createContext<Theme | undefined>(undefined);

function round(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

/**
 * Proveedor de tema y preferencias de visualización (tema, acento y unidades).
 * Las preferencias viven solo en el dispositivo (AsyncStorage).
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const system = useColorScheme();
  const [prefs, setPrefs] = useState<Prefs>(DEFAULT_PREFS);

  useEffect(() => {
    (async () => {
      try {
        const raw = await AsyncStorage.getItem(PREFS_KEY);
        if (raw) setPrefs({ ...DEFAULT_PREFS, ...JSON.parse(raw) });
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

  const value = useMemo<Theme>(() => {
    const mode: ResolvedMode =
      prefs.preference === "system" ? (system === "dark" ? "dark" : "light") : prefs.preference;
    return {
      ...prefs,
      mode,
      isDark: mode === "dark",
      colors: buildColors(mode, prefs.accent),
      shadow: cardShadow(mode),
      setPreference: (preference) => update({ preference }),
      setAccent: (accent) => update({ accent }),
      setWeightUnit: (weightUnit) => update({ weightUnit }),
      setHeightUnit: (heightUnit) => update({ heightUnit }),
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
  }, [prefs, system, update]);

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
