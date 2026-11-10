package com.sportacus.backend.profile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.sportacus.backend.auth.AuthService;
import com.sportacus.backend.auth.PasswordPolicy;
import com.sportacus.backend.auth.dto.AuthResponse;
import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthService;
import com.sportacus.backend.legal.LegalService;
import com.sportacus.backend.user.User;
import com.sportacus.backend.world.form.FormSpec;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profiles;
    private final Profile360Service profile360;
    private final HealthService health;
    private final LegalService legal;
    private final AuthService auth;
    private final JdbcTemplate jdbc;

    public ProfileController(ProfileService profiles, Profile360Service profile360, HealthService health,
            LegalService legal, AuthService auth, JdbcTemplate jdbc) {
        this.profile360 = profile360;
        this.health = health;
        this.profiles = profiles;
        this.legal = legal;
        this.auth = auth;
        this.jdbc = jdbc;
    }

    /**
     * AccountView (de ProfileService) + estado legal y del recorrido inicial. Los campos de AccountView se aplanan en
     * el mismo objeto JSON, así que la respuesta es compatible con la anterior.
     */
    public record AccountResponse(@JsonUnwrapped ProfileService.AccountView account, LegalService.LegalStatus legal,
            @JsonInclude(JsonInclude.Include.ALWAYS) OffsetDateTime tutorialCompletedAt) {
    }

    private AccountResponse withLegal(User user, ProfileService.AccountView view) {
        return new AccountResponse(view, legal.status(user.getId()), user.getTutorialCompletedAt());
    }

    @GetMapping
    public AccountResponse account(@AuthenticationPrincipal User user) {
        return withLegal(user, profiles.account(user));
    }

    @GetMapping("/form")
    public FormSpec form(@AuthenticationPrincipal User user) {
        return profiles.form(user);
    }

    @PostMapping("/form")
    public AccountResponse saveForm(@AuthenticationPrincipal User user, @RequestBody Map<String, Object> answers) {
        return withLegal(user, profiles.saveForm(user, new Answers(answers)));
    }

    @PatchMapping
    public AccountResponse patch(@AuthenticationPrincipal User user, @RequestBody ProfileService.PatchRequest body) {
        return withLegal(user, profiles.patch(user, body));
    }

    /**
     * Cambia la contraseña (misma política que el registro) y revoca todos los tokens anteriores. Devuelve un token
     * nuevo para seguir con la sesión en este dispositivo; los demás dispositivos deben volver a entrar.
     */
    @PostMapping("/password")
    public AuthResponse password(@AuthenticationPrincipal User user, @RequestBody ProfileService.PasswordRequest body) {
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan las contraseñas");
        if (body.newPassword() != null) {
            PasswordPolicy.problem(body.newPassword()).ifPresent(msg -> {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            });
        }
        profiles.changePassword(user, body);
        return auth.afterPasswordChange(user);
    }

    public record TutorialRequest(Boolean completed) {
    }

    /** Recorrido inicial terminado o saltado ({@code completed: true}); {@code false} lo deja pendiente otra vez. */
    @PostMapping("/tutorial")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void tutorial(@AuthenticationPrincipal User user, @RequestBody(required = false) TutorialRequest body) {
        boolean completed = body == null || body.completed() == null || body.completed();
        jdbc.update(completed
                ? "UPDATE app_user SET tutorial_completed_at = COALESCE(tutorial_completed_at, now()) WHERE id = ?"
                : "UPDATE app_user SET tutorial_completed_at = NULL WHERE id = ?", user.getId());
    }

    @GetMapping("/export")
    public Map<String, Object> export(@AuthenticationPrincipal User user) {
        return profiles.export(user);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user) {
        profiles.delete(user);
    }

    // ── Perfil 360 (docs/19) ──────────────────────────────────

    @GetMapping("/overview")
    public Profile360Service.Overview overview(@AuthenticationPrincipal User user) {
        return profile360.overview(user.getId());
    }

    @GetMapping("/questionnaires/{key}")
    public Profile360Service.QuestionnaireView questionnaire(@AuthenticationPrincipal User user,
            @PathVariable String key) {
        return profile360.questionnaire(user.getId(), key);
    }

    @PostMapping("/questionnaires/{key}")
    public Profile360Service.SaveResult saveQuestionnaire(@AuthenticationPrincipal User user, @PathVariable String key,
            @RequestBody Map<String, Object> answers) {
        return profile360.saveQuestionnaire(user.getId(), key, answers);
    }

    @GetMapping("/next")
    public List<Profile360Service.NextItem> next(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "2") int limit, @RequestParam(defaultValue = "home") String context) {
        return profile360.next(user.getId(), Math.max(1, Math.min(10, limit)), context);
    }

    public record AnswerRequest(String questionId, Object value) {
    }

    @PostMapping("/answers")
    public Profile360Service.SaveResult answer(@AuthenticationPrincipal User user, @RequestBody AnswerRequest body) {
        if (body.questionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta questionId");
        }
        return profile360.answer(user.getId(), body.questionId(), body.value());
    }

    public record SkipRequest(String mode) {
    }

    @PostMapping("/answers/{questionId}/skip")
    public List<Profile360Service.NextItem> skip(@AuthenticationPrincipal User user, @PathVariable String questionId,
            @RequestBody(required = false) SkipRequest body) {
        return profile360.skip(user.getId(), questionId, body == null ? null : body.mode());
    }

    public record ConsentRequest(Boolean granted) {
    }

    @PostMapping("/consent/{area}")
    public Profile360Service.AreaView consent(@AuthenticationPrincipal User user, @PathVariable String area,
            @RequestBody ConsentRequest body) {
        return profile360.consent(user.getId(), area, body != null && Boolean.TRUE.equals(body.granted()));
    }

    @GetMapping("/checkin")
    public Profile360Service.CheckinView checkin(@AuthenticationPrincipal User user) {
        return profile360.checkin(user.getId(), health.summary(user.getId()));
    }

    @PostMapping("/checkin")
    public Profile360Service.CheckinResult saveCheckin(@AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> answers) {
        return profile360.saveCheckin(user.getId(), answers, health.summary(user.getId()));
    }
}
