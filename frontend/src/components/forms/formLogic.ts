import { AnswerValue, Answers, FormSpec, Question } from "../../services/types";

/** Ids de opción que significan "nada" (excluyentes con el resto en preguntas multi). */
const NONE_IDS = new Set(["ninguno", "ninguna", "nada"]);

export function isNoneOption(id: string): boolean {
  return NONE_IDS.has(id);
}

export function isAnswered(value: AnswerValue | undefined): boolean {
  if (value === undefined || value === null) return false;
  if (typeof value === "string") return value.trim().length > 0;
  if (typeof value === "number") return !Number.isNaN(value);
  return value.length > 0;
}

/** Convierte una respuesta guardada (unknown) en un valor válido para la pregunta, o undefined. */
function coerce(question: Question, raw: unknown): AnswerValue | undefined {
  if (raw === undefined || raw === null) return undefined;
  switch (question.type) {
    case "multi": {
      const list = Array.isArray(raw) ? raw : [raw];
      const ids = list.filter((v): v is string | number => typeof v === "string" || typeof v === "number").map(String);
      return ids.length ? ids : undefined;
    }
    case "range":
    case "number": {
      const n = typeof raw === "number" ? raw : typeof raw === "string" ? Number(raw.replace(",", ".")) : NaN;
      return Number.isFinite(n) ? n : undefined;
    }
    case "options":
      return typeof raw === "string" || typeof raw === "number" ? String(raw) : undefined;
    default:
      return typeof raw === "string" ? raw : typeof raw === "number" ? String(raw) : undefined;
  }
}

/** Respuestas iniciales a partir de `FormSpec.answers`. */
export function initialAnswers(spec: FormSpec): Answers {
  const out: Answers = {};
  for (const q of spec.questions) {
    const v = coerce(q, spec.answers?.[q.id]);
    if (v !== undefined) out[q.id] = v;
  }
  return out;
}

/** ¿Se muestra la pregunta según su `showIf`? */
export function isVisible(question: Question, answers: Answers): boolean {
  const cond = question.showIf;
  if (!cond) return true;
  const value = answers[cond.question];
  if (value === undefined) return false;
  const values = Array.isArray(value) ? value : [String(value)];
  return values.some((v) => cond.in.includes(v));
}

export function visibleQuestions(spec: FormSpec, answers: Answers): Question[] {
  return spec.questions.filter((q) => isVisible(q, answers));
}

/** Valor por defecto que muestra un slider sin tocar (punto medio redondeado al paso). */
export function rangeDefault(q: Question): number {
  const min = q.min ?? 0;
  const max = q.max ?? 100;
  const step = q.step && q.step > 0 ? q.step : 1;
  return min + Math.round((max - min) / 2 / step) * step;
}

/** Mensaje de error de una respuesta, o null si es válida. */
export function validate(question: Question, value: AnswerValue | undefined): string | null {
  if (!isAnswered(value)) return question.required ? "Esta pregunta es obligatoria." : null;
  if (question.type === "number" && typeof value === "number") {
    if (question.min != null && value < question.min) return `El valor mínimo es ${question.min}${question.unit ? ` ${question.unit}` : ""}.`;
    if (question.max != null && value > question.max) return `El valor máximo es ${question.max}${question.unit ? ` ${question.unit}` : ""}.`;
  }
  if (question.type === "text" && typeof value === "string" && question.max != null && value.length > question.max) {
    return `Máximo ${question.max} caracteres.`;
  }
  return null;
}

/**
 * Alterna una opción multi respetando `max` y la opción excluyente "ninguno/ninguna".
 * Devuelve la nueva lista o null si se superó el máximo.
 */
export function toggleMulti(question: Question, current: string[], id: string): string[] | null {
  if (current.includes(id)) return current.filter((v) => v !== id);
  if (isNoneOption(id)) return [id];
  const next = [...current.filter((v) => !isNoneOption(v)), id];
  if (question.max != null && question.max > 0 && next.length > question.max) return null;
  return next;
}

/** Solo las respuestas de preguntas visibles, con texto recortado. */
export function payloadFor(spec: FormSpec, answers: Answers): Answers {
  const out: Answers = {};
  for (const q of visibleQuestions(spec, answers)) {
    const v = answers[q.id];
    if (v === undefined || !isAnswered(v)) continue;
    out[q.id] = typeof v === "string" ? v.trim() : v;
  }
  return out;
}

export const SUPPORTED_TYPES = ["options", "multi", "range", "number", "text"];
