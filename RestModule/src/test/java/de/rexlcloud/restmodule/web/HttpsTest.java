package de.rexlcloud.restmodule.web;

import com.google.gson.Gson;
import de.rexlcloud.restmodule.TestCloud;
import de.rexlcloud.restmodule.auth.SessionManager;
import de.rexlcloud.restmodule.config.RestConfig;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class HttpsTest {

    @TempDir
    Path folder;

    @Test
    void servesTheApiOverHttpsAndPicksUpARenewedCertificate() throws Exception {
        var first = writeCertificate("CN=first");
        var port = TestCloud.freePort();
        var config = new Gson().fromJson("{\"host\":\"127.0.0.1\",\"port\":" + port
                + ",\"tls\":{\"enabled\":true,\"certificate\":\"tls/fullchain.pem\",\"privateKey\":\"tls/privkey.pem\"}}",
                RestConfig.class);
        var webServer = new WebServer(config, folder, null,
                new SessionManager(null, Duration.ofMinutes(1), Duration.ofSeconds(30)));
        webServer.start();
        try {
            var response = get("https://127.0.0.1:" + port + "/api/v2/servers");
            assertEquals(401, response.statusCode(), "the API answers over HTTPS");
            assertEquals(first, response.sslSession().orElseThrow().getPeerCertificates()[0]);

            assertThrows(Exception.class, () -> get("http://127.0.0.1:" + port + "/api/v2/servers"),
                    "no plain HTTP next to HTTPS");

            // certbot renews the files
            var renewed = writeCertificate("CN=renewed");
            var later = FileTime.from(Instant.now().plusSeconds(5));
            Files.setLastModifiedTime(folder.resolve("tls/fullchain.pem"), later);
            Files.setLastModifiedTime(folder.resolve("tls/privkey.pem"), later);
            webServer.reloadCertificate();

            var afterRenewal = get("https://127.0.0.1:" + port + "/api/v2/servers");
            assertEquals(renewed, afterRenewal.sslSession().orElseThrow().getPeerCertificates()[0]);
        } finally {
            webServer.stop();
        }
    }

    @Test
    void doesNotStartWithoutTheCertificate() {
        var config = new Gson().fromJson("{\"host\":\"127.0.0.1\",\"port\":1,\"tls\":{\"enabled\":true,"
                + "\"certificate\":\"missing.pem\",\"privateKey\":\"missing-key.pem\"}}", RestConfig.class);
        var webServer = new WebServer(config, folder, null,
                new SessionManager(null, Duration.ofMinutes(1), Duration.ofSeconds(30)));
        var e = assertThrows(IllegalStateException.class, webServer::start);
        assertTrue(e.getMessage().contains("certificate could not be loaded"), e.getMessage());
    }

    @Test
    void explainsHowToConvertOldKeyFormats() throws Exception {
        writeCertificate("CN=old");
        var key = folder.resolve("tls/privkey.pem");
        Files.writeString(key, Files.readString(key).replace("PRIVATE KEY", "EC PRIVATE KEY"));
        var e = assertThrows(Exception.class, () -> Https.loadKeyStore(folder.resolve("tls/fullchain.pem"), key));
        assertTrue(e.getMessage().contains("openssl pkcs8"), e.getMessage());
    }

    private X509Certificate writeCertificate(String subject) throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        var keys = generator.generateKeyPair();
        var name = new X500Name(subject);
        var now = Instant.now();
        var holder = new JcaX509v3CertificateBuilder(name, new BigInteger(64, new SecureRandom()),
                Date.from(now.minusSeconds(3600)), Date.from(now.plusSeconds(86400)), name, keys.getPublic())
                // The HTTP client checks the address against the certificate
                .addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(new GeneralName(GeneralName.iPAddress, "127.0.0.1")))
                .build(new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate()));
        var certificate = new JcaX509CertificateConverter().getCertificate(holder);
        var encoder = Base64.getMimeEncoder(64, "\n".getBytes());
        Files.createDirectories(folder.resolve("tls"));
        Files.writeString(folder.resolve("tls/fullchain.pem"), "-----BEGIN CERTIFICATE-----\n"
                + encoder.encodeToString(certificate.getEncoded()) + "\n-----END CERTIFICATE-----\n");
        Files.writeString(folder.resolve("tls/privkey.pem"), "-----BEGIN PRIVATE KEY-----\n"
                + encoder.encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        return certificate;
    }

    private static HttpResponse<String> get(String url) throws Exception {
        // A new client per request, so the renewed certificate is seen on a new connection
        var client = HttpClient.newBuilder().sslContext(trustAll()).connectTimeout(Duration.ofSeconds(5)).build();
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static SSLContext trustAll() throws Exception {
        var context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, null);
        return context;
    }

}
