import { TypedWidget, Widget, WidgetDataMap, WidgetType } from "../../services/types";

type Data = Record<string, unknown>;

const isArray = (d: Data, k: string) => Array.isArray(d[k]);
const isNum = (d: Data, k: string) => typeof d[k] === "number";
const isStr = (d: Data, k: string) => typeof d[k] === "string";

/** Validación mínima de la forma de `data` por tipo (docs/16 §6). */
const VALIDATORS: { [T in WidgetType]: (d: Data) => boolean } = {
  next_session: (d) => d.session == null || (typeof d.session === "object" && Array.isArray((d.session as Data).blocks)),
  stat_row: (d) => isArray(d, "stats"),
  line: (d) => isArray(d, "points"),
  week_bars: (d) => isArray(d, "bars"),
  bars: (d) => isArray(d, "bars"),
  radar: (d) => isArray(d, "axes") && (d.axes as unknown[]).length >= 3,
  gauge: (d) => isNum(d, "value") && isNum(d, "min") && isNum(d, "max"),
  skill_tree: (d) => isArray(d, "trees"),
  traffic_light: (d) => isStr(d, "level"),
  goal: (d) => isNum(d, "progressPct"),
  insights: (d) => isArray(d, "items"),
  phase_track: (d) => isArray(d, "phases"),
  notice: (d) => isStr(d, "body"),
  profile_ring: (d) => isNum(d, "pct") && isArray(d, "areas"),
  question: (d) => {
    const item = d.item as Data | null | undefined;
    if (!item || typeof item !== "object" || typeof item.id !== "string" || typeof item.kind !== "string") return false;
    // Una pregunta en línea necesita su definición; las tarjetas especiales no.
    return item.kind !== "question" || (typeof item.question === "object" && item.question !== null);
  },
  checkin: (d) => typeof d.done === "boolean",
  world_suggestions: (d) => isArray(d, "items"),
  readiness: (d) => isNum(d, "factor") && isStr(d, "label") && isArray(d, "reasons"),
};

export function isKnownType(type: string): type is WidgetType {
  return Object.prototype.hasOwnProperty.call(VALIDATORS, type);
}

export type AnyTypedWidget = { [T in WidgetType]: TypedWidget<T> }[WidgetType];

/** Convierte un widget crudo en su versión tipada, o null si el tipo es desconocido o está malformado. */
export function narrowWidget(w: Widget): AnyTypedWidget | null {
  if (!isKnownType(w.type)) return null;
  const type = w.type;
  if (!VALIDATORS[type](w.data ?? {})) return null;
  return { ...w, type, data: w.data as unknown as WidgetDataMap[typeof type] } as AnyTypedWidget;
}
