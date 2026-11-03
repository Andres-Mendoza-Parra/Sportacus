package com.sportacus.backend.recommendation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.recommendation.dto.ExerciseDetailResponse;
import com.sportacus.backend.recommendation.dto.LibraryDtos.AddToRoutineRequest;
import com.sportacus.backend.recommendation.dto.LibraryDtos.LibraryExercise;
import com.sportacus.backend.recommendation.dto.LibraryDtos.LibraryFilters;
import com.sportacus.backend.recommendation.dto.RoutineResponse;
import com.sportacus.backend.user.User;

/** Biblioteca de ejercicios: búsqueda con filtros, detalle y "añadir a mi rutina". */
@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final RecommendationService service;

    public LibraryController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping("/exercises")
    public List<LibraryExercise> search(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String muscle,
            @RequestParam(required = false) String equipment,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(defaultValue = "false") boolean favoritesOnly,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.searchLibrary(currentUser.getId(), q, muscle, equipment, difficulty, favoritesOnly, limit,
                offset);
    }

    @GetMapping("/filters")
    public LibraryFilters filters(@AuthenticationPrincipal User currentUser) {
        return service.libraryFilters(currentUser.getId());
    }

    @GetMapping("/exercises/{exerciseId}")
    public ExerciseDetailResponse detail(@AuthenticationPrincipal User currentUser, @PathVariable UUID exerciseId) {
        return service.getLibraryDetail(currentUser.getId(), exerciseId);
    }

    @PostMapping("/add-to-routine")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.OK)
    public RoutineResponse addToRoutine(@AuthenticationPrincipal User currentUser,
            @RequestBody AddToRoutineRequest request) {
        if (request == null || request.exerciseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exerciseId es obligatorio");
        }
        UUID id;
        try {
            id = UUID.fromString(request.exerciseId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exerciseId no es válido");
        }
        return service.addToRoutine(currentUser.getId(), id);
    }

    @PostMapping("/exercises/{exerciseId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavorite(@AuthenticationPrincipal User currentUser, @PathVariable UUID exerciseId) {
        service.addFavorite(currentUser.getId(), exerciseId);
    }

    @DeleteMapping("/exercises/{exerciseId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@AuthenticationPrincipal User currentUser, @PathVariable UUID exerciseId) {
        service.removeFavorite(currentUser.getId(), exerciseId);
    }
}
