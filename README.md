# Backgammon Engine

Java 21 engines for long and short backgammon. Each engine creates an initial position,
validates one command at a time and returns a new game snapshot with available next actions.

## Scope

Engines contain game mechanics and retain no per-session state. The calling session supplies dice,
stores returned states and history, receives player commands, and decides when to cancel a game.
There is no internal game loop, random roll generation, persistence or event publication.
Each returned state contains the ordered events produced by the command that created it, so
storing state history also preserves the events for each transition.

## Requirements and dependencies

Use JDK 21. The Maven Wrapper can be used to build the project without a separately installed
Maven. Install these project dependencies locally, or make them available through your configured
Maven repository:

| Group                   | Artifact           | Version |
| ----------------------- | ------------------ | ------- |
| `ivs.games.engine`      | `game-engine`      | `1.0.0` |
| `ivs.games.accessories` | `backgammon-board` | `1.0.0` |

Guava, Apache Commons Lang and Lombok are declared in `pom.xml`.
Tests use JUnit 5 and Mockito. Lombok annotation processing is configured for Maven compilation.

```shell
./mvnw clean install
```

On Windows, use `mvnw.cmd clean install`. The build also attaches a source JAR.

Consumer dependency after installation:

```xml
<dependency>
    <groupId>ivs.games.engine</groupId>
    <artifactId>backgammon-engine</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Packages

All packages below are under `ivs.games.engine.backgammon`.

| Package     | Responsibility                                        |
| ----------- | ----------------------------------------------------- |
| Root        | Common initialization, validation and action dispatch |
| `command`   | Player commands and system abort                      |
| `event`     | Immutable events produced by state transitions        |
| `state`     | Setup, shared state and game phases                   |
| `longgame`  | Long backgammon engine, state and internal resolver   |
| `shortgame` | Short backgammon engine, state and internal resolver  |

## Coordinates

Board snapshots use **physical indices 0–23**. Commands and option maps use
**side-relative route points 1–24**. Route points always increase towards the active side's exit.
Physical directions are variant-specific:

| Variant | White route                                  | Black route                                   |
| ------- | -------------------------------------------- | --------------------------------------------- |
| Long    | Starts at index 0; increases with wraparound | Starts at index 12; increases with wraparound |
| Short   | Physical indices 0 through 23                | Physical indices 23 through 0                 |

The home consists of route points 19–24. The virtual exact bearing-off destination is 25.
It must not be converted to a physical cell index. Engine package-internal addressing maps
perform conversion using the board library's `BoardAddressing` component.

## Start and process commands

```java
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.shortgame.ShortBackgammonEngine;
import ivs.games.engine.backgammon.shortgame.ShortGameState;
import ivs.games.engine.backgammon.state.GameSetup;

import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.WHITE;

ShortBackgammonEngine engine = new ShortBackgammonEngine();
ShortGameState initial = engine.startGame(new GameSetup(WHITE));
ShortGameState rolled = engine.processCommand(initial, new DiceRolled(WHITE, 1, 2));
ShortGameState moved = engine.processCommand(rolled, new Move(WHITE, 1, 1));
```

For long backgammon, use `LongBackgammonEngine` and `LongGameState` from `longgame`.
The caller keeps the returned state for the next command. Do not continue with an older snapshot
unless deliberately branching or replaying a game.

## Commands and phases

| Command      | Parameters                           | Effect                                        |
| ------------ | ------------------------------------ | --------------------------------------------- |
| `DiceRolled` | Color, first value, second value     | Records roll and resolves actions             |
| `Move`       | Color, source route point, die value | Moves to source plus die value                |
| `Enter`      | Color, destination route point       | Enters from bar; die value equals destination |
| `BearOff`    | Color, source route point, die value | Removes a checker from the board              |
| `Abort`      | None                                 | Changes phase to `ABORTED`                    |

Player commands require the current active color. Each movement command consumes one occurrence
of its selected value, not the sum of the roll. Doubles expand to four equal values.
`Enter` is supported only by short backgammon and requires a destination from 1 to 6.

| Phase           | Accepted commands                       |
| --------------- | --------------------------------------- |
| `AWAITING_DICE` | `DiceRolled`, system `Abort`            |
| `AWAITING_MOVE` | Legal variant movements, system `Abort` |
| `FINISHED`      | None                                    |
| `ABORTED`       | None                                    |

Starting side selection is external. The engine does not implement the opening single-die contest:
it accepts two values from 1 to 6 in any dice phase, including doubles on the first turn.

## Available actions and turn completion

| State property                | Keys                                 | Values               |
| ----------------------------- | ------------------------------------ | -------------------- |
| `allowedMoves`                | Active-side source route points      | Permitted die values |
| `allowedBearOffs`             | Active-side source route points      | Permitted die values |
| `allowedEntries` (short only) | Active-side destination route points | Permitted die values |

These maps contain legal **first actions** from the current snapshot, filtered to permit
maximum possible use of the remaining values. If only one action can be performed,
the highest playable value is required. The resolver checks possible continuations;
the player chooses among retained first actions.

After a roll and each non-winning action, the engine resolves options again.
If the pool is empty or no legal action remains, the active side changes and the engine clears
turn dice and option maps. There is no pass command. Long backgammon also resets head departures
and marks the outgoing side's first turn complete, even after a blocked roll.

## Transition events

Each state has an immutable `events` list containing only the events generated by the operation
that produced that state, in occurrence order. The initial state contains `GameStarted`.
A new successful command replaces the previous state's events; rejected commands produce no
state. Keep successive states to retain the event history for the session.

Events include game start, dice rolls, checker movement, entry, bearing off, hits, turn changes,
game completion and aborts. Checker route points in events use the same active-side-relative
coordinates as commands; a hit's route point is expressed from the hitter's perspective.

## Long backgammon

- Each side starts with fifteen ordinary checkers at route point 1 (its head).
- Both sides move in the same physical direction, with wraparound.
- An opposing occupied point is closed. There is no hitting or bar entry.
- Normally only one checker may leave the head per turn.
- On each side's first turn, doubles of 3, 4 or 6 allow two departures.
  This implementation applies the exception without additionally testing whether it is necessary.
- A six-point prime is forbidden unless an opposing checker has passed its end on the opponent's route.
  An opposing borne-off checker counts as having passed it. Prime scans do not wrap beyond the route exit.

## Short backgammon

- Initial active-side route points: 1 × 2 checkers, 12 × 5, 17 × 3, 19 × 5.
- Sides move in opposite physical directions.
- A point with two or more opposing checkers is closed.
- Landing on a single opposing checker hits it onto its bar, during `Move` or `Enter`.
- While any active checker remains on the bar, only entries are permitted.
- After the final entry, unused values may be used for ordinary movement or legal bearing off.

## Bearing off and completion

Both engines require an empty active bar and all remaining active checkers in the home.
An exact value satisfies `from + diceValue == 25`. An oversize value is allowed only from
the earliest occupied active home point. A smaller value requires an ordinary `Move`;
submitting it as `BearOff` is rejected.

In short backgammon, a checker hit during bearing off must enter and return home before
bearing off resumes.

Removing the fifteenth checker sets `FINISHED` and `winner`, clears available action maps,
and retains the winning side as active. Unused movement values and the original roll remain
in the final snapshot. No subsequent action is accepted. Only the winner is recorded;
scoring of gammons, backgammons and doubling-cube stakes is outside the current implementation.

## System abort

```java
import ivs.games.engine.backgammon.command.Abort;

ShortGameState aborted = engine.processCommand(moved, new Abort());
```

`Abort` returns no color and bypasses player-color checks. It is accepted in either non-terminal
phase. The default handler changes only the phase to `ABORTED`; board, active side, dice,
option maps and subtype properties remain as they were. It does not declare a winner.
Further commands, including another abort, are rejected.

## State ownership and errors

States use Lombok `@SuperBuilder(toBuilder = true)` and store supplied references without
validation or defensive copying. Engine-produced snapshots contain immutable board data,
lists and option maps. Code constructing custom states must preserve those invariants,
including map/set immutability and consistency of the options with the board and dice.
Engines do not reconstruct or comprehensively validate arbitrary supplied game states.

`EngineCommandException` indicates a rejected command. No new state is returned and the input
snapshot remains unchanged. Technical failures follow the shared `AbstractGameEngine` contract.
Engines retain no per-session state and may serve independent sessions concurrently when
all supplied state data respects the immutability contract.

## Tests

- Common engine validation, dispatch, movement pools and abort behavior.
- Long engine unit tests with a mocked resolver, geometry tests, turn-sequence integration tests,
  and six-point blocking integration scenarios.
- Short engine unit tests with a mocked resolver and direct resolver tests.
- Short integration tests using public commands and the real resolver: hitting, entry, doubles,
  mandatory dice usage, bearing off, victory, cancellation and state preservation.

See [doc/backgammon-flow.txt](doc/backgammon-flow.txt) for the command lifecycle.

## Console board renderer

`BackgammonBoardRenderer` displays a long or short game snapshot with point numbers relative to
the selected side:

```java
var renderer = new BackgammonBoardRenderer();
System.out.println(renderer.render(gameState, StandardCheckerColor.WHITE));
```

The test-source console demo `ivs.games.engine.backgammon.demo.BoardRendererDemo` prints both
variants from both sides' perspectives. Run it from the repository root with
`./mvnw test-compile exec:java` (Windows: `mvnw.cmd test-compile exec:java`). It is not included
in the published library artifact.

## License

See [LICENSE](LICENSE).
