package com.korolev.rps_game_server.domain;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchmakerTest {

    @Test
    void matchesTwoPlayers() {
        Matchmaker mm = new Matchmaker();

        EmbeddedChannel c1 = new EmbeddedChannel();
        EmbeddedChannel c2 = new EmbeddedChannel();

        Player p1 = new Player("a", c1);
        Player p2 = new Player("b", c2);

        assertNull(mm.tryMatch(p1));
        GameSession s = mm.tryMatch(p2);

        assertNotNull(s);
        assertSame(c1, s.p1().channel());
        assertSame(c2, s.p2().channel());
    }

    @Test
    void removeIfWaiting() {
        Matchmaker mm = new Matchmaker();
        EmbeddedChannel c1 = new EmbeddedChannel();
        Player p1 = new Player("a", c1);

        assertNull(mm.tryMatch(p1));
        assertTrue(mm.removeIfWaiting(p1));
        assertFalse(mm.removeIfWaiting(p1));
    }
}