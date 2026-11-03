/** Tokens del sistema de diseño: paletas claro/oscuro, acentos y medidas. */

export type ThemePreference = "system" | "light" | "dark";
export type ResolvedMode = "light" | "dark";
export type AccentKey = "verde" | "azul" | "naranja" | "violeta";

interface AccentDef {
  label: string;
  main: string; // color principal (botones, activos)
  dark: string; // tono intermedio de degradados
  deep: string; // tono profundo de degradados
  soft: string; // fondo suave (chips) en claro
  softDark: string; // fondo suave en oscuro
}

export const ACCENTS: Record<AccentKey, AccentDef> = {
  verde: { label: "Verde", main: "#4CAF50", dark: "#2d5a27", deep: "#1a3a2a", soft: "#e8f5e9", softDark: "#1f3a24" },
  azul: { label: "Azul", main: "#2196F3", dark: "#1565c0", deep: "#0d2f5c", soft: "#e3f2fd", softDark: "#172f4a" },
  naranja: { label: "Naranja", main: "#FF9800", dark: "#e65100", deep: "#6b2a05", soft: "#fff3e0", softDark: "#4a2c10" },
  violeta: { label: "Violeta", main: "#8e5cf0", dark: "#5e35b1", deep: "#2c1766", soft: "#ede7f6", softDark: "#2e2250" },
};

export interface Colors {
  bg: string;
  card: string;
  cardAlt: string;
  text: string;
  textMuted: string;
  textFaint: string;
  border: string;
  inputBg: string;
  track: string;
  overlay: string;
  tabBar: string;
  primary: string;
  primaryDark: string;
  primaryDeep: string;
  primarySoft: string;
  onPrimary: string;
  /** Degradado de cabeceras. */
  headerGradient: [string, string, string];
  /** Degradado de botones primarios. */
  buttonGradient: [string, string];
  danger: string;
  dangerSoft: string;
  warn: string;
  warnSoft: string;
  info: string;
  infoSoft: string;
  success: string;
  successSoft: string;
}

export function buildColors(mode: ResolvedMode, accent: AccentKey): Colors {
  const a = ACCENTS[accent];
  const dark = mode === "dark";
  return {
    bg: dark ? "#0f1411" : "#f0f2ed",
    card: dark ? "#1a211c" : "#ffffff",
    cardAlt: dark ? "#222b24" : "#f5f6f3",
    text: dark ? "#eef2ee" : "#1a1a1a",
    textMuted: dark ? "#a7b2a9" : "#666666",
    textFaint: dark ? "#7c887f" : "#999999",
    border: dark ? "#2c362e" : "#e8e8e8",
    inputBg: dark ? "#151b17" : "#fafafa",
    track: dark ? "#2c362e" : "#eceeea",
    overlay: "rgba(0,0,0,0.5)",
    tabBar: dark ? "#141a16" : "#ffffff",
    primary: a.main,
    primaryDark: a.dark,
    primaryDeep: a.deep,
    primarySoft: dark ? a.softDark : a.soft,
    onPrimary: "#ffffff",
    headerGradient: dark ? [a.deep, a.deep, a.dark] : [a.deep, a.dark, a.main],
    buttonGradient: [a.main, a.dark],
    danger: dark ? "#ef6b6b" : "#c62828",
    dangerSoft: dark ? "#3d1f1f" : "#ffebee",
    warn: dark ? "#ffb74d" : "#e65100",
    warnSoft: dark ? "#40301a" : "#fff3e0",
    info: dark ? "#64b5f6" : "#1565c0",
    infoSoft: dark ? "#172f4a" : "#e3f2fd",
    success: dark ? "#81c784" : "#2d7a30",
    successSoft: dark ? "#1f3a24" : "#e8f5e9",
  };
}

export const RADIUS = { sm: 8, md: 12, lg: 16, xl: 24, pill: 999 } as const;

/** Sombra suave (boxShadow, soportado en iOS, Android y web con la nueva arquitectura). */
export function cardShadow(mode: ResolvedMode): { boxShadow: string } {
  return {
    boxShadow: mode === "dark" ? "0px 2px 10px rgba(0,0,0,0.35)" : "0px 3px 12px rgba(30,60,30,0.08)",
  };
}
