package de.rexlcloud.core.api;

import de.rexlcloud.core.api.exceptions.MessageChannelAlreadyRegisteredException;
import de.rexlcloud.core.api.exceptions.MessageChannelNotRegisteredException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

import java.io.Serializable;
import java.util.HashMap;

/**
 * The message channels of this server; get it with {@link RexlCloudApi#getMessageChannelManager()}.
 */
public class MessageChannelManager {

    @Getter(AccessLevel.PROTECTED)
    private final HashMap<String, MessageChannel<?>> messageChannels;

    protected MessageChannelManager() {
        messageChannels = new HashMap<>();
    }

    /**
     * @param type type of the messages; only it, JDK value types and classes of the same plugin are accepted
     */
    public <T extends Serializable> MessageChannel<T> registerMessageChannel(@NonNull String channel, @NonNull Class<T> type) {
        if (messageChannels.containsKey(channel)) {
            throw new MessageChannelAlreadyRegisteredException(channel);
        }
        var messageChannel = new MessageChannel<T>(channel, type);
        messageChannels.put(channel, messageChannel);
        return messageChannel;
    }

    /**
     * @param channel name of a registered channel
     * @param <T>     type of its messages
     * @return the channel
     * @throws de.rexlcloud.core.api.exceptions.MessageChannelNotRegisteredException if it is not registered
     */
    public <T extends Serializable> MessageChannel<T> getMessageChannel(@NonNull String channel) {
        if (!messageChannels.containsKey(channel)) {
            throw new MessageChannelNotRegisteredException(channel);
        }
        return (MessageChannel<T>) messageChannels.get(channel);
    }

    /**
     * Unregisters a channel; it receives no more messages.
     *
     * @param channel name of the channel
     * @throws de.rexlcloud.core.api.exceptions.MessageChannelNotRegisteredException if it is not registered
     */
    public void unregisterMessageChannel(@NonNull String channel) {
        if (!messageChannels.containsKey(channel)) {
            throw new MessageChannelNotRegisteredException(channel);
        }
        messageChannels.remove(channel);
    }

    /**
     * Unregisters a channel; it receives no more messages.
     *
     * @param messageChannel the channel
     * @throws de.rexlcloud.core.api.exceptions.MessageChannelNotRegisteredException if it is not registered
     */
    public void unregisterMessageChannel(@NonNull MessageChannel<?> messageChannel) {
        var channel = messageChannel.getChannel();
        if (!messageChannels.containsKey(channel)) {
            throw new MessageChannelNotRegisteredException(channel);
        }
        messageChannels.remove(channel);
    }

}
