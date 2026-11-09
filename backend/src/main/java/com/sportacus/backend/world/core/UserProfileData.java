package com.sportacus.backend.world.core;

import java.time.Year;
import java.util.List;

/** Perfil común (tabla user_profile): solo datos universales, lo propio de cada mundo va en user_world.profile. */
public record UserProfileData(
        Integer birthYear,
        String sex,
        Double weightKg,
        Double heightCm,
        Integer activityLevel,
        Integer daysPerWeek,
        Integer minutesPerSession,
        List<Integer> preferredDays,
        List<String> environments,
        List<String> equipment,
        boolean completed) {

    public static UserProfileData empty() {
        return new UserProfileData(null, null, null, null, null, null, null, List.of(), List.of(), List.of(), false);
    }

    public Integer age() {
        return birthYear == null ? null : Year.now().getValue() - birthYear;
    }

    public int ageOr(int fallback) {
        Integer a = age();
        return a == null ? fallback : a;
    }

    public Double bmi() {
        if (weightKg == null || heightCm == null || heightCm <= 0) return null;
        double m = heightCm / 100.0;
        return Math.round(weightKg / (m * m) * 10) / 10.0;
    }

    public int daysOr(int fallback) {
        return daysPerWeek == null ? fallback : daysPerWeek;
    }

    public int minutesOr(int fallback) {
        return minutesPerSession == null ? fallback : minutesPerSession;
    }

    public int activityOr(int fallback) {
        return activityLevel == null ? fallback : activityLevel;
    }

    public boolean hasEnvironment(String env) {
        return environments != null && environments.contains(env);
    }

    public boolean hasEquipment(String key) {
        return equipment != null && equipment.contains(key);
    }
}
