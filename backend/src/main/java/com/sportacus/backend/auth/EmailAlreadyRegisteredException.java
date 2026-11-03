package com.sportacus.backend.auth;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("Ya existe una cuenta registrada con el correo: " + email);
    }
}
