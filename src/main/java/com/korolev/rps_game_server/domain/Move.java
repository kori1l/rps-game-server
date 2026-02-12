package com.korolev.rps_game_server.domain;

public enum Move {
    ROCK, PAPER, SCISSORS;

    public static Move parse(String raw) {
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case "ROCK", "R" -> ROCK;
            case "PAPER", "P" -> PAPER;
            case "SCISSORS", "S" -> SCISSORS;
            default -> null;
        };
    }
}