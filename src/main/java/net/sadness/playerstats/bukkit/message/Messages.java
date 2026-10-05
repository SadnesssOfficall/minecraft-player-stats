package net.sadness.playerstats.bukkit.message;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.sadness.playerstats.config.ConfigException;
import net.sadness.playerstats.stats.StatField;

public final class Messages {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<MessageKey, String> templates = new EnumMap<>(MessageKey.class);
    private final Map<StatField, String> labels = new EnumMap<>(StatField.class);

    public Messages(Function<String, String> lookup) throws ConfigException {
        List<String> problems = new ArrayList<>();
        for (MessageKey key : MessageKey.values()) {
            read(lookup, key.path(), problems, value -> templates.put(key, value));
        }
        for (StatField field : StatField.values()) {
            read(lookup, MessageKey.LABELS_PATH + field.key(), problems, value -> labels.put(field, value));
        }
        if (!problems.isEmpty()) {
            throw new ConfigException(problems);
        }
    }

    public Component render(MessageKey key, TagResolver... resolvers) {
        return miniMessage.deserialize(templates.get(key), resolvers);
    }

    public Component label(StatField field) {
        return miniMessage.deserialize(labels.get(field));
    }

    public void send(Audience audience, MessageKey key, TagResolver... resolvers) {
        audience.sendMessage(render(key, resolvers));
    }

    private static void read(Function<String, String> lookup, String path, List<String> problems,
            Consumer<String> target) {
        String value = lookup.apply(path);
        if (value == null || value.isBlank()) {
            problems.add(path + " must be a non-empty string");
            return;
        }
        target.accept(value);
    }
}
