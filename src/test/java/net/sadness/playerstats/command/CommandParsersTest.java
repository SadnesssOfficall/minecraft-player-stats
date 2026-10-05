package net.sadness.playerstats.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.sadness.playerstats.command.CommandSyntaxException.Reason;
import net.sadness.playerstats.stats.StatField;
import org.junit.jupiter.api.Test;

class CommandParsersTest {

    private static Reason statsReason(String... args) {
        return assertThrows(CommandSyntaxException.class, () -> StatsCommandParser.parse(args)).reason();
    }

    private static Reason adminReason(String... args) {
        return assertThrows(CommandSyntaxException.class, () -> AdminCommandParser.parse(args)).reason();
    }

    @Test
    void statsWithoutArgumentsIsSelf() throws Exception {
        assertInstanceOf(StatsRequest.Self.class, StatsCommandParser.parse(new String[0]));
    }

    @Test
    void statsWithPlayerName() throws Exception {
        assertEquals(new StatsRequest.Other("Steve_1"), StatsCommandParser.parse(new String[] {"Steve_1"}));
    }

    @Test
    void statsTopDefaultsToFirstPage() throws Exception {
        assertEquals(new StatsRequest.Top(StatField.DEATHS, 1), StatsCommandParser.parse(new String[] {"TOP", "deaths"}));
        assertEquals(new StatsRequest.Top(StatField.MOB_KILLS, 3), StatsCommandParser.parse(new String[] {"top", "mob-kills", "3"}));
    }

    @Test
    void statsRejectsBadInput() {
        assertEquals(Reason.INVALID_PLAYER_NAME, statsReason("bad name!"));
        assertEquals(Reason.INVALID_PLAYER_NAME, statsReason("a".repeat(33)));
        assertEquals(Reason.USAGE, statsReason("Steve", "extra"));
        assertEquals(Reason.USAGE, statsReason("top"));
        assertEquals(Reason.USAGE, statsReason("top", "deaths", "1", "2"));
        assertEquals(Reason.UNKNOWN_FIELD, statsReason("top", "bogus"));
        assertEquals(Reason.INVALID_PAGE, statsReason("top", "deaths", "0"));
        assertEquals(Reason.INVALID_PAGE, statsReason("top", "deaths", "-1"));
        assertEquals(Reason.INVALID_PAGE, statsReason("top", "deaths", "abc"));
        assertEquals(Reason.INVALID_PAGE, statsReason("top", "deaths", "99999999999"));
    }

    @Test
    void adminReset() throws Exception {
        assertEquals(new AdminRequest.Reset("Alex"), AdminCommandParser.parse(new String[] {"Alex", "RESET"}));
    }

    @Test
    void adminSet() throws Exception {
        assertEquals(new AdminRequest.Set("Alex", StatField.DEATHS, 12), AdminCommandParser.parse(new String[] {"Alex", "set", "deaths", "12"}));
    }

    @Test
    void adminTop() throws Exception {
        assertEquals(new AdminRequest.Top(StatField.PLAYTIME, 2), AdminCommandParser.parse(new String[] {"top", "playtime", "2"}));
    }

    @Test
    void adminRejectsBadInput() {
        assertEquals(Reason.USAGE, adminReason());
        assertEquals(Reason.USAGE, adminReason("Alex"));
        assertEquals(Reason.USAGE, adminReason("Alex", "explode"));
        assertEquals(Reason.USAGE, adminReason("Alex", "reset", "now"));
        assertEquals(Reason.USAGE, adminReason("Alex", "set", "deaths"));
        assertEquals(Reason.USAGE, adminReason("top"));
        assertEquals(Reason.UNKNOWN_FIELD, adminReason("Alex", "set", "nope", "1"));
        assertEquals(Reason.INVALID_VALUE, adminReason("Alex", "set", "deaths", "-5"));
        assertEquals(Reason.INVALID_VALUE, adminReason("Alex", "set", "deaths", "1.5"));
        assertEquals(Reason.INVALID_VALUE, adminReason("Alex", "set", "deaths", "99999999999999999999"));
        assertEquals(Reason.INVALID_VALUE, adminReason("Alex", "set", "deaths", "1000000000001"));
        assertEquals(Reason.INVALID_PLAYER_NAME, adminReason("../etc", "reset"));
    }
}
