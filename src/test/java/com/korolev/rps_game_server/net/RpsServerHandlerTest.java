package com.korolev.rps_game_server.net;

import com.korolev.rps_game_server.domain.GameSession;
import com.korolev.rps_game_server.domain.Matchmaker;
import com.korolev.rps_game_server.domain.Player;
import com.korolev.rps_game_server.domain.PlayerContext;
import com.korolev.rps_game_server.domain.PlayerState;
import com.korolev.rps_game_server.protocol.Messages;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static com.korolev.rps_game_server.net.RpsServerHandler.PLAYER_CTX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RpsServerHandlerTest {

    @Test
    void welcomeOnConnect() {
        Matchmaker mm = mock(Matchmaker.class);

        EmbeddedChannel ch = newChannel(mm);

        String out = takeAllOutbound(ch);
        assertTrue(out.contains("Enter your nickname"));
        assertNotNull(ch.attr(PLAYER_CTX).get());
        assertTrue(ch.isActive());
    }

    @Test
    void helpCommandPrintsHelp() {
        Matchmaker mm = mock(Matchmaker.class);
        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        ch.writeInbound("/help");
        flush(ch);

        String out = takeAllOutbound(ch);
        assertTrue(out.contains(Messages.HELP.trim()));
        assertTrue(ch.isActive());
    }

    @Test
    void quitClosesChannel() {
        Matchmaker mm = mock(Matchmaker.class);
        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        ch.writeInbound("/quit");
        flush(ch);

        String out = takeAllOutbound(ch);
        assertTrue(out.contains(Messages.BYE.trim()));
        flush(ch);
        assertFalse(ch.isActive());
    }

    @Test
    void emptyInput() {
        Matchmaker mm = mock(Matchmaker.class);
        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        ch.writeInbound("   ");
        flush(ch);

        String out = takeAllOutbound(ch);
        assertTrue(out.contains(Messages.EMPTY_INPUT.trim()));
    }

    @Test
    void invalidNickShowsReason() {
        Matchmaker mm = mock(Matchmaker.class);
        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        ch.writeInbound("ab"); // too short
        flush(ch);

        String out = takeAllOutbound(ch).toLowerCase();
        assertTrue(out.contains("invalid nickname"));
        assertTrue(ch.isActive());
    }

    @Test
    void nickMovesToWaitMatchAndCallsTryMatch() {
        Matchmaker mm = mock(Matchmaker.class);
        when(mm.tryMatch(any(Player.class))).thenReturn(null);

        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        ch.writeInbound("kirill");
        flush(ch);

        verify(mm, times(1)).tryMatch(any(Player.class));

        PlayerContext pc = ch.attr(PLAYER_CTX).get();
        assertEquals(PlayerState.WAIT_MATCH, pc.getState());
        assertEquals("kirill", pc.getNickname());

        String out = takeAllOutbound(ch);
        assertTrue(out.contains("Hi, kirill!"));
        assertTrue(out.toLowerCase().contains("waiting for an opponent"));
    }

    @Test
    void moveWhileWaitingMatchPrintsWaitingOpponent() {
        Matchmaker mm = mock(Matchmaker.class);
        when(mm.tryMatch(any(Player.class))).thenReturn(null);

        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        ch.writeInbound("player1"); // -> WAIT_MATCH
        flush(ch);
        takeAllOutbound(ch); // hi waiting

        ch.writeInbound("rock");
        flush(ch);

        String out = takeAllOutbound(ch);
        assertTrue(out.contains(Messages.WAITING_OPPONENT.trim()));
    }

    @Test
    void badMoveWhenNotExpectingNickPrintsBadMove() {
        Matchmaker mm = mock(Matchmaker.class);
        when(mm.tryMatch(any(Player.class))).thenReturn(null);

        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        ch.writeInbound("player1"); // -> WAIT_MATCH
        flush(ch);
        takeAllOutbound(ch);

        ch.writeInbound("abracadabra"); // expectingNick=false -> Invalid(BAD_MOVE)
        flush(ch);

        String out = takeAllOutbound(ch);
        assertTrue(out.contains(Messages.BAD_MOVE.trim()));
    }

    @Test
    void twoPlayersAreMatchedAndSessionStarts() {
        Matchmaker mm = mock(Matchmaker.class);

        AtomicReference<Player> waiting = new AtomicReference<>();
        when(mm.tryMatch(any(Player.class))).thenAnswer(inv -> {
            Player me = inv.getArgument(0);
            Player prev = waiting.get();
            if (prev == null) {
                waiting.set(me);
                return null;
            }
            waiting.set(null);
            return new GameSession(prev, me);
        });

        EmbeddedChannel ch1 = newChannel(mm);
        EmbeddedChannel ch2 = newChannel(mm);
        takeAllOutbound(ch1); // welcome
        takeAllOutbound(ch2); // welcome

        ch1.writeInbound("player1");
        flush(ch1, ch2);
        takeAllOutbound(ch1); // hi waiting

        ch2.writeInbound("player2");
        flush(ch1, ch2);
        flush(ch1, ch2);

        assertEquals(PlayerState.IN_GAME, ch1.attr(Attrs.PLAYER_CTX).get().getState());
        assertEquals(PlayerState.IN_GAME, ch2.attr(Attrs.PLAYER_CTX).get().getState());

        assertNotNull(ch1.attr(Attrs.SESSION).get());
        assertNotNull(ch2.attr(Attrs.SESSION).get());

        String out1 = takeAllOutbound(ch1);
        String out2 = takeAllOutbound(ch2);

        assertTrue(out1.contains("Opponent found"));
        assertTrue(out2.contains("Opponent found"));
    }

    @Test
    void inGameButNoSessionSendsNoActiveSessionAndMovesToWaitMatch() {
        Matchmaker mm = mock(Matchmaker.class);
        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        PlayerContext pc = ch.attr(Attrs.PLAYER_CTX).get();
        pc.setNickname("player1");
        pc.setState(PlayerState.IN_GAME);
        ch.attr(Attrs.SESSION).set(null);

        ch.writeInbound("rock"); // MoveCmd
        flush(ch);

        String out = takeAllOutbound(ch);
        assertTrue(out.contains(Messages.NO_ACTIVE_SESSION.trim()));
        assertEquals(PlayerState.WAIT_MATCH, pc.getState());
    }

    @Test
    void idleInWaitNickCloses() {
        Matchmaker mm = mock(Matchmaker.class);
        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        ch.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        flush(ch);

        String out = takeAllOutbound(ch);
        assertTrue(out.contains(Messages.TIMEOUT_NICK.trim()));
        flush(ch);
        assertFalse(ch.isActive());
    }

    @Test
    void idleInWaitMatchRemovesFromQueueAndCloses() {
        Matchmaker mm = mock(Matchmaker.class);
        when(mm.tryMatch(any(Player.class))).thenReturn(null);

        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        ch.writeInbound("kirill"); // -> WAIT_MATCH
        flush(ch);
        takeAllOutbound(ch); // hi waiting

        ch.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        flush(ch);

        verify(mm, atLeastOnce()).removeIfWaiting(any(Player.class));

        String out = takeAllOutbound(ch);
        assertTrue(out.contains(Messages.TIMEOUT_WAIT.trim()));
        flush(ch);
        assertFalse(ch.isActive());
    }

    @Test
    void idleInGameOnPlayerWithoutMoveClosesBoth() {
        Matchmaker mm = mock(Matchmaker.class);

        AtomicReference<Player> waiting = new AtomicReference<>();
        when(mm.tryMatch(any(Player.class))).thenAnswer(inv -> {
            Player me = inv.getArgument(0);
            Player prev = waiting.get();
            if (prev == null) {
                waiting.set(me);
                return null;
            }
            waiting.set(null);
            return new GameSession(prev, me);
        });

        EmbeddedChannel ch1 = newChannel(mm);
        EmbeddedChannel ch2 = newChannel(mm);
        takeAllOutbound(ch1);
        takeAllOutbound(ch2);

        ch1.writeInbound("player1");
        flush(ch1, ch2);
        takeAllOutbound(ch1);

        ch2.writeInbound("player2");
        flush(ch1, ch2);
        flush(ch1, ch2);
        takeAllOutbound(ch1);
        takeAllOutbound(ch2);

        ch1.writeInbound("rock");
        flush(ch1, ch2);
        flush(ch1, ch2);
        takeAllOutbound(ch1);
        takeAllOutbound(ch2);

        ch2.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        flush(ch1, ch2);
        flush(ch1, ch2);

        assertFalse(ch1.isActive());
        assertFalse(ch2.isActive());
    }

    @Test
    void channelInactiveInWaitMatchRemovesFromQueue() {
        Matchmaker mm = mock(Matchmaker.class);
        when(mm.tryMatch(any(Player.class))).thenReturn(null);

        EmbeddedChannel ch = newChannel(mm);
        takeAllOutbound(ch); // welcome

        ch.writeInbound("kirill");
        flush(ch);
        takeAllOutbound(ch);

        ch.close();
        flush(ch);

        verify(mm, atLeastOnce()).removeIfWaiting(any(Player.class));
    }

    // -------- helpers --------

    private static EmbeddedChannel newChannel(Matchmaker mm) {
        EmbeddedChannel ch = new EmbeddedChannel(new RpsServerHandler(mm));
        ch.pipeline().fireChannelActive();
        flush(ch);
        return ch;
    }

    private static void flush(EmbeddedChannel... chs) {
        for (int i = 0; i < 20; i++) {
            for (EmbeddedChannel ch : chs) {
                ch.runPendingTasks();
                ch.runScheduledPendingTasks();
            }
        }
    }

    private static String takeAllOutbound(EmbeddedChannel ch) {
        StringBuilder sb = new StringBuilder();
        for (; ; ) {
            Object o = ch.readOutbound();
            if (o == null) {
                break;
            }
            sb.append(o);
        }
        return sb.toString();
    }
}
