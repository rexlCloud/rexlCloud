package de.rexlcloud.core.network;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

/**
 * Encrypts template files sent from the master to a wrapper with the wrapper's key.
 * Uses AES-256-GCM; the random IV is prepended to the ciphertext.
 */
public class AesUtil {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static byte[] encrypt(byte[] content, UUID key) {
        try {
            var iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(key), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            var encrypted = cipher.doFinal(content);
            var result = Arrays.copyOf(iv, IV_LENGTH + encrypted.length);
            System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);
            return result;
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @return the decrypted content, or {@code null} if it was not encrypted with this key or was modified
     */
    public static byte[] decrypt(byte[] content, UUID key) {
        if (content == null || content.length < IV_LENGTH) {
            return null;
        }
        try {
            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(key),
                    new GCMParameterSpec(TAG_LENGTH_BITS, content, 0, IV_LENGTH));
            return cipher.doFinal(content, IV_LENGTH, content.length - IV_LENGTH);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    /**
     * AES needs a 128, 192 or 256 bit key, so the wrapper key is hashed to 256 bits.
     */
    private static SecretKeySpec secretKey(UUID key) throws GeneralSecurityException {
        var digest = MessageDigest.getInstance("SHA-256").digest(key.toString().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
