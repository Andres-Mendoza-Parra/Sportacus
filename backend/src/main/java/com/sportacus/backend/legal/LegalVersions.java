package com.sportacus.backend.legal;

import java.util.Map;
import java.util.Optional;

/** Reglas puras de versiones de los documentos legales. */
public final class LegalVersions {

    private LegalVersions() {
    }

    /** ¿La versión aceptada es la vigente? Un usuario sin aceptación registrada debe aceptar. */
    public static boolean upToDate(String current, String accepted) {
        return current != null && current.equals(accepted);
    }

    /**
     * Valida una aceptación contra las versiones vigentes ({@code documento → versión}). Devuelve el problema para la
     * persona o vacío si es válida.
     */
    public static Optional<String> acceptanceProblem(String document, String version, Map<String, String> current) {
        if (document == null || !current.containsKey(document)) {
            return Optional.of("Documento desconocido");
        }
        if (version == null || version.isBlank()) {
            return Optional.of("Falta la versión del documento");
        }
        if (!current.get(document).equals(version.trim())) {
            return Optional.of("Esa versión ya no está vigente. Revisa la versión actual del documento");
        }
        return Optional.empty();
    }
}
