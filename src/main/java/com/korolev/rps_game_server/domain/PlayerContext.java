package com.korolev.rps_game_server.domain;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PlayerContext {
    private PlayerState state = PlayerState.WAIT_NICK;
    private String nickname;
}
