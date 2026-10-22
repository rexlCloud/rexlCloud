package de.rexlcloud.proxy.common.config;

import de.rexlcloud.core.config.MessageKey;
import de.rexlcloud.core.config.PluginMessageConfig;
import de.rexlcloud.proxy.common.messages.MessageParser;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.HashMap;
import java.util.Optional;

/**
 * The cloud's messages (configured in the master's {@code config/messages.json}) on a proxy.
 */
public class PluginMessages {

    @Getter
    private static final PluginMessages instance = new PluginMessages();

    private final HashMap<MessageKey, String> messages;

    public PluginMessages() {
        this.messages = new HashMap<>();
    }

    /**
     * @return the message, rendered from MiniMessage
     */
    public Component getMessage(MessageKey messageKey) {
        return MessageParser.parse(messages.getOrDefault(messageKey, "§4MESSAGE NOT FOUND"));
    }

    /**
     * @param tagResolver values for the placeholders, e.g. {@code Placeholder.parsed("server", name)}
     * @return the message, rendered from MiniMessage
     */
    public Component getMessage(MessageKey messageKey, TagResolver... tagResolver) {
        return MessageParser.parse(messages.getOrDefault(messageKey, "§4MESSAGE NOT FOUND"), tagResolver);
    }

    /**
     * The MOTD for the server list, depending on maintenance mode, or empty until the cloud sent its messages.
     */
    public Optional<Component> getMotd() {
        var key = CommonSettings.getInstance().isMaintenance() ? MessageKey.MAINTENANCE_MOTD : MessageKey.MOTD;
        return Optional.ofNullable(messages.get(key)).map(MessageParser::parse);
    }

    /**
     * Internal: applies messages sent by the master.
     */
    public void updateMessages(PluginMessageConfig pluginMessageConfig) {
        messages.putAll(pluginMessageConfig.messages());
    }

}
