package com.korolev.rps_game_server.domain;

import io.netty.channel.Channel;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public final class Matchmaker {

    private static final Logger log = LoggerFactory.getLogger(Matchmaker.class);

    private final AtomicReference<Player> waiting = new AtomicReference<>();

    public GameSession tryMatch(Player me) {
        if (me == null || me.channel() == null || !me.channel().isActive()) {
            log.debug("tryMatch: skip inactive player nick={} ch={}",
                    me != null ? me.nickname() : null,
                    me != null && me.channel() != null ? me.channel().id() : null);
            return null;
        }

        while (true) {
            Player other = waiting.get();

            if (other == null) {
                if (waiting.compareAndSet(null, me)) {
                    log.info("matchmaker_wait nick={} ch={}", me.nickname(), me.channel().id());
                    return null;
                }
                continue;
            }

            if (!other.channel().isActive()) {
                waiting.compareAndSet(other, null);
                log.debug("matchmaker_drop_inactive_waiting nick={} ch={}", other.nickname(), other.channel().id());
                continue;
            }

            if (waiting.compareAndSet(other, null)) {
                if (!other.channel().isActive()) {
                    log.debug("matchmaker_drop_inactive_after_take nick={} ch={}", other.nickname(),
                            other.channel().id());
                    continue;
                }

                log.info("matchmaker_matched p1={}({}) vs p2={}({})",
                        other.nickname(), other.channel().id(),
                        me.nickname(), me.channel().id());

                return new GameSession(other, me);
            }
        }
    }

    public boolean removeIfWaiting(Player me) {
        if (me == null || me.channel() == null) {
            return false;
        }

        Channel ch = me.channel();

        while (true) {
            Player cur = waiting.get();
            if (cur == null) {
                return false;
            }

            if (cur.channel() != ch) {
                return false;
            }

            if (waiting.compareAndSet(cur, null)) {
                log.info("matchmaker_removed_waiting nick={} ch={}", cur.nickname(), cur.channel().id());
                return true;
            }
        }
    }
}
