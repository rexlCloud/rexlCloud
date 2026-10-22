package de.rexlcloud.restmodule.web;

import de.rexlcloud.core.console.Console;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Serves the API over HTTPS with a certificate and private key in PEM files, e.g. {@code fullchain.pem} and
 * {@code privkey.pem} from Let's Encrypt. The files are checked every minute and loaded again when they change, so
 * a renewed certificate is used without a restart.
 */
final class Https {

    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN PRIVATE KEY-----([A-Za-z0-9+/=\\s]+)-----END PRIVATE KEY-----");
    // Only protects the key store in memory
    private static final String PASSWORD = "rexlcloud";

    private final Path certificate;
    private final Path privateKey;
    private final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    private ScheduledExecutorService reloader;
    private long loadedVersion;

    Https(Path certificate, Path privateKey) throws IOException, GeneralSecurityException {
        this.certificate = certificate;
        this.privateKey = privateKey;
        sslContextFactory.setKeyStore(loadKeyStore(certificate, privateKey));
        sslContextFactory.setKeyStorePassword(PASSWORD);
        loadedVersion = version();
    }

    /**
     * The HTTPS connector Javalin listens on instead of plain HTTP.
     */
    Connector connector(Server server, HttpConfiguration httpConfiguration, String host, int port) {
        var httpsConfiguration = new HttpConfiguration(httpConfiguration);
        // The panel may reach the API by IP or another name than the certificate's
        httpsConfiguration.addCustomizer(new SecureRequestCustomizer(false));
        var connector = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(httpsConfiguration));
        connector.setHost(host);
        connector.setPort(port);
        return connector;
    }

    void watch() {
        reloader = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("rest-tls-reload").daemon().factory());
        reloader.scheduleWithFixedDelay(this::reloadIfChanged, 1, 1, TimeUnit.MINUTES);
    }

    void reloadIfChanged() {
        try {
            var version = version();
            if (version == loadedVersion) {
                return;
            }
            var keyStore = loadKeyStore(certificate, privateKey);
            sslContextFactory.reload(factory -> factory.setKeyStore(keyStore));
            loadedVersion = version;
            Console.info("[RestModule] Loaded the renewed HTTPS certificate");
        } catch (Exception e) {
            // Half-written files during a renewal: tried again next minute, the old certificate stays in use
            Console.warn("[RestModule] The HTTPS certificate could not be reloaded: %s", e.getMessage());
        }
    }

    void stop() {
        if (reloader != null) {
            reloader.shutdownNow();
        }
    }

    private long version() throws IOException {
        return Files.getLastModifiedTime(certificate).toMillis() * 31 + Files.getLastModifiedTime(privateKey).toMillis();
    }

    /**
     * A key store with the private key and its certificate chain from PEM files.
     */
    static KeyStore loadKeyStore(Path certificate, Path privateKey) throws IOException, GeneralSecurityException {
        Certificate[] chain;
        try (InputStream in = Files.newInputStream(certificate)) {
            chain = CertificateFactory.getInstance("X.509").generateCertificates(in).toArray(Certificate[]::new);
        }
        if (chain.length == 0) {
            throw new GeneralSecurityException("No certificate in " + certificate);
        }
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("rest", readPrivateKey(privateKey, chain[0].getPublicKey().getAlgorithm()), PASSWORD.toCharArray(), chain);
        return keyStore;
    }

    private static PrivateKey readPrivateKey(Path file, String algorithm) throws IOException, GeneralSecurityException {
        var pem = Files.readString(file);
        var matcher = PRIVATE_KEY.matcher(pem);
        if (!matcher.find()) {
            throw new GeneralSecurityException(file + " has no PKCS#8 key (-----BEGIN PRIVATE KEY-----). Convert it with: "
                    + "openssl pkcs8 -topk8 -nocrypt -in " + file.getFileName() + " -out key.pem");
        }
        var der = Base64.getMimeDecoder().decode(matcher.group(1));
        return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(der));
    }

}
