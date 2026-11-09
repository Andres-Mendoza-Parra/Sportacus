package com.sportacus.backend.rag;

/** El motor de IA local (Ollama) no respondió: sin workflow externo que lo sustituya, se informa tal cual. */
public class RagUnavailableException extends RuntimeException {

    public RagUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
