package net.sadness.playerstats.command;

public final class CommandSyntaxException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        USAGE,
        INVALID_PLAYER_NAME,
        UNKNOWN_FIELD,
        INVALID_VALUE,
        INVALID_PAGE
    }

    private final Reason reason;
    private final String input;

    public CommandSyntaxException(Reason reason, String input) {
        super(reason + ": " + input);
        this.reason = reason;
        this.input = input;
    }

    public Reason reason() {
        return reason;
    }

    public String input() {
        return input;
    }
}
