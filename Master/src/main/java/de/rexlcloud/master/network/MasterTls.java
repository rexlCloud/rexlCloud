package de.rexlcloud.master.network;

import de.rexlcloud.core.network.tls.PinnedTrustManager;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.Getter;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * The master's TLS key and self-signed certificate ({@code config/tls/}), created on the first start. Wrappers
 * recognise the master by the certificate's fingerprint.
 */
public final class MasterTls {

    @Getter
    private final SslContext sslContext;
    @Getter
    private final String fingerprint;

    private MasterTls(SslContext sslContext, String fingerprint) {
        this.sslContext = sslContext;
        this.fingerprint = fingerprint;
    }

    /**
     * Loads the key and certificate from the folder, creating them if they do not exist yet.
     */
    public static MasterTls load(Path folder) throws IOException, GeneralSecurityException {
        var certificateFile = folder.resolve("master.crt");
        var keyFile = folder.resolve("master.key");
        if (!Files.isRegularFile(certificateFile) || !Files.isRegularFile(keyFile)) {
            create(folder, certificateFile, keyFile);
        }
        X509Certificate certificate;
        try (var in = Files.newInputStream(certificateFile)) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        var context = SslContextBuilder.forServer(certificateFile.toFile(), keyFile.toFile())
                .protocols("TLSv1.3")
                .build();
        return new MasterTls(context, PinnedTrustManager.fingerprint(certificate));
    }

    private static void create(Path folder, Path certificateFile, Path keyFile) throws IOException, GeneralSecurityException {
        Files.createDirectories(folder);
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        var keys = generator.generateKeyPair();

        var name = new X500Name("CN=rexlCloud master");
        var now = Instant.now();
        X509Certificate certificate;
        try {
            var holder = new JcaX509v3CertificateBuilder(name, new BigInteger(64, new SecureRandom()),
                    Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(36500, ChronoUnit.DAYS)),
                    name, keys.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate()));
            certificate = new JcaX509CertificateConverter().getCertificate(holder);
        } catch (OperatorCreationException e) {
            throw new GeneralSecurityException("Could not sign the certificate", e);
        }

        Files.writeString(keyFile, pem("PRIVATE KEY", keys.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
        try {
            Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // e.g. Windows
        }
        Files.writeString(certificateFile, pem("CERTIFICATE", certificate.getEncoded()), StandardCharsets.US_ASCII);
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

}
