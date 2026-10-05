package net.sadness.playerstats.command;

import net.sadness.playerstats.stats.StatField;

public sealed interface AdminRequest {

    record Reset(String playerName) implements AdminRequest {
    }

    record Set(String playerName, StatField field, long displayValue) implements AdminRequest {
    }

    record Top(StatField field, int page) implements AdminRequest {
    }
}
