/** Pestañas por las que pasa el recorrido. */
export type TourRoute = "/home" | "/plan" | "/chat";

export interface TourStep {
  /** Identificador estable (también es la propiedad `step` del evento de analítica). */
  key: string;
  route: TourRoute;
  /** Pantalla con scroll conectada (useTourScroll) donde vive la zona. */
  screen: string;
  /** Zonas a resaltar en orden de preferencia; si ninguna aparece, la tarjeta se muestra centrada. */
  targets: string[];
  icon: string;
  title: string;
  body: string;
}

/** 8 pasos sobre la app real. Tono cálido y sin presión: todo se puede hacer después. */
export const TOUR_STEPS: TourStep[] = [
  {
    key: "mundo",
    route: "/home",
    screen: "home",
    targets: ["home-header"],
    icon: "👋",
    title: "¡Te damos la bienvenida!",
    body: "Este es tu mundo activo. Aquí ves tu saludo, tu nivel y tu racha. Empiezas en Mi Base y puedes explorar otros mundos cuando quieras.",
  },
  {
    key: "perfil",
    route: "/home",
    screen: "home",
    targets: ["widget-profile_ring", "home-actions"],
    icon: "🧩",
    title: "Tu Perfil 360",
    body: "Cuanto más nos cuentes, mejor encaja tu plan. Va a tu ritmo: nada es obligatorio y puedes omitir lo que prefieras.",
  },
  {
    key: "pregunta",
    route: "/home",
    screen: "home",
    targets: ["widget-question"],
    icon: "💬",
    title: "Una pregunta a la vez",
    body: "Responde aquí mismo en segundos, o elige «Omitir», «Más tarde» o «Prefiero no decir». Sin presión.",
  },
  {
    key: "checkin",
    route: "/home",
    screen: "home",
    targets: ["widget-checkin", "widget-readiness"],
    icon: "☀️",
    title: "Check-in de hoy",
    body: "En 20 segundos nos cuentas cómo llegas (sueño, energía, ánimo) y ajustamos tu sesión del día. Solo enviamos lo que respondas.",
  },
  {
    key: "plan",
    route: "/plan",
    screen: "plan",
    targets: ["plan-header"],
    icon: "🗓️",
    title: "Tu plan de la semana",
    body: "Se arma con tus días y minutos disponibles, y se actualiza solo cuando cambian tus datos. Te avisamos cuando eso pase.",
  },
  {
    key: "ejercicio",
    route: "/plan",
    screen: "plan",
    targets: ["plan-first-session", "plan-sessions"],
    icon: "🏋️",
    title: "Sesiones y ejercicios",
    body: "Toca una sesión para empezarla. Toca un ejercicio para ver por qué está en tu plan, cómo hacerlo bien y cambiarlo por una alternativa.",
  },
  {
    key: "menu",
    route: "/plan",
    screen: "plan",
    targets: ["quick-menu"],
    icon: "☰",
    title: "Todo a mano en el menú",
    body: "Registrar actividad, biblioteca, tests, tus metas, ajustes y tus mundos. Desde «App › Ver recorrido» puedes repetir esta guía.",
  },
  {
    key: "asistente",
    route: "/chat",
    screen: "chat",
    targets: ["chat-input"],
    icon: "🤖",
    title: "Tu asistente",
    body: "Pregúntale lo que quieras sobre tu entrenamiento. Orienta y explica, pero no reemplaza a profesionales de la salud.",
  },
];
