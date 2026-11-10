/**
 * Tipos del contrato de API por Mundos (docs/16-API-MUNDOS.md).
 *
 * El backend NO serializa los campos nulos: todo campo que pueda ser null en el contrato
 * se declara opcional (`campo?: T | null`) para que el código lo trate siempre como ausente.
 */

// ───────────────────────── Comunes ─────────────────────────

export interface Factor {
  key: string;
  points: number;
  text: string;
}

export interface Streak {
  currentDays: number;
  longestDays: number;
  lastActiveDate?: string | null;
}

export interface Achievement {
  key: string;
  worldKey?: string | null;
  title: string;
  icon?: string | null;
  achievedAt?: string | null;
}

/** Logro de cualquier mundo (GET /me/achievements), más reciente primero. */
export interface AchievementView extends Achievement {
  worldName?: string | null;
  worldIcon?: string | null;
}

export type Tone = "good" | "warn" | "bad";
export type NoticeTone = "info" | "warn" | "danger" | "success";
export type StepStatus = "done" | "current" | "locked";

// ───────────────────────── Formularios ─────────────────────────

/** `date` = AAAA-MM-DD. */
export type QuestionType = "options" | "multi" | "range" | "number" | "text" | "date";

export interface QuestionOption {
  id: string;
  icon?: string | null;
  text: string;
  sub?: string | null;
}

export interface ShowIf {
  question: string;
  in: string[];
}

export interface Question {
  id: string;
  prompt: string;
  hint?: string | null;
  /** Un tipo desconocido se muestra como "no soportado" y se puede omitir. */
  type: QuestionType | (string & {});
  options?: QuestionOption[] | null;
  /** range/number: límites · multi: máximo de elecciones · text: longitud máxima. */
  min?: number | null;
  max?: number | null;
  step?: number | null;
  unit?: string | null;
  required: boolean;
  showIf?: ShowIf | null;
}

export type AnswerValue = string | number | string[];
export type Answers = Record<string, AnswerValue>;

export interface FormSpec {
  key: string;
  title: string;
  subtitle?: string | null;
  questions: Question[];
  answers: Record<string, unknown>;
}

// ───────────────────────── Cuenta y salud ─────────────────────────

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  name: string;
  onboardingCompleted: boolean;
}

/** Cuerpo de POST /auth/register. */
export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  birthYear: number;
  acceptPolicy: true;
  acceptHealthData?: boolean;
  guardianConsent?: boolean;
}

/** Cuerpo de error común del backend (ApiError). */
export interface ApiErrorBody {
  status?: number;
  message?: string;
  messages?: string[];
  /** Solo en 429 (bloqueo de inicio de sesión): segundos hasta poder reintentar. */
  retryAfterSeconds?: number | null;
}

// ───────────────────────── Legal ─────────────────────────

export type LegalDocumentKey = "politica_datos" | "terminos";
/** La versión puede llegar como texto ("1.0") o número; se compara siempre como texto. */
export type LegalVersion = string | number;

export interface LegalSection {
  title: string;
  body: string;
}

export interface LegalDocument {
  key: LegalDocumentKey | (string & {});
  version: LegalVersion;
  title: string;
  updatedAt?: string | null;
  /** Resumen corto en lenguaje claro. */
  summary?: string | null;
  sections: LegalSection[];
}

/** Estado legal de la cuenta (GET /profile → `legal`). */
export interface LegalStatus {
  policyVersion: LegalVersion;
  acceptedPolicyVersion?: LegalVersion | null;
  termsVersion: LegalVersion;
  acceptedTermsVersion?: LegalVersion | null;
  healthDataConsent: boolean;
  minor: boolean;
  /** false = hay que (re)aceptar la Política y los Términos vigentes. */
  upToDate?: boolean | null;
}

export interface UserProfileData {
  birthYear?: number | null;
  sex?: string | null;
  weightKg?: number | null;
  heightCm?: number | null;
  activityLevel?: number | null;
  daysPerWeek?: number | null;
  minutesPerSession?: number | null;
  preferredDays: number[];
  environments: string[];
  equipment: string[];
  completed: boolean;
}

export type RiskLevel = "bajo" | "moderado" | "alto";

export interface HealthCondition {
  tagKey: string;
  label: string;
  kind?: string | null;
  severity?: string | null;
  bodyZone?: string | null;
}

export interface HealthSummary {
  screened: boolean;
  riskLevel: RiskLevel;
  flags: string[];
  requiresClearance: boolean;
  clearanceConfirmed: boolean;
  redFlags: boolean;
  activeTags: string[];
  conditions: HealthCondition[];
  screenedAt?: string | null;
}

export interface MyWorld {
  key: string;
  name: string;
  icon: string;
  color: string;
  trackKey: string;
  trackName: string;
  level: number;
  active: boolean;
  sessions: number;
}

export interface AccountView {
  userId: string;
  name: string;
  email: string;
  profile: UserProfileData;
  age?: number | null;
  bmi?: number | null;
  health: HealthSummary;
  worlds: MyWorld[];
  /** Avance global del Perfil 360 (igual que en GET /profile/overview). */
  completeness?: Completeness | null;
  /** Clave del mundo activo ("base" por defecto). */
  activeWorld?: string | null;
  /** Política, términos y consentimiento de datos de salud. */
  legal?: LegalStatus | null;
  /** null = aún no vio el recorrido inicial. */
  tutorialCompletedAt?: string | null;
}

export interface ProfilePatch {
  name?: string;
  weightKg?: number;
  heightCm?: number;
}

// ───────────────────────── Perfil 360 ─────────────────────────

export type CompletenessLevel = "inicial" | "basico" | "bueno" | "completo";

export interface Completeness {
  pct: number;
  level: CompletenessLevel | (string & {});
  label: string;
}

export interface QuestionnaireSummary {
  key: string;
  title: string;
  subtitle?: string | null;
  minutes?: number | null;
  answered: number;
  total: number;
  pct: number;
}

export interface AreaView {
  key: string;
  name: string;
  icon: string;
  description: string;
  sensitive: boolean;
  consented: boolean;
  pct: number;
  answered: number;
  total: number;
  /** "health" = el área la atiende el chequeo de salud (/health), no el banco de preguntas. */
  handler?: "health" | (string & {}) | null;
  questionnaires: QuestionnaireSummary[];
}

export interface ProfileOverview {
  completeness: Completeness;
  areas: AreaView[];
}

export type AnswerStatus = "respondida" | "omitida" | "pospuesta" | "no_decir";

export interface QuestionMeta {
  why?: string | null;
  uses?: string[] | null;
  status?: AnswerStatus | (string & {}) | null;
  answeredAt?: string | null;
}

export interface QuestionnaireView {
  key: string;
  areaKey: string;
  areaName: string;
  sensitive: boolean;
  consented: boolean;
  form: FormSpec;
  meta: Record<string, QuestionMeta>;
}

export type NextKind = "question" | "consent" | "health" | "refine_world";

export interface NextItem {
  /** Id para omitir/posponer: el de la pregunta, o "health" · "consent:<área>" · "refine:<mundo>". */
  id: string;
  kind: NextKind | (string & {});
  areaKey: string;
  areaName: string;
  areaIcon?: string | null;
  questionnaireKey?: string | null;
  question?: Question | null;
  why?: string | null;
  title?: string | null;
  body?: string | null;
  route?: string | null;
}

export interface SaveResult {
  saved: number;
  completeness: Completeness;
  highlights: string[];
  next: NextItem[];
}

/** omitir = 30 días sin sugerirla · posponer = 3 días · no_decir = nunca (cuenta como respondida). */
export type SkipMode = "omitir" | "posponer" | "no_decir";

export type NextContext = "home" | "post_session" | "world";

export interface Readiness {
  /** 0,6–1,0: fracción del volumen planeado que conviene hacer hoy. */
  factor: number;
  label: string;
  reasons: string[];
}

export interface CheckinView {
  done: boolean;
  form: FormSpec;
  readiness?: Readiness | null;
}

export interface CheckinResult {
  readiness?: Readiness | null;
  highlights: string[];
}

export interface RingArea {
  key: string;
  name: string;
  icon: string;
  pct: number;
}

export interface WorldSuggestion {
  key: string;
  name: string;
  icon: string;
  color: string;
  tagline: string;
  score: number;
  reasons: string[];
}

// ───────────────────────── Catálogo de mundos ─────────────────────────

export interface TrackInfo {
  worldKey: string;
  key: string;
  name: string;
  description: string;
  icon?: string | null;
  config?: Record<string, unknown> | null;
  sortOrder: number;
}

export interface WorldCard {
  key: string;
  name: string;
  tagline: string;
  description: string;
  icon: string;
  color: string;
  status: "activo" | "proximamente";
  available: boolean;
  highlights: string[];
  tracks: TrackInfo[];
  enrolled: boolean;
  active: boolean;
}

export interface RankedWorld {
  worldKey: string;
  name: string;
  icon: string;
  color: string;
  tagline: string;
  score: number;
  reasons: string[];
  cautions: string[];
  factors: Factor[];
}

export interface DiscoveryResult {
  ranking: RankedWorld[];
  notices: string[];
}

export interface EnrollResponse {
  world: MyWorld;
  rationale: Factor[];
  notices: string[];
  plan: PlanView;
}

// ───────────────────────── Plan y sesiones ─────────────────────────

export interface WorldHeader {
  key: string;
  name: string;
  icon: string;
  color: string;
  trackKey: string;
  trackName: string;
  level: number;
}

export type PrescriptionType = "reps" | "hold" | "time" | "interval" | "distance";
export type Side = "ambos" | "cada_lado" | "izquierdo" | "derecho";

export interface Prescription {
  type: PrescriptionType | (string & {});
  sets?: number | null;
  reps?: number | null;
  repsMin?: number | null;
  repsMax?: number | null;
  loadKg?: number | null;
  rir?: number | null;
  holdS?: number | null;
  durationS?: number | null;
  distanceM?: number | null;
  repeats?: number | null;
  workS?: number | null;
  recoverS?: number | null;
  restS?: number | null;
  paceSecPerKm?: number | null;
  intensity?: string | null;
  tempo?: string | null;
  painMax?: number | null;
  side?: Side | (string & {}) | null;
  cue?: string | null;
}

export type BlockType = "calentamiento" | "principal" | "tecnica" | "intervalo" | "accesorio" | "vuelta_calma";

export interface BlockView {
  id: string;
  orderIndex: number;
  blockType: BlockType | (string & {});
  movementKey?: string | null;
  movementName?: string | null;
  title: string;
  prescription: Prescription;
  progressionKey?: string | null;
  stepIndex?: number | null;
  notes?: string | null;
  imageUrl?: string | null;
  /** Razón principal del bloque en una línea ("por qué está en tu plan"). */
  summary?: string | null;
}

export interface SessionView {
  id: string;
  weekIndex: number;
  dayIndex: number;
  /** 1 = lunes … 7 = domingo. */
  weekday?: number | null;
  title: string;
  focus?: string | null;
  kind?: string | null;
  estMinutes?: number | null;
  notes?: string | null;
  done: boolean;
  lastDoneOn?: string | null;
  blocks: BlockView[];
}

export interface PlanView {
  id: string;
  title: string;
  summary?: string | null;
  weeks: number;
  currentWeek: number;
  startsOn?: string | null;
  weekStart?: string | null;
  score?: number | null;
  rationale: Factor[];
  sessions: SessionView[];
  /** Por qué se actualizó el plan solo ("Actualizamos tu plan: hiciste tu chequeo de salud"). */
  updateReason?: string | null;
  updatedAt?: string | null;
}

export type PreCheck = "pain" | "mood";
export type PostCheck = "rpe" | "pain" | "mood" | "distance" | "duration";
export type LogField = "reps" | "load" | "rir" | "hold" | "duration" | "distance" | "pain";

export interface SessionUi {
  player: "sets" | "interval" | "protocol" | "drills" | (string & {});
  preChecks: PreCheck[];
  postChecks: PostCheck[];
  logFields: LogField[];
  tips: string[];
}

export interface SessionDetail {
  session: SessionView;
  ui: SessionUi;
  /** Cómo estás hoy según el check-in (null si no se hizo). */
  today?: Readiness | null;
}

export interface BlockLog {
  planBlockId?: string;
  movementKey?: string;
  setIndex?: number;
  reps?: number;
  loadKg?: number;
  rir?: number;
  holdS?: number;
  durationS?: number;
  distanceM?: number;
  pain?: number;
  completed?: boolean;
}

export interface SessionLogRequest {
  /** UUID generado por la app al iniciar la sesión: el mismo clientId nunca se registra dos veces. */
  clientId?: string;
  /** null = actividad libre (registro en Mi Base, sin sesión del plan). */
  planSessionId?: string | null;
  title?: string;
  performedOn?: string;
  durationMin?: number;
  rpe?: number;
  painBefore?: number;
  painAfter?: number;
  moodBefore?: number;
  moodAfter?: number;
  distanceM?: number;
  notes?: string;
  blocks: BlockLog[];
}

export interface Unlock {
  progressionKey: string;
  progressionName: string;
  fromStep: number;
  toStep: number;
  stepName: string;
}

export interface SessionResult {
  sessionLogId: string;
  highlights: string[];
  achievements: Achievement[];
  streak: Streak;
  unlocks: Unlock[];
  planRegenerated: boolean;
  /** true = ese clientId ya estaba registrado (se trata como éxito, sin efectos dobles). */
  duplicate?: boolean;
}

export interface SessionSummary {
  id: string;
  planSessionId?: string | null;
  performedOn: string;
  title?: string | null;
  durationMin?: number | null;
  rpe?: number | null;
  painBefore?: number | null;
  painAfter?: number | null;
  painNextDay?: number | null;
  distanceM?: number | null;
  moodBefore?: number | null;
  moodAfter?: number | null;
}

// ───────────────────────── Widgets ─────────────────────────

export interface StatItem {
  label: string;
  value: string;
  unit?: string | null;
  hint?: string | null;
  tone?: Tone | null;
}

export interface LinePoint {
  date: string;
  value: number;
}

export interface BarItem {
  label: string;
  value: number;
  detail?: string | null;
}

export interface GaugeZone {
  from: number;
  to: number;
  tone: string;
  label: string;
}

export interface TreeStep {
  index: number;
  name: string;
  status: StepStatus;
}

export interface SkillTree {
  key: string;
  name: string;
  icon?: string | null;
  current: number;
  total: number;
  steps: TreeStep[];
}

export interface InsightItem {
  icon?: string | null;
  title: string;
  body: string;
  tone?: string | null;
  actionLabel?: string | null;
  actionRoute?: string | null;
}

export interface Phase {
  name: string;
  status: StepStatus;
  detail?: string | null;
}

export type TrafficLevel = "verde" | "amarillo" | "rojo" | "sin_datos";

export interface WidgetDataMap {
  next_session: { session?: SessionView | null };
  stat_row: { stats: StatItem[] };
  line: { unit?: string | null; points: LinePoint[]; direction?: "up" | "down" | null; target?: number | null };
  week_bars: { unit?: string | null; bars: BarItem[]; target?: number | null };
  bars: { bars: BarItem[] };
  radar: { axes: BarItem[] };
  gauge: { value: number; min: number; max: number; unit?: string | null; zones: GaugeZone[]; caption?: string | null };
  skill_tree: { trees: SkillTree[] };
  traffic_light: { level: TrafficLevel; value?: number | null; unit?: string | null; message: string };
  goal: { label: string; progressPct: number; detail?: string | null; targetDate?: string | null };
  insights: { items: InsightItem[] };
  phase_track: { phases: Phase[] };
  notice: { tone: NoticeTone; body: string };
  profile_ring: { pct: number; level: CompletenessLevel | (string & {}); label: string; areas: RingArea[] };
  question: { item: NextItem };
  checkin: { done: boolean; readiness?: Readiness | null };
  world_suggestions: { items: WorldSuggestion[] };
  readiness: Readiness;
}

export type WidgetType = keyof WidgetDataMap;

export interface Widget {
  type: string;
  key: string;
  title?: string | null;
  subtitle?: string | null;
  data: Record<string, unknown>;
}

/** Widget cuyo `type` ya se verificó: `data` queda tipado según la tabla de docs/16 §6. */
export interface TypedWidget<T extends WidgetType> {
  type: T;
  key: string;
  title?: string | null;
  subtitle?: string | null;
  data: WidgetDataMap[T];
}

export interface DashboardView {
  world: WorldHeader;
  greeting: string;
  streak: Streak;
  widgets: Widget[];
}

export interface ProgressView {
  world: WorldHeader;
  streak: Streak;
  widgets: Widget[];
  achievements: Achievement[];
  recentSessions: SessionSummary[];
}

// ───────────────────────── Tests ─────────────────────────

export type AssessmentKind = "count" | "stopwatch" | "manual" | "timer_distance";

export interface AssessmentDefinition {
  key: string;
  worldKey: string;
  name: string;
  description: string;
  instructions: string[];
  kind: AssessmentKind | (string & {});
  durationS?: number | null;
  metricKey?: string | null;
  unit?: string | null;
  retestDays?: number | null;
  norms?: Record<string, unknown> | null;
  safetyNote?: string | null;
  sourceKey?: string | null;
  sortOrder: number;
}

export interface AssessmentView {
  definition: AssessmentDefinition;
  lastValue?: number | null;
  lastAt?: string | null;
  lastClassification?: string | null;
  due: boolean;
  daysUntilDue?: number | null;
  sourceName?: string | null;
  sourceUrl?: string | null;
}

export interface AssessmentResult {
  key: string;
  value: number;
  unit?: string | null;
  classification?: string | null;
  highlights: string[];
  unlocks: Unlock[];
  firstTime: boolean;
}

// ───────────────────────── Biblioteca ─────────────────────────

export interface MovementCard {
  key: string;
  name: string;
  role?: string | null;
  level?: number | null;
  pattern?: string | null;
  muscles: string[];
  equipment: string[];
  imageUrl?: string | null;
  favorite: boolean;
  equipmentOk: boolean;
}

export interface MovementList {
  items: MovementCard[];
  patterns: string[];
}

export interface MuscleView {
  baseUrl: string;
  overlayUrls: string[];
}

export interface MovementDetail {
  key: string;
  name: string;
  description?: string | null;
  steps: string[];
  cues: string[];
  commonMistakes: string[];
  muscles: string[];
  equipment: string[];
  impact?: string | null;
  level?: number | null;
  pattern?: string | null;
  imageUrls: string[];
  videoUrl?: string | null;
  muscleViews: MuscleView[];
  cautions: string[];
  allowed: boolean;
  favorite: boolean;
  sourceName?: string | null;
  sourceUrl?: string | null;
  license?: string | null;
  author?: string | null;
}

// ───────────────────────── Detalle de bloque ─────────────────────────

export type ReasonTone = "info" | "success" | "warn" | "danger";

export interface Reason {
  icon: string;
  title: string;
  text: string;
  tone: ReasonTone | (string & {});
}

export interface ProgressionInfo {
  key: string;
  name: string;
  /** Escalón actual (1 = primero). */
  step: number;
  total: number;
  stepName: string;
  nextStepName?: string | null;
  /** Criterio para avanzar, en texto. */
  unlock?: string | null;
}

/** GET /world/blocks/{id}: el bloque, su movimiento y por qué / cómo hacerlo. */
export interface BlockDetail {
  block: BlockView;
  sessionTitle: string;
  movement?: MovementDetail | null;
  /** Por qué está en tu plan (la 1ª es la principal). */
  why: Reason[];
  /** La prescripción explicada en lenguaje claro. */
  dose: string[];
  considerations: Reason[];
  progression?: ProgressionInfo | null;
}

// ───────────────────────── Metas ─────────────────────────

export type GoalStatus = "activa" | "lograda" | "abandonada";

export interface GoalView {
  id: string;
  worldKey?: string | null;
  goalKey: string;
  label: string;
  target?: string | number | null;
  targetDate?: string | null;
  status: GoalStatus | (string & {});
  achievedAt?: string | null;
  progressPct?: number | null;
  progressText?: string | null;
  auto: boolean;
  achievedBy?: "auto" | "manual" | null;
}

// ───────────────────────── Asistente ─────────────────────────

export interface ChatReply {
  reply: string;
  personalized: boolean;
}
