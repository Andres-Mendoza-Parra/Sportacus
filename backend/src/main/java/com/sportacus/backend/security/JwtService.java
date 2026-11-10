package com.sportacus.backend.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import com.sportacus.backend.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite y valida los JWT (auditoría C1 y A11).
 * <ul>
 * <li>El secreto sale de {@code JWT_SECRET}. El valor de desarrollo de {@code application.yml} solo se acepta fuera
 * del perfil {@code prod}: en producción sin la variable la aplicación no arranca.</li>
 * <li>El token lleva {@code iss}/{@code aud} y la versión del usuario ({@code tv}). Cambiar la contraseña o cerrar
 * todas las sesiones incrementa {@code app_user.token_version} y los tokens anteriores dejan de valer.</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    /** Debe coincidir con el valor por defecto de {@code sportacus.jwt.secret} en application.yml. */
    public static final String DEV_SECRET = "sportacus-dev-only-jwt-secret-cambialo-en-produccion-2026";
    public static final String ISSUER = "sportacus";
    public static final String AUDIENCE = "sportacus-app";
    public static final String TOKEN_VERSION_CLAIM = "tv";

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${sportacus.jwt.secret}") String secret,
            @Value("${sportacus.jwt.expiration-minutes}") long expirationMinutes,
            Environment env) {
        boolean prod = env.acceptsProfiles(Profiles.of("prod"));
        if (secret == null || secret.isBlank() || DEV_SECRET.equals(secret)) {
            if (prod) {
                throw new IllegalStateException("JWT_SECRET no está definida: el perfil prod no arranca sin ella.");
            }
            log.warn("JWT_SECRET no está definida: se usa el secreto de DESARROLLO. No lo uses así en producción.");
            secret = DEV_SECRET;
        }
        byte[] keyBytes = keyBytes(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 256 bits (32 bytes)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMinutes = expirationMinutes;
    }

    /** Acepta un secreto en Base64 (≥ 32 bytes decodificados) o en texto plano. */
    static byte[] keyBytes(String secret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(secret.trim());
            if (decoded.length >= 32) return decoded;
        } catch (IllegalArgumentException ignored) {
            // no es Base64: se usa como texto
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(User user) {
        return generateToken(user, user.tokenVersionOrZero());
    }

    public String generateToken(User user, int tokenVersion) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMinutes * 60_000);
        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /** Lanza JwtException si la firma, el emisor, la audiencia o la fecha no son válidos. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Vigente si es del mismo usuario y su versión coincide con la actual (los tokens sin "tv" ya no valen). */
    public boolean isTokenValid(Claims claims, User user) {
        Object tv = claims.get(TOKEN_VERSION_CLAIM);
        return claims.getSubject() != null && claims.getSubject().equals(user.getUsername())
                && tv instanceof Number n && n.intValue() == user.tokenVersionOrZero()
                && claims.getExpiration() != null && claims.getExpiration().after(new Date());
    }

    public long expirationMinutes() {
        return expirationMinutes;
    }
}
