import { loadRawPref, savePref } from "./prefs";

/**
 * Modos de cuidado: ajustan cómo se ejecuta una sesión (menos series, más descanso, texto grande)
 * y qué contexto recibe el asistente. No sustituyen la valoración de un profesional de la salud.
 */
export type CareModeKey = "standard" | "senior" | "teen" | "rehab" | "pregnancy";

export interface CareConfig {
  key: CareModeKey;
  label: string;
  icon: string;
  description: string;
  /** Series a restar en cada ejercicio (mínimo 1 serie). */
  setsDelta: number;
  /** Segundos extra de descanso entre series. */
  restBonus: number;
  largeText: boolean;
  /** Avisos que se muestran antes de entrenar. */
  notes: string[];
  /** Texto que se le pasa al asistente de IA. */
  assistantNote: string;
}

export const CARE_MODES: Record<CareModeKey, CareConfig> = {
  standard: {
    key: "standard",
    label: "Estándar",
    icon: "🏃",
    description: "Sin ajustes especiales.",
    setsDelta: 0,
    restBonus: 0,
    largeText: false,
    notes: [],
    assistantNote: "",
  },
  senior: {
    key: "senior",
    label: "Adulto mayor",
    icon: "🧓",
    description: "Menos series, más descanso, texto grande y prioridad al equilibrio y la movilidad.",
    setsDelta: 1,
    restBonus: 30,
    largeText: true,
    notes: [
      "Entrena cerca de una silla o pared firme para apoyarte si pierdes el equilibrio.",
      "Si te mareas, te falta el aire o sientes dolor en el pecho, detente y busca atención médica.",
    ],
    assistantNote:
      "El usuario eligió el modo adulto mayor: prioriza bajo impacto, equilibrio, movilidad y seguridad; evita saltos y cargas altas.",
  },
  teen: {
    key: "teen",
    label: "Adolescente",
    icon: "🧑‍🎓",
    description: "Énfasis en técnica, juego y hábitos; sin cargas máximas.",
    setsDelta: 0,
    restBonus: 0,
    largeText: false,
    notes: ["Prioriza la técnica sobre el peso: nada de cargas máximas ni retos extremos."],
    assistantNote:
      "El usuario eligió el modo adolescente: prioriza técnica, variedad y hábitos sanos; desaconseja cargas máximas y dietas restrictivas.",
  },
  rehab: {
    key: "rehab",
    label: "Rehabilitación",
    icon: "🩹",
    description: "Series reducidas, descansos largos y avisos de seguridad reforzados.",
    setsDelta: 1,
    restBonus: 30,
    largeText: false,
    notes: [
      "Trabaja sin dolor: una molestia leve es una señal para reducir, no para aguantar.",
      "Sigue las indicaciones de tu fisioterapeuta o médico por encima de esta rutina.",
    ],
    assistantNote:
      "El usuario eligió el modo rehabilitación: sé muy conservador, recomienda siempre consultar a su profesional de salud y nunca sugieras ignorar el dolor.",
  },
  pregnancy: {
    key: "pregnancy",
    label: "Embarazo",
    icon: "🤰",
    description: "Intensidad moderada, descansos largos y avisos específicos.",
    setsDelta: 1,
    restBonus: 30,
    largeText: false,
    notes: [
      "Mantén una intensidad en la que puedas conversar mientras te mueves.",
      "Evita acostarte boca arriba por tiempos largos y los ejercicios con riesgo de caída o golpes.",
      "Consulta con tu médico antes de entrenar y detente ante mareo, sangrado o contracciones.",
    ],
    assistantNote:
      "El usuario eligió el modo embarazo: recomienda intensidad moderada, evita impacto, posición supina prolongada y maniobras de Valsalva; indica siempre validar con su médico.",
  },
};

const KEY = "care_mode";

export async function loadCareMode(): Promise<CareConfig> {
  const key = await loadRawPref<CareModeKey>(KEY, "standard");
  return CARE_MODES[key] ?? CARE_MODES.standard;
}

export async function saveCareMode(key: CareModeKey): Promise<void> {
  await savePref(KEY, key);
}
