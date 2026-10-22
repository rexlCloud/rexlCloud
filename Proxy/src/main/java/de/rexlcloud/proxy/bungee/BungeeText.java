package de.rexlcloud.proxy.bungee;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

/**
 * Adventure text for BungeeCord. Its own chat API reads the component as JSON; Adventure's BungeeCord platform is not
 * used because it does not work with Adventure 5, which the cloud uses.
 */
public final class BungeeText {

    /**
     * The format every BungeeCord version reads (e.g. clickEvent instead of click_event); BungeeCord converts it for
     * each player's Minecraft version.
     */
    private static final GsonComponentSerializer JSON = GsonComponentSerializer.builder()
            .options(JSONOptions.compatibility()).build();

    private BungeeText() {
    }

    /**
     * @return the component as BungeeCord chat components
     */
    public static BaseComponent[] of(Component component) {
        return ComponentSerializer.parse(JSON.serialize(component));
    }

    /**
     * Sends a chat message to a player or the console.
     */
    public static void send(CommandSender receiver, Component message) {
        receiver.sendMessage(of(message));
    }

}
