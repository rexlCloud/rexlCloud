package de.rexlcloud.core.network.tls;

import javax.net.ssl.X509TrustManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Trusts exactly one certificate, identified by its SHA-256 fingerprint, like SSH host keys. The master's
 * certificate is self-signed, so there is no certificate authority to ask.
 * <p>
 * Without a pinned fingerprint the first certificate is trusted and reported through {@code onFirstUse}, so it can
 * be saved; from then on only that certificate is accepted.
 */
public class PinnedTrustManager implements X509TrustManager {

    private final Supplier<String> pinned;
    private final Consumer<String> onFirstUse;

    /**
     * @param pinned     the fingerprint to accept, or blank to trust the first certificate
     * @param onFirstUse receives the fingerprint of the first certificate when none was pinned
     */
    public PinnedTrustManager(Supplier<String> pinned, Consumer<String> onFirstUse) {
        this.pinned = pinned;
        this.onFirstUse = onFirstUse;
    }

    /**
     * SHA-256 of the certificate as upper-case hex pairs separated by colons.
     */
    public static String fingerprint(X509Certificate certificate) throws CertificateEncodingException {
        try {
            var hash = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Ignores case, colons and spaces, so fingerprints can be copied in any common format.
     */
    public static String normalize(String fingerprint) {
        return fingerprint == null ? "" : fingerprint.replaceAll("[:\\s]", "").toUpperCase(Locale.ROOT);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException("The master sent no certificate");
        }
        var fingerprint = fingerprint(chain[0]);
        var expected = normalize(pinned.get());
        if (expected.isEmpty()) {
            onFirstUse.accept(fingerprint);
            return;
        }
        if (!expected.equals(normalize(fingerprint))) {
            throw new CertificateException("The master's certificate " + fingerprint + " is not the trusted one ("
                    + pinned.get() + "). If the master got a new certificate, update masterFingerprint in the "
                    + "wrapper's config/wrapper.json; otherwise someone may be intercepting the connection.");
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("Only used to check the master");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

}
