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
import com.sportacus.backend.user.UserNameRepository;
import com.sportacus.backend.user.UserRepository;
import com.sportacus.backend.user.UserStatusRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserNameRepository userNameRepository;
    private final UserStatusRepository userStatusRepository;
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
                .language("es")
                .build();

        User saved = userRepository.save(user);
        userNameRepository.save(saved.getId(), request.name());
        String token = jwtService.generateToken(saved);

        return new AuthResponse(token, saved.getId(), saved.getEmail(), userNameRepository.displayNameOf(saved), false);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException());

        String token = jwtService.generateToken(user);

        return new AuthResponse(token, user.getId(), user.getEmail(), userNameRepository.displayNameOf(user),
                userStatusRepository.isOnboardingComplete(user.getId()));
    }

    public void completeOnboarding(User currentUser) {
        userStatusRepository.markOnboardingComplete(currentUser.getId());
    }
}
