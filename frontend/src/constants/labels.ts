/** Etiquetas y formateadores compartidos (todo el texto visible en español). */

/** 1 = lunes … 7 = domingo (convención del backend). */
export const WEEKDAY_NAMES = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"] as const;
export const WEEKDAY_SHORT = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"] as const;

export function weekdayName(weekday: number | null | undefined, short = false): string {
  if (!weekday || weekday < 1 || weekday > 7) return short ? "—" : "Día libre";
  return (short ? WEEKDAY_SHORT : WEEKDAY_NAMES)[weekday - 1];
}

/** Día de la semana de hoy en la convención del backend (1 = lunes). */
export function todayWeekday(): number {
  const d = new Date().getDay(); // 0 = domingo
  return d === 0 ? 7 : d;
}

const BLOCK_TYPES: Record<string, string> = {
  calentamiento: "Calentamiento",
  principal: "Principal",
  tecnica: "Técnica",
  intervalo: "Intervalos",
  accesorio: "Accesorio",
  vuelta_calma: "Vuelta a la calma",
};

export function blockTypeLabel(type: string): string {
  return BLOCK_TYPES[type] ?? humanize(type);
}

const BLOCK_ICONS: Record<string, string> = {
  calentamiento: "🔥",
  principal: "💪",
  tecnica: "🎯",
  intervalo: "⏱️",
  accesorio: "🧩",
  vuelta_calma: "🧘",
};

/** Ícono del tipo de bloque (miniaturas sin imagen). */
export function blockTypeIcon(type: string): string {
  return BLOCK_ICONS[type] ?? "🏋️";
}

const SIDES: Record<string, string> = {
  ambos: "Ambos lados",
  cada_lado: "Cada lado",
  izquierdo: "Lado izquierdo",
  derecho: "Lado derecho",
};

export function sideLabel(side: string | null | undefined): string | null {
  if (!side) return null;
  return SIDES[side] ?? humanize(side);
}

export const RISK_LABELS: Record<string, string> = { bajo: "Riesgo bajo", moderado: "Riesgo moderado", alto: "Riesgo alto" };

/** "empuje_horizontal" → "Empuje horizontal". */
export function humanize(key: string | null | undefined): string {
  if (!key) return "";
  const text = key.replace(/[_-]+/g, " ").trim();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** 330 → "5:30 /km". */
export function formatPace(secPerKm: number | null | undefined): string | null {
  if (!secPerKm || secPerKm <= 0) return null;
  const m = Math.floor(secPerKm / 60);
  const s = Math.round(secPerKm % 60);
  return `${m}:${String(s).padStart(2, "0")} /km`;
}

/** Segundos → "45 s", "5 min", "1 h 10 min". */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null) return "—";
  if (seconds < 60) return `${Math.round(seconds)} s`;
  const min = Math.round(seconds / 60);
  if (min < 60) return seconds % 60 === 0 || min >= 10 ? `${min} min` : `${Math.floor(seconds / 60)}:${String(Math.round(seconds % 60)).padStart(2, "0")} min`;
  const h = Math.floor(min / 60);
  return `${h} h${min % 60 ? ` ${min % 60} min` : ""}`;
}

/** Metros → "800 m" / "5 km" / "21,1 km". */
export function formatDistance(meters: number | null | undefined): string {
  if (meters == null) return "—";
  if (meters < 1000) return `${Math.round(meters)} m`;
  const km = meters / 1000;
  return `${Number.isInteger(km) ? km : km.toFixed(1).replace(".", ",")} km`;
}

const MONTHS = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic"];

/** "2026-11-08" o ISO con hora → "8 nov". Con year=true → "8 nov 2026". */
export function formatDate(value: string | null | undefined, year = false): string {
  if (!value) return "—";
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!m) return value;
  const label = `${Number(m[3])} ${MONTHS[Number(m[2]) - 1] ?? ""}`;
  return year ? `${label} ${m[1]}` : label;
}

/** Número con coma decimal y sin ceros sobrantes. */
export function formatNumber(value: number | null | undefined, decimals = 1): string {
  if (value == null || Number.isNaN(value)) return "—";
  const fixed = Number.isInteger(value) ? String(value) : value.toFixed(decimals);
  return fixed.replace(/\.0+$/, "").replace(".", ",");
}

/** Hoy como YYYY-MM-DD en la zona horaria local. */
export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
