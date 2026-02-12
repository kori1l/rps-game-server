package com.korolev.rps_game_server.domain;

import io.netty.channel.Channel;

public record Player(String nickname, Channel channel) {}
