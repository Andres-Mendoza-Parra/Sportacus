-- ═══════════════════════════════════════════════════════════════════════════════
-- Planes dinámicos (docs/22): el plan guarda la "huella" de los datos con que se generó; si cambian
-- (salud, perfil, Perfil 360, mundo, semana), se regenera solo al consultarlo.
-- Analítica de producto: registro de eventos propio (sin terceros).
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE plan ADD COLUMN input_hash   VARCHAR(64);
ALTER TABLE plan ADD COLUMN input_parts  JSONB NOT NULL DEFAULT '{}';   -- huella por categoría (salud, perfil…)
ALTER TABLE plan ADD COLUMN update_reason TEXT;                          -- por qué se regeneró (para el usuario)

CREATE TABLE event_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID REFERENCES app_user(id) ON DELETE CASCADE,
    name        VARCHAR(60) NOT NULL,
    props       JSONB NOT NULL DEFAULT '{}',
    source      VARCHAR(10) NOT NULL DEFAULT 'server' CHECK (source IN ('server','app')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_event_log_name ON event_log(name, occurred_at);
CREATE INDEX idx_event_log_user ON event_log(user_id, occurred_at);
