import { api } from "./api";
import {
  AnswerValue,
  Answers,
  AreaView,
  CheckinResult,
  CheckinView,
  NextContext,
  NextItem,
  ProfileOverview,
  QuestionnaireView,
  SaveResult,
  SkipMode,
} from "./types";

/** Perfil 360: áreas, cuestionarios, respuestas sueltas, consentimientos y check-in diario. */

export async function fetchOverview(): Promise<ProfileOverview> {
  const { data } = await api.get<ProfileOverview>("/profile/overview");
  return data;
}

export async function fetchQuestionnaire(key: string): Promise<QuestionnaireView> {
  const { data } = await api.get<QuestionnaireView>(`/profile/questionnaires/${encodeURIComponent(key)}`);
  return data;
}

/** Guarda solo lo respondido (parcial). 403 si el área es sensible y no hay consentimiento. */
export async function saveQuestionnaire(key: string, answers: Answers): Promise<SaveResult> {
  const { data } = await api.post<SaveResult>(`/profile/questionnaires/${encodeURIComponent(key)}`, answers);
  return data;
}

export async function fetchNext(limit = 2, context: NextContext = "home"): Promise<NextItem[]> {
  const { data } = await api.get<NextItem[]>("/profile/next", { params: { limit, context } });
  return data;
}

export async function answerQuestion(questionId: string, value: AnswerValue): Promise<SaveResult> {
  const { data } = await api.post<SaveResult>("/profile/answers", { questionId, value });
  return data;
}

export async function skipQuestion(questionId: string, mode: SkipMode): Promise<NextItem[]> {
  const { data } = await api.post<NextItem[]>(`/profile/answers/${encodeURIComponent(questionId)}/skip`, { mode });
  return data;
}

/** Dar o revocar el consentimiento de un área sensible. Revocar BORRA las respuestas del área. */
export async function setConsent(areaKey: string, granted: boolean): Promise<AreaView> {
  const { data } = await api.post<AreaView>(`/profile/consent/${encodeURIComponent(areaKey)}`, { granted });
  return data;
}

export async function fetchCheckin(): Promise<CheckinView> {
  const { data } = await api.get<CheckinView>("/profile/checkin");
  return data;
}

export async function saveCheckin(answers: Answers): Promise<CheckinResult> {
  const { data } = await api.post<CheckinResult>("/profile/checkin", answers);
  return data;
}
