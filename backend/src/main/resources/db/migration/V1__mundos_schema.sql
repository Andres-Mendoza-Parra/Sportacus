-- ═══════════════════════════════════════════════════════════════════════════════
-- Sportacus — esquema por Mundos (sportacus_v2). Ver docs/14-ARQUITECTURA-DE-MUNDOS.md §6.
-- Flyway ejecuta este archivo dentro del esquema sportacus_v2 (search_path), por eso los
-- nombres no van calificados. gen_random_uuid() es nativo desde PostgreSQL 13.
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ───────────────────────── 1. Cuenta y perfil común ─────────────────────────

CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(80),
    language      VARCHAR(5)   NOT NULL DEFAULT 'es',
    country       VARCHAR(60),
    city          VARCHAR(80),
    -- Pasos del recibimiento: perfil → salud → mundo. Se completa al inscribirse en el primer mundo.
    onboarding_completed_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_app_user_updated BEFORE UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Solo datos universales: lo específico de cada mundo vive en user_world.profile.
CREATE TABLE user_profile (
    user_id             UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    birth_year          SMALLINT CHECK (birth_year BETWEEN 1900 AND 2100),
    sex                 VARCHAR(20) CHECK (sex IN ('femenino','masculino','otro','prefiero_no_decir')),
    weight_kg           NUMERIC(5,1) CHECK (weight_kg BETWEEN 20 AND 400),
    height_cm           NUMERIC(5,1) CHECK (height_cm BETWEEN 80 AND 250),
    activity_level      SMALLINT CHECK (activity_level BETWEEN 0 AND 4),   -- 0 sedentario … 4 muy activo
    days_per_week       SMALLINT CHECK (days_per_week BETWEEN 1 AND 7),
    minutes_per_session SMALLINT CHECK (minutes_per_session BETWEEN 10 AND 240),
    preferred_days      SMALLINT[] NOT NULL DEFAULT '{}',                  -- 1=lunes … 7=domingo
    environments        TEXT[]     NOT NULL DEFAULT '{}',                  -- gimnasio, casa, parque, exterior, cancha
    equipment           TEXT[]     NOT NULL DEFAULT '{}',                  -- claves de equipamiento
    completed_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_user_profile_updated BEFORE UPDATE ON user_profile FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────── 2. Salud ─────────────────────────

-- Vocabulario controlado compartido por lesiones del usuario y contraindicaciones de movimientos.
CREATE TABLE contraindication_tag (
    key       VARCHAR(40) PRIMARY KEY,
    label     VARCHAR(80) NOT NULL,
    kind      VARCHAR(20) NOT NULL CHECK (kind IN ('zona','condicion','estado')),
    body_zone VARCHAR(40)
);

-- Chequeo previo a la actividad (propio, según ACSM 2015; no es PAR-Q+). Se guarda el histórico.
CREATE TABLE health_screening (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    answers_encrypted  TEXT NOT NULL,                     -- JSON de respuestas cifrado (AES-GCM)
    flags              TEXT[] NOT NULL DEFAULT '{}',      -- p. ej. cardio_sintomas, bandera_roja_nocturno
    risk_level         VARCHAR(10) NOT NULL CHECK (risk_level IN ('bajo','moderado','alto')),
    requires_clearance BOOLEAN NOT NULL DEFAULT FALSE,
    clearance_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_health_screening_user ON health_screening(user_id, created_at DESC);

CREATE TABLE health_condition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    tag_key         VARCHAR(40) NOT NULL REFERENCES contraindication_tag(key),
    kind            VARCHAR(20) NOT NULL CHECK (kind IN ('lesion','condicion','cirugia','embarazo')),
    severity        VARCHAR(10) CHECK (severity IN ('leve','moderada','grave')),
    status          VARCHAR(20) NOT NULL DEFAULT 'activa' CHECK (status IN ('activa','en_recuperacion','resuelta')),
    notes_encrypted TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_health_condition_user ON health_condition(user_id) WHERE status <> 'resuelta';
CREATE TRIGGER trg_health_condition_updated BEFORE UPDATE ON health_condition FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────── 3. Catálogo (contenido como código) ─────────────────────────

CREATE TABLE content_source (
    key         VARCHAR(60) PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    url         TEXT,
    license     VARCHAR(80) NOT NULL,
    attribution TEXT,
    notes       TEXT
);

CREATE TABLE world (
    key         VARCHAR(30) PRIMARY KEY,
    name        VARCHAR(60) NOT NULL,
    tagline     VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    icon        VARCHAR(16) NOT NULL,
    color       VARCHAR(9)  NOT NULL,         -- color de acento del mundo (#RRGGBB)
    status      VARCHAR(15) NOT NULL DEFAULT 'activo' CHECK (status IN ('activo','proximamente','oculto')),
    sort_order  SMALLINT NOT NULL DEFAULT 0,
    highlights  TEXT[] NOT NULL DEFAULT '{}'  -- 3-4 frases para la galería
);

CREATE TABLE world_track (
    world_key   VARCHAR(30) NOT NULL REFERENCES world(key) ON DELETE CASCADE,
    key         VARCHAR(40) NOT NULL,
    name        VARCHAR(80) NOT NULL,
    description TEXT NOT NULL,
    icon        VARCHAR(16),
    config      JSONB NOT NULL DEFAULT '{}',  -- parámetros propios del mundo (semanas, zona, objetivo…)
    sort_order  SMALLINT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (world_key, key)
);

CREATE TABLE movement (
    key             VARCHAR(60) PRIMARY KEY,   -- slug estable, p. ej. 'sentadilla_goblet'
    name            VARCHAR(120) NOT NULL,
    description     TEXT,
    steps           JSONB NOT NULL DEFAULT '[]',
    cues            JSONB NOT NULL DEFAULT '[]',
    common_mistakes JSONB NOT NULL DEFAULT '[]',
    body_regions    TEXT[] NOT NULL DEFAULT '{}',   -- tren_superior, tren_inferior, core, cuerpo_completo, cardio
    muscles         TEXT[] NOT NULL DEFAULT '{}',
    equipment       TEXT[] NOT NULL DEFAULT '{}',
    environments    TEXT[] NOT NULL DEFAULT '{}',
    impact          VARCHAR(10) NOT NULL DEFAULT 'bajo' CHECK (impact IN ('bajo','medio','alto')),
    media           JSONB NOT NULL DEFAULT '{}',    -- {images:[], videoId, wgerName}
    source_key      VARCHAR(60) REFERENCES content_source(key),
    license         VARCHAR(80),
    author          VARCHAR(200),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_movement_name ON movement (lower(name));

CREATE TABLE movement_contraindication (
    movement_key VARCHAR(60) NOT NULL REFERENCES movement(key) ON DELETE CASCADE,
    tag_key      VARCHAR(40) NOT NULL REFERENCES contraindication_tag(key),
    level        VARCHAR(12) NOT NULL DEFAULT 'evitar' CHECK (level IN ('evitar','precaucion')),
    PRIMARY KEY (movement_key, tag_key)
);

-- Un movimiento existe una vez y se asocia a varios mundos con atributos propios de cada uno.
CREATE TABLE world_movement (
    world_key    VARCHAR(30) NOT NULL REFERENCES world(key) ON DELETE CASCADE,
    movement_key VARCHAR(60) NOT NULL REFERENCES movement(key) ON DELETE CASCADE,
    role         VARCHAR(20) NOT NULL DEFAULT 'principal'
                 CHECK (role IN ('principal','calentamiento','vuelta_calma','tecnica','accesorio')),
    level        SMALLINT NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 5),
    pattern      VARCHAR(40),                    -- patrón/ranura dentro del mundo
    attrs        JSONB NOT NULL DEFAULT '{}',    -- p. ej. {"loadable":true,"increment":2.5} o {"phase":1}
    PRIMARY KEY (world_key, movement_key)
);
CREATE INDEX idx_world_movement_pattern ON world_movement(world_key, pattern);

-- Progresiones: árboles de calistenia, fases de rehabilitación, etc.
CREATE TABLE progression (
    key         VARCHAR(60) PRIMARY KEY,
    world_key   VARCHAR(30) NOT NULL REFERENCES world(key) ON DELETE CASCADE,
    name        VARCHAR(80) NOT NULL,
    description TEXT,
    family      VARCHAR(40),                     -- empuje, jale, fase, …
    icon        VARCHAR(16),
    sort_order  SMALLINT NOT NULL DEFAULT 0,
    source_key  VARCHAR(60) REFERENCES content_source(key)
);

CREATE TABLE progression_step (
    progression_key VARCHAR(60) NOT NULL REFERENCES progression(key) ON DELETE CASCADE,
    step_index      SMALLINT NOT NULL,
    name            VARCHAR(100) NOT NULL,
    movement_key    VARCHAR(60) REFERENCES movement(key),
    target          JSONB NOT NULL DEFAULT '{}',  -- prescripción para entrenar este escalón
    unlock_criteria JSONB NOT NULL DEFAULT '{}',  -- {"type":"reps","sets":3,"reps":8} | {"type":"hold","seconds":30} | …
    PRIMARY KEY (progression_key, step_index)
);

CREATE TABLE metric_definition (
    key         VARCHAR(60) PRIMARY KEY,
    world_key   VARCHAR(30) REFERENCES world(key) ON DELETE CASCADE,  -- NULL = métrica global
    name        VARCHAR(80) NOT NULL,
    unit        VARCHAR(20),
    value_type  VARCHAR(20) NOT NULL DEFAULT 'number'
                CHECK (value_type IN ('number','integer','duration_s','pace_s_km','scale_0_10','percent')),
    direction   VARCHAR(10) NOT NULL DEFAULT 'up' CHECK (direction IN ('up','down','range')),
    description TEXT
);

CREATE TABLE assessment_definition (
    key          VARCHAR(60) PRIMARY KEY,
    world_key    VARCHAR(30) NOT NULL REFERENCES world(key) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    description  TEXT NOT NULL,
    instructions JSONB NOT NULL DEFAULT '[]',
    kind         VARCHAR(20) NOT NULL CHECK (kind IN ('count','stopwatch','manual','timer_distance')),
    duration_s   INT,                            -- para 'count' y 'timer_distance'
    metric_key   VARCHAR(60) NOT NULL REFERENCES metric_definition(key),
    unit         VARCHAR(20),
    retest_days  SMALLINT NOT NULL DEFAULT 28,
    norms        JSONB NOT NULL DEFAULT '{}',    -- puntos de corte/clasificación
    safety_note  TEXT,
    source_key   VARCHAR(60) REFERENCES content_source(key),
    sort_order   SMALLINT NOT NULL DEFAULT 0
);

-- ───────────────────────── 4. Estado del usuario por mundo ─────────────────────────

CREATE TABLE user_world (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    world_key               VARCHAR(30) NOT NULL REFERENCES world(key),
    track_key               VARCHAR(40),
    status                  VARCHAR(10) NOT NULL DEFAULT 'activo' CHECK (status IN ('activo','pausado')),
    level                   SMALLINT NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 5),
    profile                 JSONB NOT NULL DEFAULT '{}',   -- respuestas del onboarding del mundo
    state                   JSONB NOT NULL DEFAULT '{}',   -- estado vivo del módulo (cargas sugeridas, fase…)
    onboarding_completed_at TIMESTAMPTZ,
    enrolled_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, world_key),
    FOREIGN KEY (world_key, track_key) REFERENCES world_track(world_key, key)
);
-- Exactamente un mundo activo por usuario.
CREATE UNIQUE INDEX uq_user_world_active ON user_world(user_id) WHERE status = 'activo';

CREATE TABLE user_world_goal (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_world_id UUID NOT NULL REFERENCES user_world(id) ON DELETE CASCADE,
    goal_key      VARCHAR(60) NOT NULL,
    label         VARCHAR(160) NOT NULL,
    target        JSONB NOT NULL DEFAULT '{}',     -- {"metric":"run_5k_estimado_s","value":1800}
    target_date   DATE,
    status        VARCHAR(12) NOT NULL DEFAULT 'activa' CHECK (status IN ('activa','lograda','abandonada')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    achieved_at   TIMESTAMPTZ
);

CREATE TABLE user_progression (
    user_world_id   UUID NOT NULL REFERENCES user_world(id) ON DELETE CASCADE,
    progression_key VARCHAR(60) NOT NULL REFERENCES progression(key) ON DELETE CASCADE,
    current_step    SMALLINT NOT NULL DEFAULT 0,
    best_step       SMALLINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_world_id, progression_key)
);

-- ───────────────────────── 5. Planes ─────────────────────────

CREATE TABLE plan (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_world_id  UUID NOT NULL REFERENCES user_world(id) ON DELETE CASCADE,
    track_key      VARCHAR(40),
    title          VARCHAR(120) NOT NULL,
    summary        TEXT,
    status         VARCHAR(12) NOT NULL DEFAULT 'activo' CHECK (status IN ('activo','archivado')),
    engine_version VARCHAR(20) NOT NULL,
    score          NUMERIC(5,1),
    rationale      JSONB NOT NULL DEFAULT '[]',   -- factores explicables [{key,points,text}]
    weeks          SMALLINT NOT NULL DEFAULT 1,
    starts_on      DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_plan_active ON plan(user_world_id) WHERE status = 'activo';

CREATE TABLE plan_session (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id     UUID NOT NULL REFERENCES plan(id) ON DELETE CASCADE,
    week_index  SMALLINT NOT NULL DEFAULT 0,       -- 0-based
    day_index   SMALLINT NOT NULL DEFAULT 0,       -- orden dentro de la semana
    weekday     SMALLINT CHECK (weekday BETWEEN 1 AND 7),
    title       VARCHAR(120) NOT NULL,
    focus       VARCHAR(160),
    kind        VARCHAR(30) NOT NULL,              -- fuerza, tecnica, intervalos, rodaje, protocolo, partido…
    est_minutes SMALLINT,
    notes       TEXT
);
CREATE INDEX idx_plan_session_plan ON plan_session(plan_id, week_index, day_index);

CREATE TABLE plan_block (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_session_id UUID NOT NULL REFERENCES plan_session(id) ON DELETE CASCADE,
    order_index     SMALLINT NOT NULL,
    block_type      VARCHAR(20) NOT NULL
                    CHECK (block_type IN ('calentamiento','principal','tecnica','intervalo','accesorio','vuelta_calma')),
    movement_key    VARCHAR(60) REFERENCES movement(key),
    title           VARCHAR(120) NOT NULL,
    prescription    JSONB NOT NULL,                -- ver Prescription.java
    progression_key VARCHAR(60) REFERENCES progression(key),
    step_index      SMALLINT,
    notes           TEXT
);
CREATE INDEX idx_plan_block_session ON plan_block(plan_session_id, order_index);

-- ───────────────────────── 6. Registro de actividad ─────────────────────────

CREATE TABLE session_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    user_world_id   UUID NOT NULL REFERENCES user_world(id) ON DELETE CASCADE,
    plan_session_id UUID REFERENCES plan_session(id) ON DELETE SET NULL,
    title           VARCHAR(120),
    performed_on    DATE NOT NULL DEFAULT CURRENT_DATE,
    duration_min    SMALLINT,
    rpe             SMALLINT CHECK (rpe BETWEEN 1 AND 10),
    pain_before     SMALLINT CHECK (pain_before BETWEEN 0 AND 10),
    pain_after      SMALLINT CHECK (pain_after BETWEEN 0 AND 10),
    pain_next_day   SMALLINT CHECK (pain_next_day BETWEEN 0 AND 10),
    mood_before     SMALLINT CHECK (mood_before BETWEEN 1 AND 5),
    mood_after      SMALLINT CHECK (mood_after BETWEEN 1 AND 5),
    distance_m      NUMERIC(9,1),
    completed       BOOLEAN NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_log_world ON session_log(user_world_id, performed_on DESC);

CREATE TABLE block_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_log_id UUID NOT NULL REFERENCES session_log(id) ON DELETE CASCADE,
    plan_block_id  UUID REFERENCES plan_block(id) ON DELETE SET NULL,
    movement_key   VARCHAR(60) REFERENCES movement(key),
    set_index      SMALLINT NOT NULL DEFAULT 0,
    reps           SMALLINT,
    load_kg        NUMERIC(6,2),
    rir            SMALLINT CHECK (rir BETWEEN 0 AND 10),
    hold_s         INT,
    duration_s     INT,
    distance_m     NUMERIC(9,1),
    pain           SMALLINT CHECK (pain BETWEEN 0 AND 10),
    completed      BOOLEAN NOT NULL DEFAULT TRUE,
    data           JSONB NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_block_log_session ON block_log(session_log_id);
CREATE INDEX idx_block_log_movement ON block_log(movement_key);

-- Serie temporal única: cada mundo materializa aquí sus métricas al cerrar una sesión o un test.
CREATE TABLE metric_entry (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    user_world_id UUID REFERENCES user_world(id) ON DELETE CASCADE,
    metric_key    VARCHAR(60) NOT NULL REFERENCES metric_definition(key),
    value         NUMERIC(12,3) NOT NULL,
    recorded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    source        VARCHAR(10) NOT NULL CHECK (source IN ('sesion','test','manual','sistema')),
    source_ref    VARCHAR(80),                   -- id de sesión o clave del test
    context       JSONB NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_metric_entry_series ON metric_entry(user_id, metric_key, recorded_at DESC);
CREATE INDEX idx_metric_entry_world ON metric_entry(user_world_id, recorded_at DESC);

-- ───────────────────────── 7. Descubrimiento y engagement ─────────────────────────

CREATE TABLE world_discovery (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    answers          JSONB NOT NULL,
    ranking          JSONB NOT NULL,
    chosen_world_key VARCHAR(30) REFERENCES world(key),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_streak (
    user_id          UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    current_days     INT NOT NULL DEFAULT 0,
    longest_days     INT NOT NULL DEFAULT 0,
    last_active_date DATE
);

CREATE TABLE achievement (
    user_id     UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    key         VARCHAR(80) NOT NULL,
    world_key   VARCHAR(30) REFERENCES world(key),
    title       VARCHAR(120) NOT NULL,
    icon        VARCHAR(16),
    achieved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, key)
);

CREATE TABLE movement_feedback (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    world_key    VARCHAR(30) NOT NULL REFERENCES world(key),
    movement_key VARCHAR(60) NOT NULL REFERENCES movement(key) ON DELETE CASCADE,
    reaction     VARCHAR(10) NOT NULL CHECK (reaction IN ('like','dislike','swap')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_movement_feedback_user ON movement_feedback(user_id, world_key);

CREATE TABLE favorite_movement (
    user_id      UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    movement_key VARCHAR(60) NOT NULL REFERENCES movement(key) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, movement_key)
);

-- Texto generado por el LLM local para la ficha de un movimiento (se genera una vez).
CREATE TABLE movement_enrichment (
    movement_key    VARCHAR(60) PRIMARY KEY REFERENCES movement(key) ON DELETE CASCADE,
    description     TEXT,
    steps           JSONB NOT NULL DEFAULT '[]',
    common_mistakes JSONB NOT NULL DEFAULT '[]',
    model           VARCHAR(60),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Huella del contenido cargado (para no recargar si no cambió).
CREATE TABLE content_version (
    file_name  VARCHAR(120) PRIMARY KEY,
    sha256     VARCHAR(64) NOT NULL,
    loaded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
