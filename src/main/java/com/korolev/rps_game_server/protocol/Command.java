package com.korolev.rps_game_server.protocol;

import com.korolev.rps_game_server.domain.Move;

public sealed interface Command
        permits Command.Nick, Command.MoveCmd, Command.Help, Command.Quit, Command.Empty, Command.Invalid {

    record Nick(String nickname) implements Command {}
    record MoveCmd(Move move) implements Command {}

    record Help() implements Command {}
    record Quit() implements Command {}

    record Empty() implements Command {}
    record Invalid(String reason) implements Command {}
}
