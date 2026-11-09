package com.sportacus.backend.health;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifrado AES-256-GCM a nivel de aplicación para los datos de salud en texto libre (respuestas del
 * chequeo, notas de lesiones). Cumple docs/07 §4 y la Ley 1581 de 2012 (datos sensibles). Las
 * categorías codificadas (zona, severidad) quedan en claro porque el filtro de seguridad las usa en SQL.
 *
 * Formato guardado: base64(iv[12] + texto cifrado + tag[16]).
 * La clave sale de SPORTACUS_DATA_KEY (base64 de 32 bytes). Sin ella se deriva una clave de
 * desarrollo y se avisa en el log: NO usar así en producción.
 */
@Component
public class SensitiveDataCipher {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataCipher.class);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SensitiveDataCipher(@Value("${sportacus.crypto.data-key:}") String base64Key) throws Exception {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("SPORTACUS_DATA_KEY no está definida: se usa una clave de DESARROLLO para cifrar datos de salud.");
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest("sportacus-dev-only-key".getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "AES");
        } else {
            byte[] raw = Base64.getDecoder().decode(base64Key.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("SPORTACUS_DATA_KEY debe ser base64 de exactamente 32 bytes");
            }
            this.key = new SecretKeySpec(raw, "AES");
        }
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el dato de salud", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el dato de salud (¿cambió la clave?)", e);
        }
    }
}
