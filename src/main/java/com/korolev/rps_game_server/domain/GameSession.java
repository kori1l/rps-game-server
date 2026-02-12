package com.korolev.rps_game_server.domain;

import io.netty.channel.Channel;
import io.netty.util.concurrent.EventExecutor;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class GameSession {

    private static final Logger log = LoggerFactory.getLogger(GameSession.class);

    private final Player p1;
    private final Player p2;

    private final EventExecutor owner;

    private Move m1;
    private Move m2;

    private boolean finished;

    public GameSession(Player p1, Player p2) {
        this.p1 = Objects.requireNonNull(p1);
        this.p2 = Objects.requireNonNull(p2);

        // Choose owner = eventLoop of one of the players.
        this.owner = p1.channel().eventLoop();

        log.info("session_created p1={}({}) p2={}({}) owner={}",
                p1.nickname(), shortId(p1.channel()),
                p2.nickname(), shortId(p2.channel()),
                owner);
    }

    public Player p1() {
        return p1;
    }

    public Player p2() {
        return p2;
    }

    /**
     * Start match: notify both players and request a move.
     * Can be called from any thread.
     */
    public void start() {
        runOnOwner(() -> {
            if (finished) {
                log.debug("session_start_ignored_finished");
                return;
            }

            log.info("session_started");

            send(p1, "Opponent found: " + p2.nickname() + "\r\nType ROCK/PAPER/SCISSORS:\r\n");
            send(p2, "Opponent found: " + p1.nickname() + "\r\nType ROCK/PAPER/SCISSORS:\r\n");
        });
    }

    /**
     * Player submitted a move. Can be called from any thread.
     */
    public void submitMove(Player player, Move move) {
        runOnOwner(() -> {
            if (finished) {
                log.debug("move_ignored_finished from={}", nick(player));
                return;
            }
            if (isNotParticipant(player)) {
                log.warn("move_from_non_participant ch={}", shortId(player.channel()));
                return;
            }

            int idx = indexOf(player);

            if (idx == 1) {
                if (m1 != null) {
                    log.debug("duplicate_move player={} move={}", p1.nickname(), move);
                    send(p1, "You already made a move. Waiting for opponent...\r\n");
                    return;
                }
                m1 = move;
                log.debug("move_accepted player={} move={}", p1.nickname(), move);

                // UX: if the second player hasn't moved yet - remind them it's their turn
                if (m2 == null) {
                    send(p2, "Your turn. Type ROCK/PAPER/SCISSORS:\r\n");
                }
            } else {
                if (m2 != null) {
                    log.debug("duplicate_move player={} move={}", p2.nickname(), move);
                    send(p2, "You already made a move. Waiting for opponent...\r\n");
                    return;
                }
                m2 = move;
                log.debug("move_accepted player={} move={}", p2.nickname(), move);

                if (m1 == null) {
                    send(p1, "Your turn. Type ROCK/PAPER/SCISSORS:\r\n");
                }
            }

            // If the other player hasn't moved yet - just wait
            if (m1 == null || m2 == null) {
                send(player, "Waiting for opponent's move...\r\n");
                return;
            }

            // Both moves received - calculate result
            Outcome o1 = RpsRules.outcome(m1, m2);
            Outcome o2 = invert(o1);

            if (o1 == Outcome.DRAW) {
                Move a = m1, b = m2;
                resetRound();

                log.info("round_draw p1Move={} p2Move={}", a, b);

                send(p1, "Draw! You chose " + a + ", opponent chose " + b + ". Try again: ROCK/PAPER/SCISSORS\r\n");
                send(p2, "Draw! You chose " + b + ", opponent chose " + a + ". Try again: ROCK/PAPER/SCISSORS\r\n");
                return;
            }

            log.info("round_finished p1Move={} p2Move={} p1Result={} p2Result={}",
                    m1, m2, o1, o2);

            finishByResult(m1, m2, o1, o2);
        });
    }

    /**
     * Idle timeout from Netty. Do not timeout a player if they have already moved and are waiting.
     */
    public void onIdle(Player p) {
        runOnOwner(() -> {
            if (finished) {
                log.debug("idle_ignored_finished player={}", nick(p));
                return;
            }
            if (isNotParticipant(p)) {
                log.warn("idle_from_non_participant ch={}", shortId(p.channel()));
                return;
            }

            int idx = indexOf(p);
            boolean alreadyMoved = (idx == 1) ? (m1 != null) : (m2 != null);

            if (alreadyMoved) {
                log.debug("idle_ignored_player_already_moved player={}", nick(p));
                return;
            }

            Player winner = other(p);

            log.info("idle_timeout_loss loser={} winner={}", nick(p), winner.nickname());

            send(p, "Timeout. You LOSE.\r\nGame over. Bye!\r\n");
            send(winner, "Opponent timeout. You WIN!\r\nGame over. Bye!\r\n");

            finish("idle_timeout");
        });
    }

    /**
     * Player channel disconnected.
     */
    public void onDisconnect(Player leaver) {
        runOnOwner(() -> {
            if (finished) {
                log.debug("disconnect_ignored_finished leaver={}", nick(leaver));
                return;
            }
            if (isNotParticipant(leaver)) {
                log.warn("disconnect_from_non_participant ch={}", shortId(leaver.channel()));
                return;
            }

            Player winner = other(leaver);

            log.info("player_disconnected leaver={} winner={}",
                    nick(leaver), winner.nickname());

            if (winner.channel().isActive()) {
                send(winner, "Opponent disconnected. You WIN!\r\nGame over. Bye!\r\n");
            }

            finish("disconnect");
        });
    }

    private void finishByResult(Move p1Move, Move p2Move, Outcome o1, Outcome o2) {
        if (finished) {
            return;
        }

        send(p1, "You chose " + p1Move + ", opponent chose " + p2Move + ". You " + o1 + "!\r\nGame over. Bye!\r\n");
        send(p2, "You chose " + p2Move + ", opponent chose " + p1Move + ". You " + o2 + "!\r\nGame over. Bye!\r\n");

        finish("result");
    }

    /**
     * Idempotent finish: closes both channels exactly once.
     */
    private void finish(String reason) {
        if (finished) {
            return;
        }
        finished = true;

        log.info("session_finished {} reason={}", sessionKey(), reason);

        // close() is safe even if already closed/inactive
        p1.channel().close();
        p2.channel().close();
    }

    private void resetRound() {
        m1 = null;
        m2 = null;
    }

    private void send(Player p, String msg) {
        Channel ch = p.channel();
        if (ch.isActive()) {
            ch.writeAndFlush(msg);
        } else {
            log.debug("send_skipped_inactive {} to={}({})",
                    sessionKey(), p.nickname(), shortId(ch));
        }
    }

    private boolean isNotParticipant(Player p) {
        Channel ch = p.channel();
        return ch != p1.channel() && ch != p2.channel();
    }

    private int indexOf(Player p) {
        return (p.channel() == p1.channel()) ? 1 : 2;
    }

    private Player other(Player p) {
        return (p.channel() == p1.channel()) ? p2 : p1;
    }

    private Outcome invert(Outcome o1) {
        return switch (o1) {
            case WIN -> Outcome.LOSE;
            case LOSE -> Outcome.WIN;
            case DRAW -> Outcome.DRAW;
        };
    }

    private void runOnOwner(Runnable task) {
        if (owner.inEventLoop()) {
            MDC.put("sess", sessionKey());
            try {
                task.run();
            } finally {
                MDC.remove("sess");
            }
        } else {
            owner.execute(() -> {
                MDC.put("sess", sessionKey());
                try {
                    task.run();
                } finally {
                    MDC.remove("sess");
                }
            });
        }
    }

    private String sessionKey() {
        return "p1=" + p1.nickname() + "(" + shortId(p1.channel()) + ")"
                + " p2=" + p2.nickname() + "(" + shortId(p2.channel()) + ")";
    }

    private String shortId(Channel ch) {
        return ch.id().asShortText();
    }

    private String nick(Player p) {
        return p == null ? "" : p.nickname();
    }
}
