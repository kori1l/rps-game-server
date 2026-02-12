package com.korolev.rps_game_server.protocol;

import com.korolev.rps_game_server.domain.Move;

import java.util.regex.Pattern;

import static com.korolev.rps_game_server.protocol.Messages.BAD_MOVE;

public final class CommandParser {

    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_-]{3,16}$");

    public static Command parse(String raw, boolean expectingNick) {
        if (raw == null) return new Command.Empty();

        String line = raw.trim();
        if (line.isEmpty()) return new Command.Empty();

        // slash commands
        if (line.equalsIgnoreCase("/help")) return new Command.Help();
        if (line.equalsIgnoreCase("/quit")) return new Command.Quit();

        if (expectingNick) {
            if (!NICK.matcher(line).matches()) {
                return new Command.Invalid("Invalid nickname. Use 3-16 chars [A-Za-z0-9_-].\r\n");
            }
            return new Command.Nick(line);
        }

        Move move = Move.parse(line);
        if (move == null) {
            return new Command.Invalid(BAD_MOVE);
        }
        return new Command.MoveCmd(move);
    }
}
