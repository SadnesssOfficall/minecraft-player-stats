package net.sadness.playerstats.command;

import net.sadness.playerstats.command.CommandSyntaxException.Reason;

public final class StatsCommandParser {

    private static final int MAX_TOP_ARGS = 3;

    private StatsCommandParser() {
    }

    public static StatsRequest parse(String[] args) throws CommandSyntaxException {
        if (args.length == 0) {
            return new StatsRequest.Self();
        }
        if (ArgumentParser.isTop(args[0])) {
            if (args.length < 2 || args.length > MAX_TOP_ARGS) {
                throw new CommandSyntaxException(Reason.USAGE, String.join(" ", args));
            }
            return new StatsRequest.Top(ArgumentParser.field(args[1]), ArgumentParser.page(args, 2));
        }
        if (args.length > 1) {
            throw new CommandSyntaxException(Reason.USAGE, String.join(" ", args));
        }
        return new StatsRequest.Other(ArgumentParser.playerName(args[0]));
    }
}
