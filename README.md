# RPS Game Server (Rock–Paper–Scissors)

A lightweight Netty-based multiplayer server for playing Rock–Paper–Scissors over Telnet. The app runs as a Spring Boot application and opens two ports:
- 8080 — game port (plain TCP/Telnet)
- 8081 — management port (Spring Boot Actuator: `/actuator/health`, etc.)

## Quick start

### Requirements
- Java 21 (JDK 21)
- Maven 3.9+
- (Optional) Docker and Docker Compose

### Run locally (Maven)
```bash
mvn clean package
java -jar target/rps-game-server-0.0.1-SNAPSHOT.jar
```
By default, the game port is 8080 and the management port is 8081. You can change them in `src/main/resources/application.yml` or via environment variables (see below).

### Run with Docker
```bash
docker run --rm \
  -p 8080:8080 -p 8081:8081 \
  -e RPS_PORT=8080 -e SERVER_PORT=8081 \
  rps-game-server:local
```

### Run with Docker Compose
```bash
docker compose up --build
```
Compose publishes ports 8080 (game) and 8081 (actuator). Application logs are printed to the console.

## Configuration
- Netty game port: property `rps.port` (default 8080). Can also be set via env var `RPS_PORT`.
- Management (Spring Boot Actuator): `server.port` (default 8081).
- Config file: `src/main/resources/application.yml`.

Example JVM overrides when running the JAR:
```bash
java -Drps.port=9090 -Dserver.port=9091 -jar target/rps-game-server-0.0.1-SNAPSHOT.jar
```

## How to connect
Use any Telnet client.
- Windows: enable the built-in `telnet` client, or use `nc`/PuTTY.
- macOS/Linux: built-in `telnet`/`nc`.

```bash
telnet localhost 8080
```
After connecting, the server will prompt you to enter a nickname.

## How to play
1) Enter your nickname (allowed chars: A–Z, a–z, 0–9, `_`, `-`; length 3–16).
2) The server puts you into a queue and matches you with an opponent.
3) Once matched, both players should enter one move: `ROCK`, `PAPER`, or `SCISSORS`.
   - Short forms are accepted: `R`, `P`, `S`.
4) If both moves are the same — it’s a draw; the round restarts and the server asks again.
5) If there is a winner — the server announces the result and closes the session.

### Commands
- `/help` — show help.
- `/quit` — disconnect from the server.

### Server messages (examples)
- `Enter your nickname:` — prompt to enter a nickname.
- `Hi, <nick>! Waiting for an opponent...` — you’re queued for a match.
- `Opponent found: <nick>. Type ROCK/PAPER/SCISSORS:` — the match has started, enter your move.
- `Invalid move. Type ROCK/PAPER/SCISSORS.` — invalid input.
- `Timeout ... Bye!` — an inactivity timeout has fired (see below).

## Game rules
Classic rules:
- ROCK beats SCISSORS
- SCISSORS beat PAPER
- PAPER beats ROCK

Same moves — `DRAW` (round is replayed).

## Timeouts
- Nickname input: 180 seconds. On timeout — the connection is closed.
- Waiting for an opponent: 180 seconds. On timeout — the connection is closed.
- In-game inactivity: 120 seconds. If a player hasn’t made a move — they lose by timeout, the opponent wins.

## Healthcheck (Actuator)
- Endpoint: `GET http://localhost:8081/actuator/health`
- Includes custom `NettyEventLoopHealthIndicator`

## Logs
Logging is configured via Logback (`src/main/resources/logback-spring.xml`) and includes MDC markers: `ch` (channel), `nick` (nickname), `sess` (pair of players).

## Tests
```bash
mvn test
```

## Troubleshooting
- Port already in use: change `rps.port`/`server.port` or free the port.
- Telnet cannot connect:
  - ensure the server is running and listening on port 8080,
  - on Windows, check firewall / enable Telnet client,
  - try connecting via `nc`/PuTTY.
- Actuator not accessible: verify it’s listening on 8081 and not blocked by a firewall.

## Example session (telnet)
```text
$ telnet localhost 8080
 ____  ____  ____                                                                                                                                                                                                                   
|  _ \|  _ \/ ___|                                                                                                                                                                                                                  
| |_) | |_) \___ \                                                                                                                                                                                                                  
|  _ <|  __/ ___) |                                                                                                                                                                                                                 
|_| \_\_|   |____/                                                                                                                                                                                                                  
                                                                                                                                                                                                                                    
Welcome to Rock-Paper-Scissors!                                                                                                                                                                                                     
                                                                                                                                                                                                                                    
Enter your nickname (or type /help): 
player1
Hi, player1! Waiting for an opponent...
Opponent found: player2
Type ROCK/PAPER/SCISSORS:
ROCK
Waiting for opponent's move...
You chose ROCK, opponent chose SCISSORS. You WIN!
Game over. Bye!
```
