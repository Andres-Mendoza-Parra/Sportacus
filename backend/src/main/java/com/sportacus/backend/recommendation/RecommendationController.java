package com.sportacus.backend.recommendation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.recommendation.dto.ExerciseDetailResponse;
import com.sportacus.backend.recommendation.dto.RouteOptionResponse;
import com.sportacus.backend.recommendation.dto.RoutineRequests.AddDayRequest;
import com.sportacus.backend.recommendation.dto.RoutineRequests.AddExerciseToDayRequest;
import com.sportacus.backend.recommendation.dto.RoutineRequests.DoneRequest;
import com.sportacus.backend.recommendation.dto.RoutineRequests.DoneResponse;
import com.sportacus.backend.recommendation.dto.RoutineRequests.FeedbackRequest;
import com.sportacus.backend.recommendation.dto.RoutineRequests.RegenerateRequest;
import com.sportacus.backend.recommendation.dto.RoutineRequests.SelectRequest;
import com.sportacus.backend.recommendation.dto.RoutineRequests.UpdateExerciseRequest;
import com.sportacus.backend.recommendation.dto.RoutineResponse;
import com.sportacus.backend.user.User;

@RestController
@RequestMapping("/api/routine")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping
    public RoutineResponse getRoutine(@AuthenticationPrincipal User currentUser) {
        return service.getRoutine(currentUser.getId());
    }

    @GetMapping("/options")
    public List<RouteOptionResponse> getOptions(@AuthenticationPrincipal User currentUser) {
        return service.getOptions(currentUser.getId());
    }

    @PostMapping("/select")
    public RoutineResponse select(@AuthenticationPrincipal User currentUser, @RequestBody SelectRequest request) {
        if (request == null || request.activityId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activityId es obligatorio");
        }
        return service.selectActivity(currentUser.getId(), request.activityId());
    }

    @PostMapping("/regenerate")
    public RoutineResponse regenerate(@AuthenticationPrincipal User currentUser,
            @RequestBody(required = false) RegenerateRequest request) {
        return service.regenerate(currentUser.getId(), request == null ? null : request.mode());
    }

    @GetMapping("/exercises/{exerciseRecommendationId}")
    public ExerciseDetailResponse getExerciseDetail(
            @AuthenticationPrincipal User currentUser, @PathVariable UUID exerciseRecommendationId) {
        return service.getExerciseDetail(currentUser.getId(), exerciseRecommendationId);
    }

    @PostMapping("/exercises/{exerciseRecommendationId}/swap")
    public RoutineResponse swap(@AuthenticationPrincipal User currentUser,
            @PathVariable UUID exerciseRecommendationId) {
        return service.swapExercise(currentUser.getId(), exerciseRecommendationId);
    }

    @PostMapping("/exercises/{exerciseRecommendationId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void feedback(@AuthenticationPrincipal User currentUser, @PathVariable UUID exerciseRecommendationId,
            @RequestBody FeedbackRequest request) {
        service.setFeedback(currentUser.getId(), exerciseRecommendationId, request == null ? null : request.reaction());
    }

    @PutMapping("/exercises/{exerciseRecommendationId}/done")
    public DoneResponse setDone(@AuthenticationPrincipal User currentUser, @PathVariable UUID exerciseRecommendationId,
            @RequestBody DoneRequest request) {
        if (request == null || request.done() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "done es obligatorio (true o false)");
        }
        return service.setDone(currentUser.getId(), exerciseRecommendationId, request.done());
    }

    @PostMapping("/exercises/{exerciseRecommendationId}/log")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logExercise(@AuthenticationPrincipal User currentUser, @PathVariable UUID exerciseRecommendationId) {
        service.logExercise(currentUser.getId(), exerciseRecommendationId);
    }

    // ───────────────────────── Taller de rutina ─────────────────────────

    @PostMapping("/days")
    public RoutineResponse addDay(@AuthenticationPrincipal User currentUser, @RequestBody AddDayRequest request) {
        return service.addDay(currentUser.getId(), request == null ? null : request.label());
    }

    @DeleteMapping("/days/{dayIndex}")
    public RoutineResponse removeDay(@AuthenticationPrincipal User currentUser, @PathVariable int dayIndex) {
        return service.removeDay(currentUser.getId(), dayIndex);
    }

    @PostMapping("/days/{dayIndex}/exercises")
    public RoutineResponse addExerciseToDay(@AuthenticationPrincipal User currentUser, @PathVariable int dayIndex,
            @RequestBody AddExerciseToDayRequest request) {
        if (request == null || request.exerciseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exerciseId es obligatorio");
        }
        return service.addExerciseToDay(currentUser.getId(), dayIndex, request.exerciseId(), request.sets(),
                request.reps(), request.durationSec());
    }

    @PutMapping("/exercises/{exerciseRecommendationId}")
    public RoutineResponse updateExercise(@AuthenticationPrincipal User currentUser,
            @PathVariable UUID exerciseRecommendationId, @RequestBody UpdateExerciseRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuerpo de la petición obligatorio");
        }
        return service.updateExercise(currentUser.getId(), exerciseRecommendationId, request.sets(), request.reps(),
                request.durationSec(), request.dayIndex());
    }

    @DeleteMapping("/exercises/{exerciseRecommendationId}")
    public RoutineResponse removeExercise(@AuthenticationPrincipal User currentUser,
            @PathVariable UUID exerciseRecommendationId) {
        return service.removeExercise(currentUser.getId(), exerciseRecommendationId);
    }
}
