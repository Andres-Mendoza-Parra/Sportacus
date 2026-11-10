-- ═══════════════════════════════════════════════════════════════════════════════
-- Autenticación y base legal (auditoría docs/21: C1, C4, A10, A11).
--  · token_version: el JWT lleva la versión (claim "tv"); cambiar la contraseña o "cerrar todas las sesiones"
--    la incrementa y los tokens anteriores dejan de servir.
--  · tutorial_completed_at: recorrido inicial terminado (o saltado).
--  · login_attempt: 3 fallos seguidos para el mismo correo → bloqueo de 15 minutos (también para correos que no
--    existen, para no revelar si una cuenta existe).
--  · consent_log: registro de cada aceptación o revocación (Ley 1581 de 2012, art. 9; Decreto 1377 de 2013,
--    art. 7: el responsable debe conservar prueba de la autorización).
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE app_user ADD COLUMN token_version         INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN tutorial_completed_at TIMESTAMPTZ;

CREATE TABLE login_attempt (
    email           VARCHAR(254) PRIMARY KEY,          -- correo normalizado (exista o no la cuenta)
    failures        SMALLINT    NOT NULL DEFAULT 0,
    last_failure_at TIMESTAMPTZ,
    locked_until    TIMESTAMPTZ
);

CREATE TABLE consent_log (
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    -- politica_datos | terminos | datos_salud | autorizacion_representante | <área sensible del Perfil 360>
    document   VARCHAR(40) NOT NULL,
    version    VARCHAR(20),                             -- versión del texto aceptado (NULL en consentimientos por área)
    granted    BOOLEAN     NOT NULL,
    source     VARCHAR(20) NOT NULL DEFAULT 'app' CHECK (source IN ('registro','app','reaceptacion')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_consent_log_user ON consent_log(user_id, document, created_at DESC);
