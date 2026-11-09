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

export type Tone = "good" | "warn" | "bad";
export type NoticeTone = "info" | "warn" | "danger" | "success";
export type StepStatus = "done" | "current" | "locked";

// ───────────────────────── Formularios ─────────────────────────

export type QuestionType = "options" | "multi" | "range" | "number" | "text";

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

export interface OnboardingStatus {
  profileDone: boolean;
  healthDone: boolean;
  worldDone: boolean;
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
  onboarding: OnboardingStatus;
}

export interface ProfilePatch {
  name?: string;
  weightKg?: number;
  heightCm?: number;
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
  planSessionId?: string;
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

// ───────────────────────── Asistente ─────────────────────────

export interface ChatReply {
  reply: string;
  personalized: boolean;
}
