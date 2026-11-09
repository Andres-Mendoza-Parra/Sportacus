import { Colors } from "../../theme";

/** Colores de reserva para series de gráficos que el backend no colorea. */
const PALETTE = ["#4CAF50", "#2196F3", "#FF9800", "#8e5cf0", "#ef6b6b", "#26c6da"];

export function paletteColor(index: number, color?: string | null): string {
  return color ?? PALETTE[index % PALETTE.length];
}

/** Tono del backend ("good", "verde", "warn", "rojo"…) → color del tema. */
export function toneColor(tone: string | null | undefined, colors: Colors): string {
  switch (tone) {
    case "good":
    case "verde":
    case "success":
      return colors.success;
    case "warn":
    case "warning":
    case "amarillo":
      return colors.warn;
    case "bad":
    case "rojo":
    case "danger":
      return colors.danger;
    case "info":
      return colors.info;
    default:
      return colors.primary;
  }
}
