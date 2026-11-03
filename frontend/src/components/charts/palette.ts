import { FocusItem } from "../../services/dashboardService";

/** Colores de reserva para ítems de gráficos que el backend no colorea. */
const PALETTE = ["#4CAF50", "#2196F3", "#FF9800", "#8e5cf0", "#ef6b6b", "#26c6da"];

export function paletteColor(item: Pick<FocusItem, "color">, index: number): string {
  return item.color ?? PALETTE[index % PALETTE.length];
}
