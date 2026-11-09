package com.sportacus.backend.profile;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sportacus.backend.health.HealthService;
import com.sportacus.backend.user.User;
import com.sportacus.backend.world.core.UserContext;

/** Arma el {@link UserContext} (perfil común + salud) que reciben todos los mundos. */
@Service
public class UserContextService {

    private final ProfileRepository profileRepository;
    private final HealthService healthService;

    public UserContextService(ProfileRepository profileRepository, HealthService healthService) {
        this.profileRepository = profileRepository;
        this.healthService = healthService;
    }

    public UserContext of(User user) {
        return of(user.getId(), user.visibleName());
    }

    public UserContext of(UUID userId, String name) {
        return new UserContext(userId, name, profileRepository.load(userId), healthService.summary(userId));
    }
}
