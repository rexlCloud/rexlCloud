package de.rexlcloud.restmodule.user;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

public class Encryption {

    private static final Random random = new SecureRandom();
    private static final String saltCharacters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String passwordCharacters = "0123456789ABCDEFGHIJKLMNOP+#@.,&$§=QRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int keyLength = 256;

    /**
     * Supported password hash formats. New passwords always use {@link #CURRENT}.
     */
    public enum HashAlgorithm {
        /** Format of rexlCloud 1.x, only used to verify old passwords. */
        PBKDF2_SHA1_10K("PBKDF2WithHmacSHA1", 10_000),
        PBKDF2_SHA256_600K("PBKDF2WithHmacSHA256", 600_000);

        public static final HashAlgorithm CURRENT = PBKDF2_SHA256_600K;

        private final String algorithm;
        private final int iterations;

        HashAlgorithm(String algorithm, int iterations) {
            this.algorithm = algorithm;
            this.iterations = iterations;
        }
    }

    public static String generateSalt() {
        var finalVal = new StringBuilder(30);

        for (int i = 0; i < 30; i++) {
            finalVal.append(saltCharacters.charAt(random.nextInt(saltCharacters.length())));
        }

        return new String(finalVal);
    }

    public static String generatePassword() {
        var finalVal = new StringBuilder(15);

        for (int i = 0; i < 15; i++) {
            finalVal.append(passwordCharacters.charAt(random.nextInt(passwordCharacters.length())));
        }

        return new String(finalVal);
    }

    /**
     * Random URL-safe token with 256 bits of entropy.
     */
    public static String generateToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] hash(char[] password, byte[] salt, HashAlgorithm algorithm) {
        var spec = new PBEKeySpec(password, salt, algorithm.iterations, keyLength);
        Arrays.fill(password, Character.MIN_VALUE);
        try {
            var skf = SecretKeyFactory.getInstance(algorithm.algorithm);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new AssertionError("Error while hashing a password: " + e.getMessage(), e);
        } finally {
            spec.clearPassword();
        }
    }

    public static String hash(String password, String salt, HashAlgorithm algorithm) {
        var securePassword = hash(password.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), algorithm);

        return Base64.getEncoder().encodeToString(securePassword);
    }

    public static boolean check(String providedPassword, String securedPassword, String salt, HashAlgorithm algorithm) {
        var newSecurePassword = hash(providedPassword, salt, algorithm);
        return MessageDigest.isEqual(newSecurePassword.getBytes(StandardCharsets.UTF_8),
                securedPassword.getBytes(StandardCharsets.UTF_8));
    }

}
