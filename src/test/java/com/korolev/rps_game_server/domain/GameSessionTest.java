package com.korolev.rps_game_server.domain;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {

    @Test
    void startSendsOpponentFoundToBoth() {
        EmbeddedChannel c1 = new EmbeddedChannel();
        EmbeddedChannel c2 = new EmbeddedChannel();

        Player p1 = new Player("p1", c1);
        Player p2 = new Player("p2", c2);

        GameSession s = new GameSession(p1, p2);
        s.start();

        flush(c1, c2);

        String out1 = takeAllOutbound(c1);
        String out2 = takeAllOutbound(c2);

        assertTrue(out1.contains("Opponent found: p2"));
        assertTrue(out2.contains("Opponent found: p1"));
    }

    @Test
    void winFlowClosesBoth() {
        EmbeddedChannel c1 = new EmbeddedChannel();
        EmbeddedChannel c2 = new EmbeddedChannel();

        Player p1 = new Player("p1", c1);
        Player p2 = new Player("p2", c2);

        GameSession s = new GameSession(p1, p2);
        s.start();
        flush(c1, c2);

        // p1: ROCK, p2: SCISSORS => p1 WIN
        s.submitMove(p1, Move.ROCK);
        s.submitMove(p2, Move.SCISSORS);

        flush(c1, c2);

        String out1 = takeAllOutbound(c1);
        String out2 = takeAllOutbound(c2);

        assertTrue(out1.contains("You WIN") || out1.contains("You WIN!"));
        assertTrue(out2.contains("You LOSE") || out2.contains("You LOSE!"));

        // channels should be closed after finish
        assertFalse(c1.isActive());
        assertFalse(c2.isActive());
    }

    @Test
    void drawResetsRoundAndAsksAgain() {
        EmbeddedChannel c1 = new EmbeddedChannel();
        EmbeddedChannel c2 = new EmbeddedChannel();

        Player p1 = new Player("p1", c1);
        Player p2 = new Player("p2", c2);

        GameSession s = new GameSession(p1, p2);
        s.start();
        flush(c1, c2);

        s.submitMove(p1, Move.ROCK);
        s.submitMove(p2, Move.ROCK);

        flush(c1, c2);

        String out1 = takeAllOutbound(c1);
        String out2 = takeAllOutbound(c2);

        assertTrue(out1.contains("Draw!"));
        assertTrue(out2.contains("Draw!"));
        assertTrue(out1.contains("Try again"));
        assertTrue(out2.contains("Try again"));

        // second round should work (no "already made a move")
        s.submitMove(p1, Move.PAPER);
        s.submitMove(p2, Move.ROCK);
        flush(c1, c2);

        String out1b = takeAllOutbound(c1);
        assertTrue(out1b.contains("Game over"));
        assertFalse(c1.isActive());
        assertFalse(c2.isActive());
    }

    @Test
    void duplicateMoveIsRejected() {
        EmbeddedChannel c1 = new EmbeddedChannel();
        EmbeddedChannel c2 = new EmbeddedChannel();

        Player p1 = new Player("p1", c1);
        Player p2 = new Player("p2", c2);

        GameSession s = new GameSession(p1, p2);
        s.start();
        flush(c1, c2);

        s.submitMove(p1, Move.ROCK);
        s.submitMove(p1, Move.PAPER); // duplicate

        flush(c1);

        String out1 = takeAllOutbound(c1);
        assertTrue(out1.contains("already made a move"));
        assertTrue(c1.isActive());
        assertTrue(c2.isActive());
    }

    @Test
    void onIdleTimesOutOnlyIfPlayerHasNotMoved() {
        EmbeddedChannel c1 = new EmbeddedChannel();
        EmbeddedChannel c2 = new EmbeddedChannel();

        Player p1 = new Player("p1", c1);
        Player p2 = new Player("p2", c2);

        GameSession s = new GameSession(p1, p2);
        s.start();
        flush(c1, c2);

        // p1 makes a move, p2 does not
        s.submitMove(p1, Move.ROCK);
        flush(c1, c2);
        takeAllOutbound(c1);
        takeAllOutbound(c2);

        // idle p1 should NOT finish (he already moved)
        s.onIdle(p1);
        flush(c1, c2);
        assertTrue(c1.isActive());
        assertTrue(c2.isActive());

        // idle p2 should finish (he has not moved)
        s.onIdle(p2);
        flush(c1, c2);

        String out2 = takeAllOutbound(c2);
        String out1 = takeAllOutbound(c1);

        assertTrue(out2.contains("Timeout"));
        assertTrue(out1.contains("Opponent timeout") || out1.contains("WIN"));

        assertFalse(c1.isActive());
        assertFalse(c2.isActive());
    }

    @Test
    void disconnectFinishesSession() {
        EmbeddedChannel c1 = new EmbeddedChannel();
        EmbeddedChannel c2 = new EmbeddedChannel();

        Player p1 = new Player("p1", c1);
        Player p2 = new Player("p2", c2);

        GameSession s = new GameSession(p1, p2);
        s.start();
        flush(c1, c2);
        takeAllOutbound(c1);
        takeAllOutbound(c2);

        s.onDisconnect(p1);
        flush(c1, c2);

        String out2 = takeAllOutbound(c2);
        assertTrue(out2.contains("Opponent disconnected"));

        assertFalse(c1.isActive());
        assertFalse(c2.isActive());
    }

    // -------- helpers --------

    private static void flush(EmbeddedChannel... chs) {
        for (EmbeddedChannel ch : chs) {
            ch.runPendingTasks();
            ch.runScheduledPendingTasks();
        }
    }

    private static String takeAllOutbound(EmbeddedChannel ch) {
        StringBuilder sb = new StringBuilder();
        for (;;) {
            Object o = ch.readOutbound();
            if (o == null) break;
            sb.append(o);
        }
        return sb.toString();
    }
}