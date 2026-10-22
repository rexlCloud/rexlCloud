package de.rexlcloud.core.server.files;

import de.rexlcloud.core.server.ServerType;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

@Getter
public class ServerJar implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Where the jar comes from.
     */
    public enum Source {
        /**
         * Downloaded from {@link #getDownloadUrl()}.
         */
        DOWNLOAD,
        /**
         * Built with SpigotMC's BuildTools for {@link #getMinecraftVersion()}; needs git. The download URL points to
         * the version's BuildTools descriptor.
         */
        SPIGOT_BUILD_TOOLS,
        /**
         * Nothing is installed: the user puts their own server.jar (e.g. a Minestom server) into the template.
         */
        CUSTOM
    }

    /**
     * Id of {@link #custom()}.
     */
    public static final String CUSTOM_ID = "CUSTOM";

    /**
     * The choice for groups that run their own server.jar instead of a downloaded one.
     */
    public static ServerJar custom() {
        var jar = new ServerJar();
        jar.name = CUSTOM_ID;
        jar.serverType = ServerType.SERVER;
        jar.source = Source.CUSTOM;
        return jar;
    }

    private ServerJar() {
    }

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9]");
    private static final Pattern MC_VERSION = Pattern.compile("[0-9.]");
    private static final Pattern URL = Pattern.compile("https?://[-a-zA-Z0-9@:%._/+~#=]{1,256}.[a-zA-Z0-9()]{1,6}");

    protected String name;
    @Nullable
    protected String minecraftVersion;
    @Nullable
    protected String version;
    protected ServerType serverType;
    protected String downloadUrl;
    protected Source source = Source.DOWNLOAD;


    public ServerJar(String name, String minecraftVersion, ServerType serverType, String downloadUrl) throws RuntimeException {
        this(name, minecraftVersion, null, serverType, downloadUrl);
    }

    public ServerJar(String name, @Nullable String minecraftVersion, @Nullable String version, ServerType serverType, String downloadUrl) throws RuntimeException {
        this(name, minecraftVersion, version, serverType, downloadUrl, Source.DOWNLOAD);
    }

    public ServerJar(String name, @Nullable String minecraftVersion, @Nullable String version, ServerType serverType,
                     String downloadUrl, Source source) throws RuntimeException {
        this.name = check(name);
        this.minecraftVersion = minecraftVersion == null ? null : checkMcVersion(minecraftVersion);
        this.version = version == null ? null : check(version);
        this.serverType = serverType;
        this.downloadUrl = checkUrl(downloadUrl);
        this.source = Objects.requireNonNull(source);
        if (source == Source.SPIGOT_BUILD_TOOLS && minecraftVersion == null) {
            throw new RuntimeException("BuildTools needs a Minecraft version");
        }
    }

    /**
     * {@link Source#DOWNLOAD} for objects without a source, e.g. deserialized from older versions.
     */
    public Source getSource() {
        return source == null ? Source.DOWNLOAD : source;
    }

    private String checkMcVersion(String text) throws RuntimeException {
        if (!MC_VERSION.matcher(text).find()) {
            throw new RuntimeException("ServerJar Minecraft version does not match '[0-9.]'");
        }
        return text.trim();
    }

    private String check(String text) throws RuntimeException {
        if (!ALLOWED.matcher(text.trim()).find()) {
            throw new RuntimeException("ServerJar parameters does not match '[A-Za-z0-9.]'");
        }
        return text.trim();
    }

    private String checkUrl(String url) throws RuntimeException {
        if (!URL.matcher(url).find()) {
            throw new RuntimeException("ServerJar url does not match to url");
        }
        return url.trim();
    }

    private boolean match(String string) {
        return string == null || ALLOWED.matcher(name).find();
    }

    private boolean matchMcVersion(String string) {
        return string == null || MC_VERSION.matcher(string).find();
    }

    private boolean matchUrl(String string) {
        return URL.matcher(string).find();
    }

    protected boolean checkAll() {
        return match(name) && matchMcVersion(minecraftVersion) && match(version) && matchUrl(downloadUrl);
    }

    public String getFullText() {
        if (version == null && minecraftVersion == null) {
            return name.replace(".", "_").toUpperCase();
        }
        if (version == null) {
            return (name.replace(".", "_") + "_" + minecraftVersion.replace(".", "_")).toUpperCase();
        }
        if (minecraftVersion == null) {
            return (name.replace(".", "_") + "_" + version.replace(".", "_")).toUpperCase();
        }
        return (name.replace(".", "_") + "_" + minecraftVersion.replace(".", "_") + "_" + version.replace(".", "_")).toUpperCase();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ServerJar serverJar = (ServerJar) o;
        return Objects.equals(name, serverJar.name) && Objects.equals(minecraftVersion, serverJar.minecraftVersion) && Objects.equals(version, serverJar.version) && serverType == serverJar.serverType && Objects.equals(downloadUrl, serverJar.downloadUrl) && getSource() == serverJar.getSource();
    }

    @Override
    public String toString() {
        return "ServerJar{" +
                "name='" + name + '\'' +
                ", minecraftVersion='" + minecraftVersion + '\'' +
                ", version='" + version + '\'' +
                ", serverType=" + serverType +
                ", downloadUrl='" + downloadUrl + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, minecraftVersion, version, serverType, downloadUrl, getSource());
    }
}
