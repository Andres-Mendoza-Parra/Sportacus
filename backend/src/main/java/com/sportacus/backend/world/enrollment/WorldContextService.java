package com.sportacus.backend.world.enrollment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.profile.UserContextService;
import com.sportacus.backend.user.User;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.UserWorldData;
import com.sportacus.backend.world.core.WorldContext;

/** Resuelve el mundo sobre el que opera cada petición (por defecto, el activo del usuario). */
@Service
public class WorldContextService {

    private final UserWorldRepository userWorlds;
    private final UserContextService userContexts;
    private final EnrollmentService enrollment;

    public WorldContextService(UserWorldRepository userWorlds, UserContextService userContexts,
            @org.springframework.context.annotation.Lazy EnrollmentService enrollment) {
        this.enrollment = enrollment;
        this.userWorlds = userWorlds;
        this.userContexts = userContexts;
    }

    /** @param worldKey mundo pedido explícitamente o null para el activo */
    public WorldContext resolve(User user, String worldKey) {
        if ((worldKey == null || worldKey.isBlank() || EnrollmentService.BASE.equals(worldKey))
                && userWorlds.find(user.getId(), EnrollmentService.BASE).isEmpty()) {
            enrollment.ensureBase(user); // usuarios anteriores a Mi Base
        }
        UserContext uc = userContexts.of(user);
        UserWorldData uw = (worldKey == null || worldKey.isBlank()
                ? userWorlds.findActive(user.getId())
                : userWorlds.find(user.getId(), worldKey))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        worldKey == null ? "Aún no has elegido un mundo" : "No estás inscrito en ese mundo"));
        return new WorldContext(uc, uw);
    }

    /** Contexto de una inscripción concreta del usuario (p. ej. el mundo de un registro antiguo), activa o no. */
    public WorldContext forUserWorld(User user, java.util.UUID userWorldId) {
        UserWorldData uw = userWorlds.byId(userWorldId).filter(w -> w.userId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos ese mundo"));
        return new WorldContext(userContexts.of(user), uw);
    }

    /** Recarga la inscripción (tras cambios de estado o progresión) conservando el contexto de usuario. */
    public WorldContext reload(WorldContext ctx) {
        return new WorldContext(ctx.user(), userWorlds.byId(ctx.world().id()).orElse(ctx.world()));
    }
}
