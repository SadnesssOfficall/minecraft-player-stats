package net.sadness.playerstats.bukkit.command;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sadness.playerstats.bukkit.message.MessageKey;
import net.sadness.playerstats.bukkit.message.Messages;
import net.sadness.playerstats.command.CommandSyntaxException;
import net.sadness.playerstats.stats.StatField;
import org.bukkit.command.CommandSender;

final class SyntaxErrorReporter {

    private final Messages messages;

    SyntaxErrorReporter(Messages messages) {
        this.messages = messages;
    }

    void report(CommandSender sender, CommandSyntaxException error, MessageKey usage) {
        switch (error.reason()) {
            case USAGE -> messages.send(sender, usage);
            case INVALID_PLAYER_NAME -> messages.send(sender, MessageKey.INVALID_PLAYER_NAME, Placeholder.unparsed("input", error.input()));
            case UNKNOWN_FIELD -> messages.send(sender, MessageKey.INVALID_FIELD,
                    Placeholder.unparsed("input", error.input()), Placeholder.unparsed("fields", StatField.allKeys()));
            case INVALID_VALUE -> messages.send(sender, MessageKey.INVALID_VALUE, Placeholder.unparsed("input", error.input()));
            case INVALID_PAGE -> messages.send(sender, MessageKey.INVALID_PAGE, Placeholder.unparsed("input", error.input()));
        }
    }
}
