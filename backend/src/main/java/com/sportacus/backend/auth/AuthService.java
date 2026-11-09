package com.sportacus.backend.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sportacus.backend.auth.dto.AuthResponse;
import com.sportacus.backend.auth.dto.LoginRequest;
import com.sportacus.backend.auth.dto.RegisterRequest;
import com.sportacus.backend.security.JwtService;
import com.sportacus.backend.user.User;
import com.sportacus.backend.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Registro e inicio de sesión. El recibimiento (perfil → salud → mundo) ya no se marca con un
 * endpoint propio: queda completo cuando el usuario se inscribe en su primer mundo
 * (ver {@code EnrollmentService}).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.name() == null || request.name().isBlank() ? null : request.name().trim())
                .language("es")
                .build();

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getEmail(), user.visibleName(),
                user.getOnboardingCompletedAt() != null);
    }
}
