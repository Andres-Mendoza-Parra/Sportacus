package com.sportacus.backend.auth;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.analytics.AnalyticsService;
import com.sportacus.backend.auth.dto.AuthResponse;
import com.sportacus.backend.auth.dto.LoginRequest;
import com.sportacus.backend.auth.dto.RegisterRequest;
import com.sportacus.backend.legal.LegalService;
import com.sportacus.backend.security.JwtService;
import com.sportacus.backend.user.User;
import com.sportacus.backend.user.UserRepository;
import com.sportacus.backend.world.enrollment.EnrollmentService;

import lombok.RequiredArgsConstructor;

/**
 * Registro, inicio de sesión y revocación de tokens. El recibimiento ya no es obligatorio: todos empiezan en Mi Base
 * (ver {@code EnrollmentService.ensureBase}).
 * <ul>
 * <li>Registro: año de nacimiento (≥ 12; 12–17 con autorización de su representante), aceptación obligatoria de la
 * política y los términos (queda en {@code consent_log}) y autorización opcional de datos de salud.</li>
 * <li>Inicio de sesión: 3 fallos seguidos por correo → bloqueo de 15 minutos ({@link LoginThrottle}).</li>
 * <li>JWT revocable: claim {@code tv} = {@code app_user.token_version}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EnrollmentService enrollment;
    private final LoginAttemptRepository attempts;
    private final LegalService legal;
    private final AnalyticsService analytics;
    private final JdbcTemplate jdbc;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.acceptPolicy())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para crear tu cuenta debes aceptar la Política de tratamiento de datos y los Términos");
        }
        AgePolicy.Result age = AgePolicy.check(request.birthYear(), LocalDate.now(ZONE).getYear(),
                request.guardianConsent());
        if (!age.allowed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, age.message());
        }
        PasswordPolicy.problem(request.password()).ifPresent(msg -> {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        });
        String normalizedEmail = normalize(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.name() == null || request.name().isBlank() ? null : request.name().trim())
                .language("es")
                .build();
        user.setOnboardingCompletedAt(OffsetDateTime.now()); // sin recibimiento obligatorio (docs/19)
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException(normalizedEmail); // alta simultánea con el mismo correo
        }
        UUID id = saved.getId();
        jdbc.update("""
                INSERT INTO user_profile (user_id, birth_year) VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET birth_year = EXCLUDED.birth_year
                """, id, request.birthYear());
        boolean healthData = Boolean.TRUE.equals(request.acceptHealthData());
        legal.recordRegistration(id, age.minor(), healthData);
        enrollment.ensureBase(saved); // todos empiezan en Mi Base, con un plan suave listo

        // Analítica DESPUÉS de confirmar el alta (el evento referencia al usuario).
        afterCommit(() -> {
            analytics.track(id, "signup", Map.of("minor", age.minor(), "healthData", healthData));
            analytics.track(id, "policy_accepted", Map.of("document", "politica_datos", "version",
                    legal.currentVersion("politica_datos"), "at", "registro"));
            if (healthData) analytics.track(id, "consent_changed", Map.of("area", "salud", "granted", true));
        });
        return toResponse(saved, 0);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        OffsetDateTime now = OffsetDateTime.now();
        LoginThrottle.State state = attempts.find(email);
        long locked = LoginThrottle.lockedSeconds(state, now);
        if (locked > 0) {
            throw new LoginLockedException(locked);
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException e) {
            LoginThrottle.State next = LoginThrottle.afterFailure(state, now);
            attempts.save(email, next);
            if (next.lockedUntil() != null) {
                UUID userId = userRepository.findByEmail(email).map(User::getId).orElse(null);
                analytics.track(userId, "login_locked");
                throw new LoginLockedException(LoginThrottle.lockedSeconds(next, now));
            }
            throw new LoginFailedException(LoginThrottle.remaining(next));
        }
        if (state.failures() > 0 || state.lockedUntil() != null) {
            attempts.reset(email);
        }
        User user = userRepository.findByEmail(email).orElseThrow(() -> new LoginFailedException(LoginThrottle.MAX_FAILURES));
        analytics.track(user.getId(), "login");
        return toResponse(user, user.tokenVersionOrZero());
    }

    /** Invalida todos los tokens emitidos hasta ahora y devuelve la nueva versión. */
    public int revokeAllTokens(UUID userId) {
        Integer v = jdbc.queryForObject(
                "UPDATE app_user SET token_version = token_version + 1 WHERE id = ? RETURNING token_version",
                Integer.class, userId);
        return v == null ? 0 : v;
    }

    /** Tras cambiar la contraseña: revoca los tokens anteriores y entrega uno nuevo para este dispositivo. */
    public AuthResponse afterPasswordChange(User user) {
        int version = revokeAllTokens(user.getId());
        return toResponse(user, version);
    }

    private AuthResponse toResponse(User user, int tokenVersion) {
        return new AuthResponse(jwtService.generateToken(user, tokenVersion), user.getId(), user.getEmail(),
                user.visibleName(), true); // ya no hay recibimiento obligatorio; se conserva el campo por compatibilidad
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
