package de.rexlcloud.core.api;

import de.rexlcloud.core.network.packets.CustomMessageChannelPacket;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.io.*;
import java.util.ArrayList;
import java.util.function.BiConsumer;

/**
 * A named channel for sending objects between servers of the network; register it on every server that uses it
 * with {@link MessageChannelManager#registerMessageChannel(String, Class)}.
 * <pre>{@code
 * var channel = RexlCloudApi.getMessageChannelManager().registerMessageChannel("myplugin:scores", Score.class);
 * channel.addListener((from, score) -> getLogger().info(from.getName() + " sent " + score));
 * channel.sendMessage(new Score(42), otherServer);
 * }</pre>
 * Objects are sent with Java serialization. For safety only the channel's type, JDK value types and classes of the
 * plugin that registered it are read; other messages are dropped.
 *
 * @param <T> type of the messages
 */
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageChannel<T extends Serializable> {

    @Getter(AccessLevel.PROTECTED)
    private final String channel;
    /**
     * Type of the messages; only it, JDK value types and classes of the same plugin are read.
     */
    private final Class<T> type;
    private final ArrayList<BiConsumer<Server, T>> consumers = new ArrayList<>();

    /**
     * Internal: passes a received message to the listeners.
     */
    protected void handelPacket(PacketContainer packetContainer) {
        if (packetContainer.getPacketType() == PacketType.CUSTOM_MESSAGE_CHANNEL) {
            var packet = (CustomMessageChannelPacket) packetContainer.getPacket();
            if (packet.getMessageChannel().equals(channel)) {
                Object received;
                try {
                    received = SafeObjectInput.read(packet.getObjectBytes(), type.getClassLoader());
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("[rexlCloud] Dropped a message on channel " + channel + " from "
                            + packet.getSourceServerName() + ": " + e.getMessage());
                    return;
                }
                if (!type.isInstance(received)) {
                    System.err.println("[rexlCloud] Dropped a message on channel " + channel + " from "
                            + packet.getSourceServerName() + ": expected " + type.getName());
                    return;
                }
                var message = type.cast(received);
                RexlCloudApi.getInstance().getCachedServer(packet.getSourceServerName()).ifPresent(server ->
                        consumers.forEach(consumer -> consumer.accept(server, message)));
            }
        }
    }

    /**
     * Sends an object to servers; their listeners on this channel receive it.
     *
     * @param object  the message
     * @param servers the receiving servers
     */
    public void sendMessage(@NonNull T object, @NonNull Server... servers) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos)) {
            objectOutputStream.writeObject(object);
            objectOutputStream.flush();
            var bytes = bos.toByteArray();
            for (Server server : servers) {
                var packet = new CustomMessageChannelPacket(server.getName(), RexlCloudApi.getInstance().getServerName(), channel, bytes);
                var packetContainer = new PacketContainer(PacketType.CUSTOM_MESSAGE_CHANNEL, packet);
                RexlCloudApi.getInstance().getChannel().writeAndFlush(packetContainer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a listener for messages on this channel.
     *
     * @param consumer gets the sending server and the message
     */
    public void addListener(BiConsumer<Server, T> consumer) {
        consumers.add(consumer);
    }

    /**
     * Removes a listener added with {@link #addListener(BiConsumer)}.
     */
    public void removeListeners(BiConsumer<Server, T> consumer) {
        consumers.remove(consumer);
    }

    /**
     * Removes all listeners.
     */
    public void clearListeners() {
        consumers.clear();
    }

}
