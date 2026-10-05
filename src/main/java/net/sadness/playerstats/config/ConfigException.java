package net.sadness.playerstats.config;

import java.util.List;

public final class ConfigException extends Exception {

    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public ConfigException(List<String> problems) {
        super("Invalid configuration: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
