import { api } from "./api";
import { invalidateUserContext } from "./contextBus";

export interface QuestionnaireSummary {
  key: string;
  title: string;
  description: string;
  icon: string;
  status: "completo" | "parcial" | "sin_iniciar";
  completionPct: number;
  answeredCount: number;
  totalCount: number;
}

export interface QuestionnaireGroup {
  groupTitle: string;
  items: QuestionnaireSummary[];
}

export interface QuestionnaireList {
  overallCompletionPct: number;
  groups: QuestionnaireGroup[];
}

export interface QuestionOption {
  id: string;
  icon: string | null;
  text: string;
  sub: string | null;
}

/** Los tipos 'number' y 'text' son nuevos; cualquier otro tipo desconocido se ignora sin romper. */
export type QuestionType = "options" | "range" | "tags" | "tags_catalog" | "number" | "text" | string;

export interface Question {
  id: string;
  prompt: string;
  hint: string | null;
  type: QuestionType;
  options: QuestionOption[] | null;
  min: number | null;
  max: number | null;
  unit: string | null;
  tags: string[] | null;
}

export interface QuestionnaireDetail {
  key: string;
  title: string;
  questions: Question[];
  answers: Record<string, unknown>;
}

export interface SaveResult {
  routeInvalidated: boolean;
  profileStrengthPct: number | null;
}

export async function fetchQuestionnaireList(): Promise<QuestionnaireList> {
  const { data } = await api.get<QuestionnaireList>("/questionnaires");
  return {
    overallCompletionPct: data.overallCompletionPct ?? 0,
    groups: (data.groups ?? []).map((g) => ({
      groupTitle: g.groupTitle,
      items: (g.items ?? []).map((i) => ({
        ...i,
        answeredCount: i.answeredCount ?? 0,
        totalCount: i.totalCount ?? 0,
      })),
    })),
  };
}

export async function fetchQuestionnaireDetail(key: string): Promise<QuestionnaireDetail> {
  const { data } = await api.get<QuestionnaireDetail>(`/questionnaires/${key}`);
  return data;
}

/** Guarda respuestas (pueden ser parciales). El backend invalida la rutina de hoy si hubo cambios. */
export async function saveQuestionnaireAnswers(key: string, answers: Record<string, unknown>): Promise<SaveResult> {
  const { data } = await api.post<Partial<SaveResult> | "">(`/questionnaires/${key}`, answers);
  const body = (data && typeof data === "object" ? data : {}) as Partial<SaveResult>;
  invalidateUserContext();
  // La foto del progreso (peso, score) se toma en segundo plano; import dinámico para evitar ciclos.
  import("./progressService").then((m) => m.recordSnapshot()).catch(() => {});
  return {
    routeInvalidated: body.routeInvalidated ?? true,
    profileStrengthPct: body.profileStrengthPct ?? null,
  };
}
