import * as WebBrowser from "expo-web-browser";
import { Linking } from "react-native";

import { formatNumber } from "../../constants/labels";

/** Valor de un test con su unidad ("20 reps", "45,5 s"). */
export function formatValue(value: number | null | undefined, unit: string | null | undefined): string {
  if (value == null) return "—";
  return unit ? `${formatNumber(value)} ${unit}` : formatNumber(value);
}

/** Abre un enlace de fuente en el navegador integrado (o el del sistema si falla). */
export async function openSource(url: string): Promise<void> {
  try {
    await WebBrowser.openBrowserAsync(url);
  } catch {
    Linking.openURL(url).catch(() => {});
  }
}

/** Convierte texto con coma o punto decimal en número (null si no es válido). */
export function parseDecimal(text: string): number | null {
  const cleaned = text.replace(",", ".").trim();
  if (cleaned === "") return null;
  const n = Number(cleaned);
  return Number.isFinite(n) ? n : null;
}
