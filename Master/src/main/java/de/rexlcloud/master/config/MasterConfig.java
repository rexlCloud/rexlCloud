package de.rexlcloud.master.config;

import de.rexlcloud.core.server.ProxyForwarding;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.security.SecureRandom;
import java.util.HexFormat;

@Getter
@Accessors(fluent = true)
public class MasterConfig {

    private String host = "0.0.0.0";
    /**
     * Port the wrappers connect to.
     */
    @Setter
    private int port = 1234;
    private boolean debug = false;
    /**
     * Off on new installs; players can join as soon as the network runs.
     */
    @Setter
    private boolean maintenance = false;
    /**
     * MODERN (Velocity modern forwarding, recommended) or LEGACY (BungeeCord forwarding, needed for BungeeCord
     * proxies and servers older than 1.13).
     */
    @Setter
    private ProxyForwarding.Mode forwardingMode = ProxyForwarding.Mode.MODERN;
    /**
     * Secret of modern forwarding, generated on the first start.
     */
    private String forwardingSecret;

    /**
     * Fills in settings that configurations written by older versions lack.
     *
     * @return whether something changed and the configuration should be saved
     */
    public boolean completeDefaults() {
        var changed = false;
        if (forwardingMode == null) {
            forwardingMode = ProxyForwarding.Mode.MODERN;
            changed = true;
        }
        if (forwardingSecret == null || forwardingSecret.isBlank()) {
            var bytes = new byte[24];
            new SecureRandom().nextBytes(bytes);
            forwardingSecret = HexFormat.of().formatHex(bytes);
            changed = true;
        }
        return changed;
    }

    public ProxyForwarding forwarding() {
        return new ProxyForwarding(forwardingMode, forwardingSecret);
    }

}
