package net.sadness.playerstats.command;

import java.util.regex.Pattern;
import net.sadness.playerstats.command.CommandSyntaxException.Reason;
import net.sadness.playerstats.stats.StatField;

final class ArgumentParser {

    static final String TOP_KEYWORD = "top";

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.]{1,32}");
    private static final Pattern DIGITS = Pattern.compile("[0-9]{1,13}");
    private static final int MAX_PAGE_DIGITS = 6;
    private static final int FIRST_PAGE = 1;

    private ArgumentParser() {
    }

    static String playerName(String input) throws CommandSyntaxException {
        if (!PLAYER_NAME.matcher(input).matches()) {
            throw new CommandSyntaxException(Reason.INVALID_PLAYER_NAME, input);
        }
        return input;
    }

    static StatField field(String input) throws CommandSyntaxException {
        return StatField.fromKey(input).orElseThrow(() -> new CommandSyntaxException(Reason.UNKNOWN_FIELD, input));
    }

    static long value(String input) throws CommandSyntaxException {
        if (!DIGITS.matcher(input).matches()) {
            throw new CommandSyntaxException(Reason.INVALID_VALUE, input);
        }
        long value = Long.parseLong(input);
        if (value > StatField.MAX_DISPLAY_VALUE) {
            throw new CommandSyntaxException(Reason.INVALID_VALUE, input);
        }
        return value;
    }

    static int page(String[] args, int index) throws CommandSyntaxException {
        if (args.length <= index) {
            return FIRST_PAGE;
        }
        String input = args[index];
        if (input.length() > MAX_PAGE_DIGITS || !DIGITS.matcher(input).matches() || Integer.parseInt(input) < FIRST_PAGE) {
            throw new CommandSyntaxException(Reason.INVALID_PAGE, input);
        }
        return Integer.parseInt(input);
    }

    static boolean isTop(String input) {
        return TOP_KEYWORD.equalsIgnoreCase(input);
    }
}
