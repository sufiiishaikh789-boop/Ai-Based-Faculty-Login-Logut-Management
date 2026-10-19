package com.example.facultyattendance.authentication;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2-HMAC-SHA256 password hashing with a random salt. Stored form: "iterations:salt:hash"
 * (Base64). The password itself is never stored.
 */
public final class PasswordHasher {
    public static final int DEFAULT_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private PasswordHasher() { }

    public static String hash(char[] password) { return hash(password, DEFAULT_ITERATIONS); }

    public static String hash(char[] password, int iterations) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] h = derive(password, salt, iterations);
        return iterations + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(h);
    }

    public static boolean verify(char[] password, String stored) {
        if (stored == null) return false;
        String[] parts = stored.split(":");
        if (parts.length != 3) return false;
        try {
            int iterations = Integer.parseInt(parts[0]);
            if (iterations < 1000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);   // constant-time comparison
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Minimum policy: 10+ characters and at least 4 different characters. */
    public static boolean isAcceptable(char[] password) {
        if (password == null || password.length < 10) return false;
        java.util.Set<Character> distinct = new java.util.HashSet<Character>();
        for (char c : password) distinct.add(c);
        return distinct.size() >= 4;
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        } finally {
            spec.clearPassword();   // the salt is not secret, so it is intentionally not wiped
        }
    }
}
