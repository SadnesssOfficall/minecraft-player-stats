package net.sadness.playerstats.bukkit.message;

public enum MessageKey {
    NO_PERMISSION("no-permission"),
    PLAYER_ONLY("player-only"),
    PLAYER_NOT_FOUND("player-not-found"),
    STATS_LOADING("stats-loading"),
    STATS_HEADER("stats-header"),
    STATS_LINE("stats-line"),
    TOP_HEADER("top-header"),
    TOP_LINE("top-line"),
    TOP_EMPTY("top-empty"),
    USAGE_STATS("usage-stats"),
    USAGE_ADMIN("usage-admin"),
    INVALID_PLAYER_NAME("invalid-player-name"),
    INVALID_FIELD("invalid-field"),
    INVALID_VALUE("invalid-value"),
    INVALID_PAGE("invalid-page"),
    ADMIN_RESET("admin-reset"),
    ADMIN_SET("admin-set"),
    STORAGE_BUSY("storage-busy"),
    STORAGE_ERROR("storage-error");

    public static final String MESSAGES_PATH = "messages.";
    public static final String LABELS_PATH = "labels.";

    private final String path;

    MessageKey(String key) {
        this.path = MESSAGES_PATH + key;
    }

    public String path() {
        return path;
    }
}
