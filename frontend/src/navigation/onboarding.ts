import { Href } from "expo-router";

import { AccountView } from "../services/types";

/**
 * Decide el siguiente paso del recibimiento según `AccountView.onboarding`:
 * perfil → chequeo de salud → elegir mundo → (onboarding del mundo) → app.
 */
export function nextOnboardingStep(account: AccountView): Href {
  const { profileDone, healthDone, worldDone } = account.onboarding;
  if (!profileDone) return "/onboarding";
  if (!healthDone) return "/onboarding/health";
  if (!worldDone || account.worlds.length === 0) return "/onboarding/choose";
  return "/home";
}

/** Modo de los pasos reutilizables: "edit" = abierto desde Cuenta (al terminar vuelve atrás). */
export type FlowMode = "onboarding" | "edit";

export function parseMode(value: string | string[] | undefined): FlowMode {
  return value === "edit" ? "edit" : "onboarding";
}
