package de.rexlcloud.proxy.common.messages;

import de.rexlcloud.core.api.CloudText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

public class MessageParser {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().tags(StandardTags.defaults()).build();

    public static Component parse(String string){
        return MINI_MESSAGE.deserialize(CloudText.replaceColorCodes(string));
    }

    public static Component parse(String string, TagResolver... tagResolver){
        return MINI_MESSAGE.deserialize(CloudText.replaceColorCodes(string), tagResolver);
    }

}
