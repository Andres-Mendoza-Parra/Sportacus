import { api } from "./api";
import { Answers, FormSpec, HealthSummary } from "./types";

export async function fetchScreeningForm(): Promise<FormSpec> {
  const { data } = await api.get<FormSpec>("/health/screening/form");
  return data;
}

export async function saveScreening(answers: Answers): Promise<HealthSummary> {
  const { data } = await api.post<HealthSummary>("/health/screening", answers);
  return data;
}

/** El usuario confirma que un profesional le dio el aval para hacer ejercicio. */
export async function confirmClearance(): Promise<HealthSummary> {
  const { data } = await api.post<HealthSummary>("/health/clearance");
  return data;
}

export async function fetchHealthSummary(): Promise<HealthSummary> {
  const { data } = await api.get<HealthSummary>("/health/summary");
  return data;
}
