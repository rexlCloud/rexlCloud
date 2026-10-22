package de.rexlcloud.wrapper.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class WrapperConfig {

    // Not final: final fields with constant values are inlined by the compiler, so wrapper.json would be ignored
    String address = "127.0.0.1";
    int port = 1234;
    @Setter
    UUID authKey = null;
    /**
     * SHA-256 fingerprint of the master's TLS certificate (shown when the master starts). Empty: the first master
     * the wrapper connects to is trusted and its fingerprint saved here.
     */
    @Setter
    String masterFingerprint = "";
    /**
     * Java homes, or folders containing Java homes, to offer besides the ones found in the usual places.
     */
    List<String> javaSearchFolders = new ArrayList<>();
    /**
     * Local port the cloud plugins of this wrapper's servers connect to. Two wrappers on one machine need different
     * ports.
     */
    int serverPort = 2243;
    /**
     * Runs each server in its own tmux session (Linux, needs tmux), so the servers keep running when the wrapper
     * restarts or is updated; it attaches to them again when it starts. "stop" still stops them.
     */
    boolean detachedServers = false;

}
