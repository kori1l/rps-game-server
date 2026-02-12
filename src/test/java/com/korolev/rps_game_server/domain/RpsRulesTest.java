package com.korolev.rps_game_server.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RpsRulesTest {

    @Test
    void allOutcomes() {
        assertEquals(Outcome.DRAW, RpsRules.outcome(Move.ROCK, Move.ROCK));
        assertEquals(Outcome.LOSE, RpsRules.outcome(Move.ROCK, Move.PAPER));
        assertEquals(Outcome.WIN,  RpsRules.outcome(Move.ROCK, Move.SCISSORS));

        assertEquals(Outcome.WIN,  RpsRules.outcome(Move.PAPER, Move.ROCK));
        assertEquals(Outcome.DRAW, RpsRules.outcome(Move.PAPER, Move.PAPER));
        assertEquals(Outcome.LOSE, RpsRules.outcome(Move.PAPER, Move.SCISSORS));

        assertEquals(Outcome.LOSE, RpsRules.outcome(Move.SCISSORS, Move.ROCK));
        assertEquals(Outcome.WIN,  RpsRules.outcome(Move.SCISSORS, Move.PAPER));
        assertEquals(Outcome.DRAW, RpsRules.outcome(Move.SCISSORS, Move.SCISSORS));
    }
}