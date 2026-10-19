package com.example.facultyattendance.authentication;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts face templates with an AES-256-GCM key that lives in the Android Keystore (the key
 * material never enters the app process). The faculty UID is bound in as associated data, so a
 * ciphertext copied onto another person's row fails to decrypt.
 *
 * Blob layout: [version=1][ivLength][iv][ciphertext+tag]
 *
 * Limitation: the key is hardware-backed only when the device supports it; on a rooted device or
 * with physical device compromise, protection is weaker. See docs/SECURITY_PRIVACY.md.
 */
public final class KeystoreTemplateCipher {
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String ALIAS = "faculty_template_key_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final byte VERSION = 1;

    public byte[] encrypt(byte[] plain, String uid) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());     // Keystore generates a fresh random IV
        cipher.updateAAD(uid.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        byte[] ct = cipher.doFinal(plain);
        ByteBuffer out = ByteBuffer.allocate(2 + iv.length + ct.length);
        out.put(VERSION).put((byte) iv.length).put(iv).put(ct);
        return out.array();
    }

    public byte[] decrypt(byte[] blob, String uid) throws GeneralSecurityException {
        if (blob == null || blob.length < 3 || blob[0] != VERSION) throw new GeneralSecurityException("bad blob");
        int ivLen = blob[1] & 0xFF;
        if (ivLen < 12 || blob.length <= 2 + ivLen) throw new GeneralSecurityException("bad blob");
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(TAG_BITS, blob, 2, ivLen));
        cipher.updateAAD(uid.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(blob, 2 + ivLen, blob.length - 2 - ivLen);
    }

    /** Deletes the key: every stored template becomes permanently unreadable. Used by "erase all biometric data". */
    public void deleteKey() throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance(PROVIDER);
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance(PROVIDER);
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e);
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return gen.generateKey();
    }
}
