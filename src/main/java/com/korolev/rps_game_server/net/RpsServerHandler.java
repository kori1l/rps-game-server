package com.korolev.rps_game_server.net;

import com.korolev.rps_game_server.domain.GameSession;
import com.korolev.rps_game_server.domain.Matchmaker;
import com.korolev.rps_game_server.domain.Move;
import com.korolev.rps_game_server.domain.Player;
import com.korolev.rps_game_server.domain.PlayerContext;
import com.korolev.rps_game_server.domain.PlayerState;
import com.korolev.rps_game_server.protocol.Command;
import com.korolev.rps_game_server.protocol.CommandParser;
import com.korolev.rps_game_server.protocol.Messages;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RpsServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(RpsServerHandler.class);

    public static final String IDLE_HANDLER_NAME = "idle";

    public static final int NICK_IDLE_SECONDS = 180;
    private static final int WAIT_IDLE_SECONDS = 180;
    private static final int GAME_IDLE_SECONDS = 120;

    private final Matchmaker matchmaker;

    public RpsServerHandler(Matchmaker matchmaker) {
        this.matchmaker = matchmaker;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        PlayerContext pc = new PlayerContext();
        ctx.channel().attr(Attrs.PLAYER_CTX).set(pc);

        setIdleTimeout(ctx.channel(), NICK_IDLE_SECONDS);

        log.info("client_connected ch={} remote={}", shortId(ctx.channel()), ctx.channel().remoteAddress());
        ctx.writeAndFlush(Messages.WELCOME);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        PlayerContext pc = ctx.channel().attr(Attrs.PLAYER_CTX).get();
        if (pc == null) {
            pc = new PlayerContext();
            ctx.channel().attr(Attrs.PLAYER_CTX).set(pc);
            log.warn("player_context_missing_recreated ch={}", shortId(ctx.channel()));
        }

        MDC.put("ch", shortId(ctx.channel()));
        MDC.put("nick", safeNick(pc));
        try {
            boolean expectingNick = pc.getState() == PlayerState.WAIT_NICK;
            Command cmd = CommandParser.parse(msg, expectingNick);

            switch (cmd) {
                case Command.Empty ignored -> ctx.writeAndFlush(Messages.EMPTY_INPUT);

                case Command.Help ignored -> ctx.writeAndFlush(Messages.HELP);

                case Command.Quit ignored -> {
                    log.info("client_quit state={}", pc.getState());
                    ctx.writeAndFlush(Messages.BYE).addListener(f -> ctx.close());
                }

                case Command.Invalid inv -> {
                    log.debug("client_invalid_input state={} reason={}", pc.getState(), inv.reason());
                    ctx.writeAndFlush(inv.reason());
                }

                case Command.Nick nickCmd -> handleNick(ctx, pc, nickCmd.nickname());

                case Command.MoveCmd moveCmd -> {
                    if (pc.getState() != PlayerState.IN_GAME) {
                        log.debug("move_while_not_in_game state={}", pc.getState());
                        ctx.writeAndFlush(Messages.WAITING_OPPONENT);
                        return;
                    }
                    handleMove(ctx, pc, moveCmd.move());
                }
            }
        } finally {
            MDC.clear();
        }
    }

    private void handleNick(ChannelHandlerContext ctx, PlayerContext pc, String nick) {
        if (pc.getState() != PlayerState.WAIT_NICK) {
            log.debug("nick_received_in_non_wait_nick state={}", pc.getState());
        }

        pc.setNickname(nick);
        MDC.put("nick", nick); // update MDC immediately
        pc.setState(PlayerState.WAIT_MATCH);
        setIdleTimeout(ctx.channel(), WAIT_IDLE_SECONDS);

        log.info("nick_accepted");

        ctx.writeAndFlush(String.format(Messages.HI_WAITING_TEMPLATE, nick));

        Player me = new Player(nick, ctx.channel());

        GameSession session;
        try {
            session = matchmaker.tryMatch(me);
        } catch (RuntimeException e) {
            log.error("matchmaker_failed", e);
            ctx.writeAndFlush(Messages.TIMEOUT_GENERIC).addListener(f -> ctx.close());
            return;
        }

        if (session == null) {
            log.info("queued_for_match");
            return;
        }

        log.info("match_found vs={}",
                session.p1().nickname().equals(nick) ? session.p2().nickname() : session.p1().nickname());

        attachSession(session);
        session.start();
    }

    private void attachSession(GameSession session) {
        Player p1 = session.p1();
        Player p2 = session.p2();

        p1.channel().attr(Attrs.SESSION).set(session);
        p2.channel().attr(Attrs.SESSION).set(session);

        setState(p1.channel(), PlayerState.IN_GAME);
        setState(p2.channel(), PlayerState.IN_GAME);

        setIdleTimeout(p1.channel(), GAME_IDLE_SECONDS);
        setIdleTimeout(p2.channel(), GAME_IDLE_SECONDS);

        log.info("session_attached p1={}({}) p2={}({})",
                p1.nickname(), shortId(p1.channel()),
                p2.nickname(), shortId(p2.channel()));
    }

    private void handleMove(ChannelHandlerContext ctx, PlayerContext pc, Move move) {
        GameSession session = ctx.channel().attr(Attrs.SESSION).get();
        if (session == null) {
            log.warn("move_but_no_session ch={} nick={} -> back_to_wait_match",
                    shortId(ctx.channel()), safeNick(pc));

            pc.setState(PlayerState.WAIT_MATCH);
            setIdleTimeout(ctx.channel(), WAIT_IDLE_SECONDS);
            ctx.writeAndFlush(Messages.NO_ACTIVE_SESSION);
            return;
        }

        log.debug("move_received ch={} nick={} move={}",
                shortId(ctx.channel()), safeNick(pc), move);

        Player me = new Player(safeNick(pc), ctx.channel());
        session.submitMove(me, move);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent e && e.state() == IdleState.READER_IDLE) {

            PlayerContext pc = ctx.channel().attr(Attrs.PLAYER_CTX).get();
            if (pc == null) {
                log.warn("idle_but_no_player_context ch={} -> close", shortId(ctx.channel()));
                ctx.close();
                return;
            }

            Player me = new Player(safeNick(pc), ctx.channel());

            switch (pc.getState()) {
                case WAIT_NICK -> {
                    log.info("timeout_wait_nick ch={}", shortId(ctx.channel()));
                    ctx.writeAndFlush(Messages.TIMEOUT_NICK).addListener(f -> ctx.close());
                }

                case WAIT_MATCH -> {
                    log.info("timeout_wait_match ch={} nick={}", shortId(ctx.channel()), safeNick(pc));
                    matchmaker.removeIfWaiting(me);
                    ctx.writeAndFlush(Messages.TIMEOUT_WAIT).addListener(f -> ctx.close());
                }

                case IN_GAME -> {
                    log.info("idle_in_game ch={} nick={}", shortId(ctx.channel()), safeNick(pc));
                    GameSession session = ctx.channel().attr(Attrs.SESSION).get();
                    if (session != null) {
                        session.onIdle(me);
                    } else {
                        log.warn("idle_in_game_but_no_session ch={} nick={} -> close",
                                shortId(ctx.channel()), safeNick(pc));
                        ctx.writeAndFlush(Messages.TIMEOUT_GENERIC).addListener(f -> ctx.close());
                    }
                }
            }
            return;
        }

        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        PlayerContext pc = ctx.channel().attr(Attrs.PLAYER_CTX).get();
        if (pc == null) {
            log.info("client_disconnected ch={} (no player ctx)", shortId(ctx.channel()));
            return;
        }

        log.info("client_disconnected ch={} nick={} state={}",
                shortId(ctx.channel()), safeNick(pc), pc.getState());

        Player me = new Player(safeNick(pc), ctx.channel());

        if (pc.getState() == PlayerState.WAIT_MATCH) {
            boolean removed = matchmaker.removeIfWaiting(me);
            log.debug("removed_from_queue ch={} nick={} removed={}",
                    shortId(ctx.channel()), safeNick(pc), removed);
        }

        if (pc.getState() == PlayerState.IN_GAME) {
            GameSession session = ctx.channel().attr(Attrs.SESSION).getAndSet(null);
            if (session != null) {
                session.onDisconnect(me);
            }
        }
    }

    private void setState(Channel ch, PlayerState state) {
        PlayerContext pc = ch.attr(Attrs.PLAYER_CTX).get();
        if (pc != null) pc.setState(state);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        PlayerContext pc = ctx.channel().attr(Attrs.PLAYER_CTX).get();
        log.warn("channel_exception ch={} nick={} state={}",
                shortId(ctx.channel()), pc == null ? "" : safeNick(pc), pc == null ? null : pc.getState(), cause);
        ctx.close();
    }

    private void setIdleTimeout(Channel ch, int seconds) {
        ChannelPipeline p = ch.pipeline();
        IdleStateHandler newIdle = new IdleStateHandler(seconds, 0, 0);

        if (p.get(IDLE_HANDLER_NAME) != null) {
            p.replace(IDLE_HANDLER_NAME, IDLE_HANDLER_NAME, newIdle);
        } else {
            p.addFirst(IDLE_HANDLER_NAME, newIdle);
        }

        log.debug("idle_timeout_set ch={} seconds={}", shortId(ch), seconds);
    }

    private String safeNick(PlayerContext pc) {
        return pc.getNickname() == null ? "" : pc.getNickname();
    }

    private String shortId(Channel ch) {
        return ch.id().asShortText();
    }
}
