package com.korolev.rps_game_server.net;

import com.korolev.rps_game_server.domain.GameSession;
import com.korolev.rps_game_server.domain.PlayerContext;
import io.netty.util.AttributeKey;

public class Attrs {
    public static final AttributeKey<PlayerContext> PLAYER_CTX = AttributeKey.valueOf("playerCtx");
    public static final AttributeKey<GameSession> SESSION = AttributeKey.valueOf("session");
}
