package com.sportacus.backend.media;

import java.util.List;

/**
 * Material visual y explicativo de un ejercicio, obtenido de la API libre de wger (https://wger.de).
 * Cualquier campo puede venir vacío: si no hay coincidencia solo se devuelven las imágenes de músculos.
 */
public record ExerciseMedia(
        List<String> imageUrls,
        String thumbUrl,
        String videoUrl,
        List<String> muscleImageUrls,
        List<MuscleView> muscleViews,
        List<String> instructions,
        String description,
        String equipment,
        String source) {

    /** Silueta del cuerpo (frente o espalda) con los músculos resaltados encima, en capas transparentes. */
    public record MuscleView(String baseUrl, List<String> overlayUrls) {
    }

    public static ExerciseMedia empty() {
        return new ExerciseMedia(List.of(), null, null, List.of(), List.of(), List.of(), null, null, null);
    }
}
