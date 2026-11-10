-- ═══════════════════════════════════════════════════════════════════════════════
-- Perfil 360 y "Mi Base" (docs/19). Principio de no presión: nada es obligatorio; cada respuesta es
-- un dato con fecha e historial y el usuario decide qué contar y cuándo.
-- ═══════════════════════════════════════════════════════════════════════════════

-- Mi Base es un mundo interno: no sale en la galería pero funciona como cualquier otro.
ALTER TABLE world DROP CONSTRAINT IF EXISTS world_status_check;
ALTER TABLE world ADD CONSTRAINT world_status_check CHECK (status IN ('activo','proximamente','oculto','base'));

-- Historial de respuestas (append-only). La respuesta vigente es la más reciente por pregunta.
-- Las de áreas sensibles van cifradas (AES-256-GCM) en value_encrypted y value queda NULL.
CREATE TABLE profile_answer (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    question_key    VARCHAR(60) NOT NULL,
    area_key        VARCHAR(40) NOT NULL,
    value           JSONB,
    value_encrypted TEXT,
    source          VARCHAR(20) NOT NULL DEFAULT 'cuestionario'
                    CHECK (source IN ('cuestionario','sugerida','checkin','mundo','perfil')),
    answered_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (value IS NOT NULL OR value_encrypted IS NOT NULL)
);
CREATE INDEX idx_profile_answer_latest ON profile_answer(user_id, question_key, answered_at DESC);
CREATE INDEX idx_profile_answer_area ON profile_answer(user_id, area_key);

-- Estado de cada pregunta para el usuario: respondida, omitida (30 días), pospuesta (3 días) o "prefiero no decir".
CREATE TABLE profile_question_state (
    user_id      UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    question_key VARCHAR(60) NOT NULL,
    status       VARCHAR(12) NOT NULL CHECK (status IN ('respondida','omitida','pospuesta','no_decir')),
    until        TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, question_key)
);

-- Consentimiento explícito por área sensible (Ley 1581 de 2012, art. 6: datos sensibles).
CREATE TABLE profile_consent (
    user_id    UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    area_key   VARCHAR(40) NOT NULL,
    granted    BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, area_key)
);
