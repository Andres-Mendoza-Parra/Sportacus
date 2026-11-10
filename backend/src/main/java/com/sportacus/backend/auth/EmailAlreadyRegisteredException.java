package com.sportacus.backend.auth;

/** Mensaje genérico a propósito: no confirma a terceros que el correo tiene una cuenta. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public static final String MESSAGE =
            "No pudimos crear la cuenta con ese correo. Si ya tienes una cuenta, inicia sesión.";

    public EmailAlreadyRegisteredException(String email) {
        super(MESSAGE);
    }
}
