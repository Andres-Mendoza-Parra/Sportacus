package com.sportacus.backend.profile;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sportacus.backend.health.HealthService;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.user.User;
import com.sportacus.backend.world.core.UserContext;

/** Arma el {@link UserContext} (perfil común + salud + Perfil 360 derivado) que reciben todos los mundos. */
@Service
public class UserContextService {

    private final ProfileRepository profileRepository;
    private final HealthService healthService;
    private final ProfileFactsService facts;

    public UserContextService(ProfileRepository profileRepository, HealthService healthService,
            ProfileFactsService facts) {
        this.profileRepository = profileRepository;
        this.healthService = healthService;
        this.facts = facts;
    }

    public UserContext of(User user) {
        return of(user.getId(), user.visibleName());
    }

    public UserContext of(UUID userId, String name) {
        HealthSummary h = healthService.summary(userId);
        return new UserContext(userId, name, profileRepository.load(userId), h, facts.insights(userId, h));
    }
}
