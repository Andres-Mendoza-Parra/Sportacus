/** Tokens del sistema de diseño: paletas claro/oscuro, acentos y medidas. */

export type ThemePreference = "system" | "light" | "dark";
export type ResolvedMode = "light" | "dark";
export type AccentKey = "verde" | "azul" | "naranja" | "violeta";

export interface AccentDef {
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

// ───────────────────── Acento derivado de un color arbitrario ─────────────────────

type RGB = [number, number, number];

function parseHex(hex: string): RGB | null {
  const m = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return null;
  const h = m[1].length === 3 ? m[1].split("").map((c) => c + c).join("") : m[1];
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

function toHex([r, g, b]: RGB): string {
  return `#${[r, g, b].map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, "0")).join("")}`;
}

/** Mezcla `color` con `other` (t = 0 → color, t = 1 → other). */
function mix(color: RGB, other: RGB, t: number): RGB {
  return [color[0] + (other[0] - color[0]) * t, color[1] + (other[1] - color[1]) * t, color[2] + (other[2] - color[2]) * t];
}

/** Luminancia relativa (WCAG) para decidir si un texto blanco se lee bien encima. */
function luminance([r, g, b]: RGB): number {
  const lin = (v: number) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

/**
 * Construye un acento completo (principal, degradados y fondos suaves) a partir del color de un mundo.
 * Si el color es muy claro se oscurece el principal para que el texto blanco siga siendo legible.
 */
export function deriveAccent(hex: string, label = "Mundo"): AccentDef {
  const base = parseHex(hex);
  if (!base) return ACCENTS.verde;
  const black: RGB = [0, 0, 0];
  const white: RGB = [255, 255, 255];
  const main = luminance(base) > 0.45 ? mix(base, black, 0.25) : base;
  return {
    label,
    main: toHex(main),
    dark: toHex(mix(main, black, 0.32)),
    deep: toHex(mix(main, black, 0.62)),
    soft: toHex(mix(main, white, 0.88)),
    softDark: toHex(mix(main, [15, 20, 17], 0.72)),
  };
}

/** Color con transparencia (hex → rgba) para fondos de chips o zonas de gráficos. */
export function withAlpha(hex: string, alpha: number): string {
  const rgb = parseHex(hex);
  if (!rgb) return hex;
  return `rgba(${rgb[0]},${rgb[1]},${rgb[2]},${alpha})`;
}

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

export function buildColors(mode: ResolvedMode, a: AccentDef): Colors {
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
