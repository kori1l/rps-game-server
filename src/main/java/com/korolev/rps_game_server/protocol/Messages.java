package com.korolev.rps_game_server.protocol;

public final class Messages {
    private Messages() {}

    public static final String LOGO =
            """
                     ____  ____  ____\r
                    |  _ \\|  _ \\/ ___|\r
                    | |_) | |_) \\___ \\\r
                    |  _ <|  __/ ___) |\r
                    |_| \\_\\_|   |____/\r
                    """;

    public static final String WELCOME =
            LOGO
            + "\r\nWelcome to Rock-Paper-Scissors!\r\n"
            + "\r\nEnter your nickname (or type /help):\r\n";
    public static final String EMPTY_INPUT = "Empty input. Try again:\r\n";
    public static final String WAITING_OPPONENT = "Still waiting for an opponent...\r\n";

    public static final String HI_WAITING_TEMPLATE = "Hi, %s! Waiting for an opponent...\r\n";

    public static final String NO_ACTIVE_SESSION = "No active session. Waiting for an opponent...\r\n";

    public static final String BAD_MOVE = "Invalid move. Type ROCK/PAPER/SCISSORS.\r\n";

    public static final String TIMEOUT_NICK = "Timeout waiting for nickname. Bye!\r\n";
    public static final String TIMEOUT_WAIT = "Timeout waiting for opponent. Bye!\r\n";
    public static final String TIMEOUT_GENERIC = "Timeout. Bye!\r\n";

    public static final String BYE = "Bye!\r\n";

    public static final String HELP =
            """
                    Commands:\r
                      /help - show this message\r
                      /quit - disconnect\r
                    \r
                    Rules:\r
                      ROCK beats SCISSORS\r
                      SCISSORS beat PAPER\r
                      PAPER beats ROCK\r
                    \r
                    Gameplay:\r
                      Enter nickname (3-16 chars: A-Za-z0-9_-)\r
                      When matched, type: ROCK / PAPER / SCISSORS\r
                    """;
}
