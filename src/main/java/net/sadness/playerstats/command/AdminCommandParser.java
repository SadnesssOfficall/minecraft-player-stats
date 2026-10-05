package net.sadness.playerstats.command;

import java.util.Locale;
import net.sadness.playerstats.command.CommandSyntaxException.Reason;

public final class AdminCommandParser {

    private static final String RESET = "reset";
    private static final String SET = "set";
    private static final int RESET_ARGS = 2;
    private static final int SET_ARGS = 4;
    private static final int MAX_TOP_ARGS = 3;

    private AdminCommandParser() {
    }

    public static AdminRequest parse(String[] args) throws CommandSyntaxException {
        if (args.length == 0) {
            throw usage(args);
        }
        if (ArgumentParser.isTop(args[0])) {
            if (args.length < 2 || args.length > MAX_TOP_ARGS) {
                throw usage(args);
            }
            return new AdminRequest.Top(ArgumentParser.field(args[1]), ArgumentParser.page(args, 2));
        }
        if (args.length < RESET_ARGS) {
            throw usage(args);
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (RESET.equals(action) && args.length == RESET_ARGS) {
            return new AdminRequest.Reset(ArgumentParser.playerName(args[0]));
        }
        if (SET.equals(action) && args.length == SET_ARGS) {
            String player = ArgumentParser.playerName(args[0]);
            return new AdminRequest.Set(player, ArgumentParser.field(args[2]), ArgumentParser.value(args[3]));
        }
        throw usage(args);
    }

    private static CommandSyntaxException usage(String[] args) {
        return new CommandSyntaxException(Reason.USAGE, String.join(" ", args));
    }
}
