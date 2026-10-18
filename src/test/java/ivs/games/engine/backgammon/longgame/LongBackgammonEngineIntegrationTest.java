package ivs.games.engine.backgammon.longgame;

import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardChecker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.command.Abort;
import ivs.games.engine.backgammon.command.BearOff;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.event.CheckerBorneOffEvent;
import ivs.games.engine.backgammon.event.CheckerMovedEvent;
import ivs.games.engine.backgammon.event.DiceRolledEvent;
import ivs.games.engine.backgammon.event.GameAbortedEvent;
import ivs.games.engine.backgammon.event.GameFinishedEvent;
import ivs.games.engine.backgammon.event.GameStartedEvent;
import ivs.games.engine.backgammon.event.TurnChangedEvent;
import ivs.games.engine.backgammon.state.GameSetup;
import ivs.games.engine.exception.EngineCommandException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.BLACK;
import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.WHITE;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.ADDRESSING;
import static ivs.games.engine.backgammon.state.GamePhase.ABORTED;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_DICE;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_MOVE;
import static ivs.games.engine.backgammon.state.GamePhase.FINISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests complete command sequences using the real long backgammon engine
 * and its default move resolver.
 *
 * <p>Scenarios cover turn transitions, head restrictions, mandatory dice usage,
 * blocked rolls, bearing off, victory and system abort.
 * Earlier immutable snapshots are checked after subsequent commands.</p>
 */
class LongBackgammonEngineIntegrationTest {

    private final LongBackgammonEngine engine = new LongBackgammonEngine();

    /**
     * Verifies an ordinary first turn for either color.
     * One checker leaves the head and uses both dice before play changes sides.
     * Earlier snapshots remain unchanged.
     *
     * @param color the starting color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void ordinaryRollMovesOneCheckerAndPassesTurn(StandardCheckerColor color) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        LongGameState initial = engine.startGame(new GameSetup(color));
        assertEquals(List.of(new GameStartedEvent(color)), initial.getEvents());

        LongGameState rolled = engine.processCommand(initial, new DiceRolled(color, 1, 2));

        assertEquals(List.of(new DiceRolledEvent(color, 1, 2)), rolled.getEvents());
        assertEquals(AWAITING_MOVE, rolled.getGamePhase());
        assertEquals(color, rolled.getActiveColor());
        assertEquals(List.of(1, 2), rolled.getDiceValues());
        assertEquals(List.of(1, 2), rolled.getRemainingDiceValues());
        assertEquals(Map.of(1, Set.of(1, 2)), rolled.getAllowedMoves());
        assertTrue(rolled.getAllowedBearOffs().isEmpty());

        LongGameState moved = engine.processCommand(rolled, new Move(color, 1, 1));

        assertEquals(List.of(new CheckerMovedEvent(color, 1, 2, 1)), moved.getEvents());
        assertEquals(AWAITING_MOVE, moved.getGamePhase());
        assertEquals(List.of(2), moved.getRemainingDiceValues());
        assertEquals(1, moved.getHeadMoves());
        assertEquals(Map.of(2, Set.of(2)), moved.getAllowedMoves());
        assertEquals(14, checkerCount(moved, color, 1));
        assertEquals(1, checkerCount(moved, color, 2));

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(moved, new Move(color, 1, 2)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(moved, new Move(opponent, 2, 2)));

        LongGameState completed = engine.processCommand(moved, new Move(color, 2, 2));

        assertEquals(List.of(
                new CheckerMovedEvent(color, 2, 4, 2),
                new TurnChangedEvent(color, opponent)), completed.getEvents());
        assertTurnPassed(completed, opponent);
        assertEquals(14, checkerCount(completed, color, 1));
        assertEquals(1, checkerCount(completed, color, 4));
        assertEquals(0, checkerCount(completed, color, 2));
        assertFalse(completed.getFirstTurns().get(color));
        assertTrue(completed.getFirstTurns().get(opponent));

        assertEquals(AWAITING_DICE, initial.getGamePhase());
        assertEquals(15, checkerCount(initial, color, 1));
        assertEquals(0, checkerCount(initial, color, 4));
        assertEquals(List.of(1, 2), rolled.getRemainingDiceValues());
        assertEquals(15, checkerCount(rolled, color, 1));
        assertEquals(List.of(2), moved.getRemainingDiceValues());
        assertEquals(1, checkerCount(moved, color, 2));

        LongGameState opponentRoll = engine.processCommand(
                completed, new DiceRolled(opponent, 1, 2));

        assertEquals(AWAITING_MOVE, opponentRoll.getGamePhase());
        assertEquals(opponent, opponentRoll.getActiveColor());
        assertEquals(Map.of(1, Set.of(1, 2)), opponentRoll.getAllowedMoves());
    }

    /**
     * Verifies that first-turn doubles of three and four permit
     * two head departures and consumption of all four movement values.
     *
     * @param value the first-turn double value
     */
    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void firstTurnDoubleAllowsTwoHeadDeparturesAndFourMovements(int value) {
        LongGameState initial = engine.startGame(new GameSetup(WHITE));
        LongGameState rolled = engine.processCommand(initial, new DiceRolled(WHITE, value, value));

        assertEquals(List.of(value, value, value, value), rolled.getRemainingDiceValues());

        LongGameState first = engine.processCommand(rolled, new Move(WHITE, 1, value));

        assertEquals(1, first.getHeadMoves());
        assertEquals(List.of(value, value, value), first.getRemainingDiceValues());
        assertTrue(first.getAllowedMoves().get(1).contains(value));

        LongGameState second = engine.processCommand(first, new Move(WHITE, 1, value));

        assertEquals(AWAITING_MOVE, second.getGamePhase());
        assertEquals(2, second.getHeadMoves());
        assertEquals(List.of(value, value), second.getRemainingDiceValues());
        assertFalse(second.getAllowedMoves().containsKey(1));
        assertEquals(13, checkerCount(second, WHITE, 1));
        assertEquals(2, checkerCount(second, WHITE, 1 + value));

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(second, new Move(WHITE, 1, value)));

        LongGameState third = engine.processCommand(second, new Move(WHITE, 1 + value, value));

        assertEquals(List.of(value), third.getRemainingDiceValues());

        LongGameState completed = engine.processCommand(third, new Move(WHITE, 1 + value, value));

        assertTurnPassed(completed, BLACK);
        assertEquals(13, checkerCount(completed, WHITE, 1));
        assertEquals(0, checkerCount(completed, WHITE, 1 + value));
        assertEquals(2, checkerCount(completed, WHITE, 1 + 2 * value));
        assertFalse(completed.getFirstTurns().get(WHITE));
        assertTrue(completed.getFirstTurns().get(BLACK));

        assertEquals(15, checkerCount(initial, WHITE, 1));
        assertEquals(2, second.getHeadMoves());
        assertEquals(List.of(value, value), second.getRemainingDiceValues());
    }

    /**
     * Verifies the first-turn double-six exception.
     * Two checkers leave the head, but neither can move onto the opposing head.
     * The unused movement values are discarded when no legal action remains.
     */
    @Test
    void firstTurnDoubleSixPassesTurnAfterTwoHeadDepartures() {
        LongGameState initial = engine.startGame(new GameSetup(WHITE));
        LongGameState rolled = engine.processCommand(initial, new DiceRolled(WHITE, 6, 6));

        LongGameState first = engine.processCommand(rolled, new Move(WHITE, 1, 6));

        assertEquals(Map.of(1, Set.of(6)), first.getAllowedMoves());
        assertEquals(List.of(6, 6, 6), first.getRemainingDiceValues());
        assertEquals(1, first.getHeadMoves());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(first, new Move(WHITE, 7, 6)));

        LongGameState completed = engine.processCommand(first, new Move(WHITE, 1, 6));

        assertTurnPassed(completed, BLACK);
        assertEquals(13, checkerCount(completed, WHITE, 1));
        assertEquals(2, checkerCount(completed, WHITE, 7));
        assertEquals(15, checkerCount(completed, BLACK, 1));
        assertFalse(completed.getFirstTurns().get(WHITE));
    }

    /**
     * Verifies that the double-three head exception no longer applies
     * after the active side has completed its first turn.
     */
    @Test
    void laterDoubleAllowsOnlyOneHeadDeparture() {
        LongGameState original = engine.startGame(new GameSetup(WHITE))
                .toBuilder()
                .firstTurns(Map.of(WHITE, false, BLACK, false))
                .build();

        LongGameState rolled = engine.processCommand(original, new DiceRolled(WHITE, 3, 3));
        LongGameState first = engine.processCommand(rolled, new Move(WHITE, 1, 3));

        assertEquals(1, first.getHeadMoves());
        assertEquals(Map.of(4, Set.of(3)), first.getAllowedMoves());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(first, new Move(WHITE, 1, 3)));

        LongGameState second = engine.processCommand(first, new Move(WHITE, 4, 3));

        assertEquals(Map.of(7, Set.of(3)), second.getAllowedMoves());

        LongGameState completed = engine.processCommand(second, new Move(WHITE, 7, 3));

        assertTurnPassed(completed, BLACK);
        assertEquals(14, checkerCount(completed, WHITE, 1));
        assertEquals(1, checkerCount(completed, WHITE, 10));
    }

    /**
     * Verifies that a completely blocked roll automatically completes the turn.
     * First-turn status is updated even though no checker has moved.
     */
    @Test
    void blockedRollPassesTurnWithoutChangingBoard() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 1))
                .setState(Collections.nCopies(15, StandardChecker.WHITE_MAN));
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 2))
                .setState(Collections.nCopies(7, StandardChecker.BLACK_MAN));
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 3))
                .setState(Collections.nCopies(8, StandardChecker.BLACK_MAN));

        LongGameState original = position(board, WHITE, true);
        LongGameState completed = engine.processCommand(original, new DiceRolled(WHITE, 1, 2));

        assertEquals(List.of(
                new DiceRolledEvent(WHITE, 1, 2),
                new TurnChangedEvent(WHITE, BLACK)), completed.getEvents());
        assertTurnPassed(completed, BLACK);
        assertFalse(completed.getFirstTurns().get(WHITE));
        assertTrue(completed.getFirstTurns().get(BLACK));
        assertEquals(original.getBoard().getCells(), completed.getBoard().getCells());
        assertEquals(original.getBoard().getBars(), completed.getBoard().getBars());
        assertEquals(original.getBoard().getBorneOff(), completed.getBoard().getBorneOff());
        assertTrue(original.getFirstTurns().get(WHITE));
    }

    /**
     * Verifies that moving before bearing off is mandatory when it permits
     * both dice to be used instead of only one.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void mandatoryDiceUsageRequiresMovementBeforeFinalBearOff(StandardCheckerColor color) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        StandardBoard<StandardChecker> board = new StandardBoard<>();

        board.getCell(ADDRESSING.toBoardIndex(color, 23)).push(man(color));
        board.getBorneOff(color).setState(Collections.nCopies(14, man(color)));
        board.getCell(ADDRESSING.toBoardIndex(opponent, 1))
                .setState(Collections.nCopies(15, man(opponent)));

        LongGameState original = position(board, color, false);
        LongGameState rolled = engine.processCommand(original, new DiceRolled(color, 1, 2));

        assertEquals(Map.of(23, Set.of(1)), rolled.getAllowedMoves());
        assertTrue(rolled.getAllowedBearOffs().isEmpty());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(rolled, new BearOff(color, 23, 2)));

        LongGameState moved = engine.processCommand(rolled, new Move(color, 23, 1));

        assertEquals(AWAITING_MOVE, moved.getGamePhase());
        assertEquals(List.of(2), moved.getRemainingDiceValues());
        assertTrue(moved.getAllowedMoves().isEmpty());
        assertEquals(Map.of(24, Set.of(2)), moved.getAllowedBearOffs());

        LongGameState finished = engine.processCommand(moved, new BearOff(color, 24, 2));

        assertEquals(List.of(
                new CheckerBorneOffEvent(color, 24, 2),
                new GameFinishedEvent(color)), finished.getEvents());
        assertEquals(FINISHED, finished.getGamePhase());
        assertEquals(color, finished.getWinner());
        assertEquals(color, finished.getActiveColor());
        assertEquals(15, finished.getBoard().getBorneOff().get(color).size());
        assertTrue(finished.getRemainingDiceValues().isEmpty());
        assertTrue(finished.getAllowedMoves().isEmpty());
        assertTrue(finished.getAllowedBearOffs().isEmpty());
        assertEquals(0, checkerCount(finished, color, 24));

        assertEquals(14, original.getBoard().getBorneOff().get(color).size());
        assertEquals(1, checkerCount(original, color, 23));
        assertEquals(1, checkerCount(moved, color, 24));
        assertNull(original.getWinner());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(finished, new DiceRolled(color, 1, 2)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(finished, new Abort()));
    }

    /**
     * Verifies that the higher playable die is mandatory when
     * only one checker action can be performed.
     */
    @Test
    void onlyOnePlayableActionRequiresHigherDie() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 24)).push(StandardChecker.WHITE_MAN);
        board.getBorneOff(WHITE).setState(Collections.nCopies(14, StandardChecker.WHITE_MAN));
        board.getCell(ADDRESSING.toBoardIndex(BLACK, 1))
                .setState(Collections.nCopies(15, StandardChecker.BLACK_MAN));

        LongGameState original = position(board, WHITE, false);
        LongGameState rolled = engine.processCommand(original, new DiceRolled(WHITE, 1, 2));

        assertTrue(rolled.getAllowedMoves().isEmpty());
        assertEquals(Map.of(24, Set.of(2)), rolled.getAllowedBearOffs());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(rolled, new BearOff(WHITE, 24, 1)));

        LongGameState finished = engine.processCommand(rolled, new BearOff(WHITE, 24, 2));

        assertEquals(FINISHED, finished.getGamePhase());
        assertEquals(WHITE, finished.getWinner());
        assertEquals(15, finished.getBoard().getBorneOff().get(WHITE).size());
        assertEquals(List.of(1), finished.getRemainingDiceValues());
        assertTrue(finished.getAllowedMoves().isEmpty());
        assertTrue(finished.getAllowedBearOffs().isEmpty());
    }

    /**
     * Verifies that bearing off consumes one occurrence of a double at a time.
     * Victory immediately ends the game even when unused movement values remain.
     */
    @Test
    void doubleBearOffFinishesGameBeforeAllValuesAreConsumed() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 24))
                .setState(Collections.nCopies(2, StandardChecker.WHITE_MAN));
        board.getBorneOff(WHITE).setState(Collections.nCopies(13, StandardChecker.WHITE_MAN));
        board.getCell(ADDRESSING.toBoardIndex(BLACK, 1))
                .setState(Collections.nCopies(15, StandardChecker.BLACK_MAN));

        LongGameState original = position(board, WHITE, false);
        LongGameState rolled = engine.processCommand(original, new DiceRolled(WHITE, 1, 1));

        assertEquals(List.of(1, 1, 1, 1), rolled.getRemainingDiceValues());
        assertEquals(Map.of(24, Set.of(1)), rolled.getAllowedBearOffs());

        LongGameState first = engine.processCommand(rolled, new BearOff(WHITE, 24, 1));

        assertEquals(AWAITING_MOVE, first.getGamePhase());
        assertEquals(List.of(1, 1, 1), first.getRemainingDiceValues());
        assertEquals(14, first.getBoard().getBorneOff().get(WHITE).size());
        assertEquals(Map.of(24, Set.of(1)), first.getAllowedBearOffs());
        assertNull(first.getWinner());

        LongGameState finished = engine.processCommand(first, new BearOff(WHITE, 24, 1));

        assertEquals(FINISHED, finished.getGamePhase());
        assertEquals(WHITE, finished.getActiveColor());
        assertEquals(WHITE, finished.getWinner());
        assertEquals(List.of(1, 1), finished.getRemainingDiceValues());
        assertEquals(15, finished.getBoard().getBorneOff().get(WHITE).size());
        assertTrue(finished.getAllowedMoves().isEmpty());
        assertTrue(finished.getAllowedBearOffs().isEmpty());

        assertEquals(13, original.getBoard().getBorneOff().get(WHITE).size());
        assertEquals(14, first.getBoard().getBorneOff().get(WHITE).size());
    }

    /**
     * Verifies that system abort after a real movement preserves
     * the current board, remaining dice and long-game properties.
     * No further command can continue the aborted game.
     */
    @Test
    void abortDuringTurnPreservesPositionAndPreventsFurtherActions() {
        LongGameState initial = engine.startGame(new GameSetup(WHITE));
        LongGameState rolled = engine.processCommand(initial, new DiceRolled(WHITE, 1, 2));
        LongGameState moved = engine.processCommand(rolled, new Move(WHITE, 1, 1));

        LongGameState aborted = engine.processCommand(moved, new Abort());

        assertEquals(List.of(new GameAbortedEvent()), aborted.getEvents());
        assertNotSame(moved, aborted);
        assertEquals(ABORTED, aborted.getGamePhase());
        assertEquals(AWAITING_MOVE, moved.getGamePhase());
        assertSame(moved.getBoard(), aborted.getBoard());
        assertSame(moved.getActiveColor(), aborted.getActiveColor());
        assertSame(moved.getDiceValues(), aborted.getDiceValues());
        assertSame(moved.getRemainingDiceValues(), aborted.getRemainingDiceValues());
        assertSame(moved.getAllowedMoves(), aborted.getAllowedMoves());
        assertSame(moved.getAllowedBearOffs(), aborted.getAllowedBearOffs());
        assertSame(moved.getFirstTurns(), aborted.getFirstTurns());
        assertEquals(moved.getHeadMoves(), aborted.getHeadMoves());
        assertNull(aborted.getWinner());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(aborted, new Move(WHITE, 2, 2)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(aborted, new BearOff(WHITE, 24, 2)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(aborted, new DiceRolled(WHITE, 3, 5)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(aborted, new Abort()));

        assertEquals(14, checkerCount(aborted, WHITE, 1));
        assertEquals(1, checkerCount(aborted, WHITE, 2));
        assertEquals(List.of(2), aborted.getRemainingDiceValues());
        assertEquals(1, aborted.getHeadMoves());
    }

    /*
     * Creates an immutable scenario state awaiting dice.
     * The board arrangement is supplied explicitly; legal options are
     * calculated by the real resolver when the roll is processed.
     */
    private static LongGameState position(
            StandardBoard<StandardChecker> board,
            StandardCheckerColor color,
            boolean firstTurn) {

        return LongGameState.builder()
                .board(board.getState())
                .activeColor(color)
                .gamePhase(AWAITING_DICE)
                .diceValues(List.of())
                .remainingDiceValues(List.of())
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .winner(null)
                .headMoves(0)
                .firstTurns(Map.of(WHITE, firstTurn, BLACK, firstTurn))
                .build();
    }

    /*
     * Checks all turn-reset properties together.
     * First-turn flags are checked separately by each scenario.
     */
    private static void assertTurnPassed(LongGameState state, StandardCheckerColor nextColor) {
        assertEquals(nextColor, state.getActiveColor());
        assertEquals(AWAITING_DICE, state.getGamePhase());
        assertTrue(state.getDiceValues().isEmpty());
        assertTrue(state.getRemainingDiceValues().isEmpty());
        assertTrue(state.getAllowedMoves().isEmpty());
        assertTrue(state.getAllowedBearOffs().isEmpty());
        assertEquals(0, state.getHeadMoves());
        assertNull(state.getWinner());
    }

    /*
     * Converts a side-relative route point to its physical board index
     * and returns the corresponding stack size.
     */
    private static int checkerCount(
            LongGameState state,
            StandardCheckerColor color,
            int point) {

        int index = ADDRESSING.toBoardIndex(color, point);
        return state.getBoard().getCells().get(index).size();
    }

    /*
     * Selects the ordinary checker constant for scenario construction.
     */
    private static StandardChecker man(StandardCheckerColor color) {
        return color == WHITE ? StandardChecker.WHITE_MAN : StandardChecker.BLACK_MAN;
    }
}
