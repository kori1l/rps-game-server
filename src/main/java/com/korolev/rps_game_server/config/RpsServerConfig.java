package com.korolev.rps_game_server.config;

import com.korolev.rps_game_server.net.NettyServer;
import com.korolev.rps_game_server.net.RpsChannelInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RpsServerConfig {

    private static final Logger log = LoggerFactory.getLogger(RpsServerConfig.class);

    @Value("${rps.port:8080}")
    private int port;

    @Bean(destroyMethod = "close")
    public NettyServer nettyServer() {
        return new NettyServer(port);
    }

    @Bean
    @ConditionalOnProperty(name = "rps.enabled", havingValue = "true", matchIfMissing = true)
    public CommandLineRunner run(NettyServer nettyServer, RpsChannelInitializer channelInitializer) {
        return args -> {
            nettyServer.start(channelInitializer);
            log.info("RPS server started on port {}", port);
        };
    }
}
