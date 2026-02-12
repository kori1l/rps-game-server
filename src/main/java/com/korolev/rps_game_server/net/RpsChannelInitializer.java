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

@Component
public class RpsChannelInitializer extends ChannelInitializer<Channel> {

    private final Matchmaker matchmaker;

    public RpsChannelInitializer(Matchmaker matchmaker) {
        this.matchmaker = matchmaker;
    }

    @Override
    protected void initChannel(Channel ch) {
        ch.pipeline()
                .addLast("idle", new IdleStateHandler(180, 0, 0))
                .addLast(new LineBasedFrameDecoder(256))
                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                .addLast(new StringEncoder(StandardCharsets.UTF_8))
                .addLast(new RpsServerHandler(matchmaker));
    }
}