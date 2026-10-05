package net.sadness.playerstats.bukkit.command;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sadness.playerstats.bukkit.message.MessageKey;
import net.sadness.playerstats.bukkit.message.Messages;
import net.sadness.playerstats.service.StatsNotLoadedException;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

final class Replies {

    private final Plugin plugin;
    private final Messages messages;
    private final Logger logger;

    Replies(Plugin plugin, Messages messages, Logger logger) {
        this.plugin = plugin;
        this.messages = messages;
        this.logger = logger;
    }

    <T> void deliver(CommandSender sender, CompletableFuture<T> future, Consumer<T> onSuccess) {
        future.whenComplete((value, failure) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (failure == null) {
                    onSuccess.accept(value);
                } else {
                    messages.send(sender, keyFor(failure));
                }
            });
        });
    }

    private MessageKey keyFor(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof RejectedExecutionException) {
            return MessageKey.STORAGE_BUSY;
        }
        if (cause instanceof StatsNotLoadedException) {
            return MessageKey.STATS_LOADING;
        }
        logger.log(Level.WARNING, "Statistics command failed", cause);
        return MessageKey.STORAGE_ERROR;
    }
}
