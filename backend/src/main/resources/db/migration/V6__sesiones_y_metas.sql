-- ═══════════════════════════════════════════════════════════════════════════════
-- Sesiones, permanencia y metas (auditoría docs/21: A1, A2, A5, A7, A20 y medios de integridad).
--  · session_log.client_id: UUID que genera la app al iniciar la sesión. Reenviar el mismo registro (doble toque,
--    reintento sin conexión, sesión expirada) devuelve el resultado ya guardado en session_log.result.
--  · user_world_goal.baseline: punto de partida para medir el avance (escalón, marca o conteo al crear la meta).
--  · user_world_goal.achieved_by: si la meta se marcó sola (auto) o la marcó la persona (manual).
--  · "Prefiero no decir" en una pregunta p_* del Perfil 360 borra el dato del perfil común.
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE session_log ADD COLUMN client_id UUID;
ALTER TABLE session_log ADD COLUMN result    JSONB;
CREATE UNIQUE INDEX uq_session_log_client ON session_log(user_id, client_id) WHERE client_id IS NOT NULL;

ALTER TABLE user_world_goal ADD COLUMN baseline    NUMERIC(12,3);
ALTER TABLE user_world_goal ADD COLUMN achieved_by VARCHAR(10) CHECK (achieved_by IN ('auto','manual'));
CREATE INDEX idx_user_world_goal_world ON user_world_goal(user_world_id, status);

-- La racha cuenta días con actividad, no sesiones.
UPDATE achievement SET title = 'Racha de 7 días'  WHERE key = 'racha_7';
UPDATE achievement SET title = 'Racha de 30 días' WHERE key = 'racha_30';

-- "Prefiero no decir" en el perfil común: se borra el valor guardado. El año de nacimiento se conserva porque lo
-- exige la política de edad (12+ y autorización de menores) y los topes de seguridad; solo se oculta del Perfil 360.
CREATE OR REPLACE FUNCTION clear_profile_field_on_no_decir() RETURNS trigger AS $$
BEGIN
    IF NEW.status = 'no_decir' THEN
        CASE NEW.question_key
            WHEN 'p_sex'            THEN UPDATE user_profile SET sex = NULL                 WHERE user_id = NEW.user_id;
            WHEN 'p_weight'         THEN UPDATE user_profile SET weight_kg = NULL           WHERE user_id = NEW.user_id;
            WHEN 'p_height'         THEN UPDATE user_profile SET height_cm = NULL           WHERE user_id = NEW.user_id;
            WHEN 'p_activity'       THEN UPDATE user_profile SET activity_level = NULL      WHERE user_id = NEW.user_id;
            WHEN 'p_days'           THEN UPDATE user_profile SET days_per_week = NULL       WHERE user_id = NEW.user_id;
            WHEN 'p_minutes'        THEN UPDATE user_profile SET minutes_per_session = NULL WHERE user_id = NEW.user_id;
            WHEN 'p_preferred_days' THEN UPDATE user_profile SET preferred_days = '{}'      WHERE user_id = NEW.user_id;
            WHEN 'p_environments'   THEN UPDATE user_profile SET environments = '{}'        WHERE user_id = NEW.user_id;
            WHEN 'p_equipment'      THEN UPDATE user_profile SET equipment = '{}'           WHERE user_id = NEW.user_id;
            ELSE NULL;
        END CASE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_profile_question_no_decir
    AFTER INSERT OR UPDATE OF status ON profile_question_state
    FOR EACH ROW EXECUTE FUNCTION clear_profile_field_on_no_decir();

-- Datos ya guardados antes de esta versión.
UPDATE user_profile p SET sex = NULL FROM profile_question_state s
 WHERE s.user_id = p.user_id AND s.status = 'no_decir' AND s.question_key = 'p_sex';
UPDATE user_profile p SET weight_kg = NULL FROM profile_question_state s
 WHERE s.user_id = p.user_id AND s.status = 'no_decir' AND s.question_key = 'p_weight';
UPDATE user_profile p SET height_cm = NULL FROM profile_question_state s
 WHERE s.user_id = p.user_id AND s.status = 'no_decir' AND s.question_key = 'p_height';
UPDATE user_profile p SET activity_level = NULL FROM profile_question_state s
 WHERE s.user_id = p.user_id AND s.status = 'no_decir' AND s.question_key = 'p_activity';
