package com.korolev.rps_game_server.net;

import com.korolev.rps_game_server.domain.Matchmaker;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

import static com.korolev.rps_game_server.net.RpsServerHandler.IDLE_HANDLER_NAME;
import static com.korolev.rps_game_server.net.RpsServerHandler.NICK_IDLE_SECONDS;

@Component
public class RpsChannelInitializer extends ChannelInitializer<Channel> {

    private final Matchmaker matchmaker;

    public RpsChannelInitializer(Matchmaker matchmaker) {
        this.matchmaker = matchmaker;
    }

    @Override
    protected void initChannel(Channel ch) {
        ch.pipeline()
                .addLast(IDLE_HANDLER_NAME, new IdleStateHandler(NICK_IDLE_SECONDS, 0, 0))
                .addLast(new LineBasedFrameDecoder(256))
                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                .addLast(new StringEncoder(StandardCharsets.UTF_8))
                .addLast(new RpsServerHandler(matchmaker));
    }
}