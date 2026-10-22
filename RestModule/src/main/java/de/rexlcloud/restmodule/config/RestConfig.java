package de.rexlcloud.restmodule.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
public class RestConfig {

    private String host = "0.0.0.0";
    private int port = 4343;
    /**
     * Minutes of inactivity after which a login token expires.
     */
    private int sessionTimeoutMinutes = 60;
    /**
     * Seconds a console WebSocket ticket stays valid before it is used.
     */
    private int consoleTicketSeconds = 30;
    /**
     * Permissions that existed when the module last ran. Used to grant newly added permissions to admins.
     * {@code null} for configurations written by rexlCloud 1.x.
     */
    @Setter
    private List<String> knownPermissions;
    /**
     * Token for {@code GET /api/v2/metrics} (Prometheus), sent as {@code Authorization: Bearer <token>}. Empty: no
     * metrics.
     */
    private String metricsToken = "";
    /**
     * HTTPS on {@link #port} instead of plain HTTP.
     */
    @Setter
    private Tls tls = new Tls();

    /**
     * @param enabled     whether the API is served over HTTPS
     * @param certificate PEM file with the certificate and its chain, relative to the module folder or absolute,
     *                    e.g. /etc/letsencrypt/live/example.com/fullchain.pem
     * @param privateKey  PEM file with the private key in PKCS#8, e.g. /etc/letsencrypt/live/example.com/privkey.pem
     */
    public record Tls(boolean enabled, String certificate, String privateKey) {
        public Tls() {
            this(false, "tls/fullchain.pem", "tls/privkey.pem");
        }
    }

}
