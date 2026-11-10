/** Tokens del sistema de diseño: paletas claro/oscuro, acentos y medidas. */

export type ThemePreference = "system" | "light" | "dark";
export type ResolvedMode = "light" | "dark";
export type AccentKey =
  | "verde"
  | "esmeralda"
  | "turquesa"
  | "cian"
  | "azul"
  | "indigo"
  | "violeta"
  | "uva"
  | "rosa"
  | "coral"
  | "naranja"
  | "ambar"
  | "oliva"
  | "grafito";
/** Color elegido en Ajustes: "mundo" = el del mundo activo (por defecto) o una paleta fija. */
export type AccentChoice = "mundo" | AccentKey;

export interface AccentDef {
  label: string;
  main: string; // color principal (botones, activos)
  dark: string; // tono intermedio de degradados
  deep: string; // tono profundo de degradados
  soft: string; // fondo suave (chips) en claro
  softDark: string; // fondo suave en oscuro
}

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

/** Contraste mínimo (WCAG AA, texto normal) del texto blanco sobre el color principal. */
const MIN_WHITE_CONTRAST = 4.5;

function whiteContrast(color: RGB): number {
  return 1.05 / (luminance(color) + 0.05);
}

const FALLBACK_ACCENT: AccentDef = {
  label: "Verde",
  main: "#2e7d32",
  dark: "#1f5522",
  deep: "#112f13",
  soft: "#e6f0e6",
  softDark: "#1a3220",
};

/**
 * Construye un acento completo (principal, degradados y fondos suaves) a partir de un color (el del
 * mundo activo o una paleta). El principal se oscurece lo justo para que el texto blanco cumpla AA.
 */
export function deriveAccent(hex: string, label = "Mundo"): AccentDef {
  const base = parseHex(hex);
  if (!base) return FALLBACK_ACCENT;
  const black: RGB = [0, 0, 0];
  const white: RGB = [255, 255, 255];
  let main = base;
  for (let t = 0.03; whiteContrast(main) < MIN_WHITE_CONTRAST && t <= 1; t += 0.03) {
    main = mix(base, black, t);
  }
  return {
    label,
    main: toHex(main),
    dark: toHex(mix(main, black, 0.32)),
    deep: toHex(mix(main, black, 0.62)),
    soft: toHex(mix(main, white, 0.88)),
    softDark: toHex(mix(main, [15, 20, 17], 0.72)),
  };
}

/**
 * Paletas de Ajustes → Apariencia: [etiqueta, color base]. Bases vivas: deriveAccent las oscurece justo hasta AA
 * con texto blanco (≈4.7:1), lo que deja el principal legible también como texto sobre el fondo oscuro (≈3.9:1).
 */
const PALETTE: Record<AccentKey, [string, string]> = {
  verde: ["Verde", "#43a047"],
  esmeralda: ["Esmeralda", "#10b981"],
  turquesa: ["Turquesa", "#14b8a6"],
  cian: ["Cian", "#06b6d4"],
  azul: ["Azul", "#2196f3"],
  indigo: ["Índigo", "#6366f1"],
  violeta: ["Violeta", "#8b5cf6"],
  uva: ["Uva", "#c026d3"],
  rosa: ["Rosa", "#ec4899"],
  coral: ["Coral", "#f05a4a"],
  naranja: ["Naranja", "#f57c00"],
  ambar: ["Ámbar", "#f59e0b"],
  oliva: ["Oliva", "#84a312"],
  grafito: ["Grafito", "#64748b"],
};

export const ACCENT_KEYS = Object.keys(PALETTE) as AccentKey[];

export const ACCENTS = Object.fromEntries(ACCENT_KEYS.map((key) => [key, deriveAccent(PALETTE[key][1], PALETTE[key][0])])) as Record<
  AccentKey,
  AccentDef
>;

export function isAccentChoice(value: unknown): value is AccentChoice {
  return value === "mundo" || (typeof value === "string" && (ACCENT_KEYS as string[]).includes(value));
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
    // ≥ 4,5:1 sobre bg, card y cardAlt en ambos modos (WCAG AA).
    textFaint: dark ? "#97a29a" : "#6b6b6b",
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
