package com.korolev.rps_game_server.protocol;

import com.korolev.rps_game_server.domain.Move;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CommandParserTest {


    @Test
    void parsesNick() {
        Command c = CommandParser.parse("kirill", true);
        assertInstanceOf(Command.Nick.class, c);
        assertEquals("kirill", ((Command.Nick) c).nickname());
    }

    @Test
    void rejectsBadNick() {
        Command c = CommandParser.parse("k!", true);
        assertInstanceOf(Command.Invalid.class, c);
    }

    @Test
    void parsesMove() {
        Command c = CommandParser.parse("rock", false);
        assertInstanceOf(Command.MoveCmd.class, c);
        assertEquals(Move.ROCK, ((Command.MoveCmd) c).move());
    }

    @Test
    void parsesHelpAndQuit() {
        assertInstanceOf(Command.Help.class, CommandParser.parse("/help", true));
        assertInstanceOf(Command.Quit.class, CommandParser.parse("/quit", false));
    }
}
