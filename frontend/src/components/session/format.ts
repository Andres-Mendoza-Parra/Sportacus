import { formatDistance, formatDuration, formatPace } from "../../constants/labels";
import { Prescription } from "../../services/types";

/** Resumen corto de una prescripción: "3 × 8–12 · RIR 2", "6 × (1 min / 1:30 min)", "5 km · 5:30 /km". */
export function prescriptionSummary(p: Prescription): string {
  const parts: string[] = [];
  switch (p.type) {
    case "reps": {
      const reps = p.repsMin != null && p.repsMax != null ? `${p.repsMin}–${p.repsMax}` : p.reps ?? p.repsMax ?? "?";
      parts.push(`${p.sets ?? 1} × ${reps} reps`);
      if (p.loadKg != null) parts.push(`${p.loadKg} kg`);
      if (p.rir != null) parts.push(`RIR ${p.rir}`);
      break;
    }
    case "hold":
      parts.push(`${p.sets ?? 1} × ${formatDuration(p.holdS ?? 0)}`);
      break;
    case "time":
      parts.push(formatDuration(p.durationS ?? 0));
      break;
    case "interval":
      parts.push(`${p.repeats ?? 1} × (${formatDuration(p.workS ?? 0)} / ${formatDuration(p.recoverS ?? 0)})`);
      break;
    case "distance":
      parts.push(formatDistance(p.distanceM ?? 0));
      break;
    default:
      break;
  }
  if (p.intensity) parts.push(p.intensity);
  const pace = formatPace(p.paceSecPerKm);
  if (pace) parts.push(pace);
  return parts.join(" · ");
}

/** "12,5" / "12.5" → 12.5; vacío o inválido → undefined. */
export function parseDecimal(text: string): number | undefined {
  const cleaned = text.replace(",", ".").trim();
  if (cleaned === "") return undefined;
  const n = Number(cleaned);
  return Number.isFinite(n) ? n : undefined;
}

export function painColor(pain: number, colors: { success: string; warn: string; danger: string }): string {
  if (pain <= 3) return colors.success;
  if (pain <= 5) return colors.warn;
  return colors.danger;
}
