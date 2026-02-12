package com.korolev.rps_game_server.domain;

public class RpsRules {
    public static Outcome outcome(Move self, Move opponent) {
        if (self == opponent) return Outcome.DRAW;

        return switch (self) {
            case ROCK -> (opponent == Move.SCISSORS) ? Outcome.WIN : Outcome.LOSE;
            case PAPER -> (opponent == Move.ROCK) ? Outcome.WIN : Outcome.LOSE;
            case SCISSORS -> (opponent == Move.PAPER) ? Outcome.WIN : Outcome.LOSE;
        };
    }
}