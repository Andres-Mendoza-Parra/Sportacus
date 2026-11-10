package com.sportacus.backend.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Un fragmento de conocimiento listo para vectorizar.
 *
 * @param sourceType tipo de fuente (movement, guide, legal…); junto con sourceId identifica el fragmento
 * @param worldKey   mundo al que pertenece, o null si es general
 * @param title      nombre corto que el chat muestra como fuente
 * @param content    texto completo que se vectoriza y se entrega al modelo
 * @param reference  de dónde sale la información (cita bibliográfica o "catálogo de Sportacus")
 */
public record KnowledgeDoc(String sourceType, String sourceId, String worldKey, String title, String content,
        String reference) {

    /** Huella del texto y del título: si no cambia, no hace falta volver a generar el vector. */
    public String hash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((title + "\n" + content + "\n" + (reference == null ? "" : reference) + "\n" + worldKey)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Texto que se vectoriza: el título ayuda a que la búsqueda encuentre el fragmento por su tema. */
    public String embeddingText() {
        return title + ". " + content;
    }
}
