package ivs.games.engine.backgammon;

import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardChecker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.command.Abort;
import ivs.games.engine.backgammon.command.BearOff;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Enter;
import ivs.games.engine.backgammon.command.GameAction;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.longgame.LongGameState;
import ivs.games.engine.backgammon.shortgame.ShortGameState;
import ivs.games.engine.backgammon.state.GamePhase;
import ivs.games.engine.backgammon.state.GameSetup;
import ivs.games.engine.backgammon.state.GameState;
import ivs.games.engine.exception.EngineCommandException;
import ivs.games.engine.exception.EngineException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.BLACK;
import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.WHITE;
import static ivs.games.engine.backgammon.state.GamePhase.ABORTED;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_DICE;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_MOVE;
import static ivs.games.engine.backgammon.state.GamePhase.FINISHED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests common setup validation, player action validation, dispatch,
 * system abort processing and movement pool creation.
 *
 * <p>Test handlers record their arguments without applying variant rules.
 * The default abort handler is retained for state preservation tests.</p>
 */
@SuppressWarnings("ThrowableNotThrown")
class AbstractBackgammonEngineTest {

    private final TestEngine engine = new TestEngine();

    /**
     * Checks that either starting color is accepted and the setup
     * reaches the initialization handler unchanged.
     *
     * @param color the starting color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void startGamePassesSetupToInitialStateHandler(StandardCheckerColor color) {
        GameSetup setup = new GameSetup(color);
        GameState expected = state(color, AWAITING_DICE);
        engine.nextState = expected;

        GameState actual = engine.startGame(setup);

        assertSame(expected, actual);
        assertSame(setup, engine.receivedSetup);
    }

    /**
     * Checks that a missing starting color prevents initialization
     * and is wrapped as an engine failure.
     */
    @Test
    void startGameRejectsNullStartingColorBeforeCallingHandler() {
        EngineException exception = assertThrows(
                EngineException.class, () -> engine.startGame(new GameSetup(null)));

        assertInstanceOf(NullPointerException.class, exception.getCause());
        assertNull(engine.receivedSetup);
    }

    /**
     * Checks that entry reaches an overridden entry handler.
     */
    @Test
    void enterIsDispatchedToItsHandler() {
        assertDispatch(new Enter(WHITE, 5), AWAITING_MOVE);
    }

    /**
     * Checks that player actions without a color are rejected
     * before processing.
     */
    @Test
    void rejectsNullPlayerActionColorsBeforeCallingHandler() {
        assertRejected(state(WHITE, AWAITING_DICE), new DiceRolled(null, 3, 5));
        assertRejected(state(WHITE, AWAITING_MOVE), new Move(null, 1, 1));
        assertRejected(state(WHITE, AWAITING_MOVE), new BearOff(null, 24, 1));
        assertRejected(state(WHITE, AWAITING_MOVE), new Enter(null, 5));
    }

    /**
     * Checks that player actions cannot be submitted during the other side's turn.
     */
    @Test
    void rejectsPlayerActionsFromOtherSideBeforeCallingHandler() {
        assertRejected(state(WHITE, AWAITING_DICE), new DiceRolled(BLACK, 3, 5));
        assertRejected(state(WHITE, AWAITING_MOVE), new Move(BLACK, 1, 1));
        assertRejected(state(WHITE, AWAITING_MOVE), new BearOff(BLACK, 24, 1));
        assertRejected(state(WHITE, AWAITING_MOVE), new Enter(BLACK, 5));
    }

    /**
     * Checks that a player action cannot match a state with no active color.
     */
    @Test
    void rejectsNullStateColorForPlayerAction() {
        assertRejected(state(null, AWAITING_DICE), new DiceRolled(WHITE, 3, 5));
    }

    /**
     * Checks that dice results require the dice phase.
     */
    @Test
    void rejectsDiceRolledDuringMovementPhase() {
        EngineCommandException exception = assertRejected(
                state(WHITE, AWAITING_MOVE), new DiceRolled(WHITE, 3, 5));

        assertEquals("Action requires phase AWAITING_DICE, but current phase is AWAITING_MOVE",
                exception.getMessage());
    }

    /**
     * Checks that all movement actions require the movement phase.
     */
    @Test
    void rejectsMovementActionsDuringDicePhase() {
        GameState original = state(WHITE, AWAITING_DICE);
        String expected = "Action requires phase AWAITING_MOVE, but current phase is AWAITING_DICE";

        assertEquals(expected, assertRejected(original, new Move(WHITE, 1, 1)).getMessage());
        assertEquals(expected, assertRejected(original, new BearOff(WHITE, 24, 1)).getMessage());
        assertEquals(expected, assertRejected(original, new Enter(WHITE, 5)).getMessage());
    }

    /**
     * Checks that terminal phases reject every supported action,
     * including system abort, before any processing handler is called.
     *
     * @param phase the terminal phase
     */
    @ParameterizedTest
    @EnumSource(value = GamePhase.class, names = {"FINISHED", "ABORTED"})
    void terminalPhasesRejectAllSupportedActions(GamePhase phase) {
        GameState original = state(WHITE, phase);
        String expected = "Game is already %s; no further actions are permitted".formatted(phase);

        assertEquals(expected, assertRejected(original, new DiceRolled(WHITE, 3, 5)).getMessage());
        assertEquals(expected, assertRejected(original, new Move(WHITE, 1, 1)).getMessage());
        assertEquals(expected, assertRejected(original, new BearOff(WHITE, 24, 1)).getMessage());
        assertEquals(expected, assertRejected(original, new Enter(WHITE, 5)).getMessage());
        assertEquals(expected, assertRejected(original, new Abort()).getMessage());

        assertEquals(phase, original.getGamePhase());
    }

    /**
     * Checks that terminal phase validation precedes action color validation.
     *
     * @param phase the terminal phase
     */
    @ParameterizedTest
    @EnumSource(value = GamePhase.class, names = {"FINISHED", "ABORTED"})
    void terminalPhaseIsCheckedBeforeActionColor(GamePhase phase) {
        EngineCommandException exception = assertRejected(
                state(WHITE, phase), new DiceRolled(null, 3, 5));

        assertEquals("Game is already %s; no further actions are permitted".formatted(phase),
                exception.getMessage());
    }

    /**
     * Checks that invalid first-die values are rejected.
     *
     * @param value the invalid first-die value
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 7, Integer.MAX_VALUE})
    void rejectsInvalidFirstDieValue(int value) {
        EngineCommandException exception = assertRejected(
                state(WHITE, AWAITING_DICE), new DiceRolled(WHITE, value, 3));

        assertEquals("First die value must be between 1 and 6: " + value, exception.getMessage());
    }

    /**
     * Checks that invalid second-die values are rejected.
     *
     * @param value the invalid second-die value
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 7, Integer.MAX_VALUE})
    void rejectsInvalidSecondDieValue(int value) {
        EngineCommandException exception = assertRejected(
                state(WHITE, AWAITING_DICE), new DiceRolled(WHITE, 3, value));

        assertEquals("Second die value must be between 1 and 6: " + value, exception.getMessage());
    }

    /**
     * Checks both inclusive dice boundaries and dispatch to the dice handler.
     *
     * @param first  the first die value
     * @param second the second die value
     */
    @ParameterizedTest
    @CsvSource({"1, 1", "1, 6", "6, 1", "6, 6"})
    void acceptsDiceBoundaryValues(int first, int second) {
        assertDispatch(new DiceRolled(WHITE, first, second), AWAITING_DICE);
    }

    /**
     * Checks that moves and bearing-off actions reject invalid selected die values.
     *
     * @param value the invalid die value
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 7, Integer.MAX_VALUE})
    void rejectsInvalidSelectedDieValues(int value) {
        GameState original = state(WHITE, AWAITING_MOVE);
        String expected = "Die value must be between 1 and 6: " + value;

        assertEquals(expected, assertRejected(original, new Move(WHITE, 1, value)).getMessage());
        assertEquals(expected, assertRejected(original, new BearOff(WHITE, 24, value)).getMessage());
    }

    /**
     * Checks source route bounds for moves and bearing-off actions.
     *
     * @param point the invalid source route point
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 25, Integer.MAX_VALUE})
    void rejectsInvalidSourceRoutePoints(int point) {
        GameState original = state(WHITE, AWAITING_MOVE);
        String expected = "Source route point must be between 1 and 24: " + point;

        assertEquals(expected, assertRejected(original, new Move(WHITE, point, 1)).getMessage());
        assertEquals(expected, assertRejected(original, new BearOff(WHITE, point, 1)).getMessage());
    }

    /**
     * Checks destination route bounds for bar entry.
     *
     * @param point the invalid destination route point
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 25, Integer.MAX_VALUE})
    void rejectsInvalidEnterDestination(int point) {
        EngineCommandException exception = assertRejected(
                state(WHITE, AWAITING_MOVE), new Enter(WHITE, point));

        assertEquals("Destination route point must be between 1 and 24: " + point,
                exception.getMessage());
    }

    /**
     * Checks valid source, destination and selected die boundaries
     * while verifying dispatch to the movement handler.
     *
     * @param from      the source route point
     * @param diceValue the selected die value
     */
    @ParameterizedTest
    @CsvSource({"1, 1", "1, 6", "23, 1", "18, 6"})
    void acceptsMoveBoundaryValues(int from, int diceValue) {
        assertDispatch(new Move(WHITE, from, diceValue), AWAITING_MOVE);
    }

    /**
     * Checks both route and die boundaries for bearing off.
     * Variant rules are not evaluated by the recording handler.
     *
     * @param from      the source route point
     * @param diceValue the selected die value
     */
    @ParameterizedTest
    @CsvSource({"1, 1", "1, 6", "24, 1", "24, 6"})
    void acceptsBearOffBoundaryValues(int from, int diceValue) {
        assertDispatch(new BearOff(WHITE, from, diceValue), AWAITING_MOVE);
    }

    /**
     * Checks both route boundaries for entry.
     *
     * @param point the destination route point
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 24})
    void acceptsEnterBoundaryPoints(int point) {
        assertDispatch(new Enter(WHITE, point), AWAITING_MOVE);
    }

    /**
     * Checks that a valid source and die cannot produce a destination
     * beyond the last route point.
     *
     * @param from      the source route point
     * @param diceValue the selected die value
     */
    @ParameterizedTest
    @CsvSource({"24, 1", "19, 6"})
    void rejectsMoveBeyondRoute(int from, int diceValue) {
        EngineCommandException exception = assertRejected(
                state(WHITE, AWAITING_MOVE), new Move(WHITE, from, diceValue));

        assertEquals("Destination route point must be between 1 and 24: " + (from + diceValue),
                exception.getMessage());
    }

    /**
     * Checks that an unsupported action is rejected before dispatch.
     */
    @Test
    void rejectsUnsupportedActionBeforeCallingHandler() {
        GameAction action = mock(GameAction.class);
        when(action.getActiveColor()).thenReturn(WHITE);

        EngineCommandException exception = assertRejected(state(WHITE, AWAITING_MOVE), action);

        assertEquals("Unsupported action type: " + action.getClass().getSimpleName(),
                exception.getMessage());
    }

    /**
     * Checks the dispatch fallback independently of preliminary validation.
     */
    @Test
    void doProcessCommandRejectsUnsupportedAction() {
        GameAction action = mock(GameAction.class);

        EngineCommandException exception = assertThrows(
                EngineCommandException.class,
                () -> engine.doProcessCommand(state(WHITE, AWAITING_MOVE), action));

        assertEquals("Unsupported action type: " + action.getClass().getSimpleName(),
                exception.getMessage());
        assertNull(engine.receivedAction);
        assertNull(engine.receivedState);
    }

    /**
     * Checks that the default entry handler rejects variants without bar entry.
     */
    @Test
    void defaultEnterHandlerRejectsBarEntry() {
        TestEngineWithoutEntry withoutEntry = new TestEngineWithoutEntry();

        EngineCommandException exception = assertThrows(
                EngineCommandException.class,
                () -> withoutEntry.processCommand(
                        state(WHITE, AWAITING_MOVE), new Enter(WHITE, 5)));

        assertEquals("Bar entry is not supported by this game variant", exception.getMessage());
    }

    /**
     * Checks that system abort is accepted in each non-terminal phase
     * and changes only the phase without modifying the supplied state.
     *
     * @param phase the non-terminal phase
     */
    @ParameterizedTest
    @EnumSource(value = GamePhase.class, names = {"AWAITING_DICE", "AWAITING_MOVE"})
    void abortPreservesBaseStateInEachNonTerminalPhase(GamePhase phase) {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(0).push(StandardChecker.WHITE_MAN);
        board.getBorneOff(BLACK).push(StandardChecker.BLACK_MAN);

        GameState original = GameState.builder()
                .board(board.getState())
                .activeColor(BLACK)
                .gamePhase(phase)
                .diceValues(phase == AWAITING_MOVE ? List.of(3, 5) : List.of())
                .remainingDiceValues(phase == AWAITING_MOVE ? List.of(5) : List.of())
                .allowedMoves(phase == AWAITING_MOVE ? Map.of(1, Set.of(5)) : Map.of())
                .allowedBearOffs(Map.of())
                .winner(null)
                .build();

        GameState actual = engine.processCommand(original, new Abort());

        assertNotSame(original, actual);
        assertEquals(GameState.class, actual.getClass());
        assertEquals(ABORTED, actual.getGamePhase());
        assertEquals(phase, original.getGamePhase());
        assertPreservedBaseState(original, actual);
        assertNull(engine.receivedAction);
        assertNull(engine.receivedState);
    }

    /**
     * Checks that system abort does not require the state to have an active color.
     */
    @Test
    void abortDoesNotRequireActiveColor() {
        GameState original = state(null, AWAITING_DICE);

        GameState actual = engine.processCommand(original, new Abort());

        assertEquals(ABORTED, actual.getGamePhase());
        assertNull(actual.getActiveColor());
        assertEquals(AWAITING_DICE, original.getGamePhase());
    }

    /**
     * Checks that validation does not read the system abort action's color.
     */
    @Test
    void abortValidationDoesNotReadActionColor() {
        Abort action = new Abort() {
            @Override
            public StandardCheckerColor getActiveColor() {
                return fail("System abort color must not be read");
            }
        };

        engine.validateCommand(state(WHITE, AWAITING_MOVE), action);
    }

    /**
     * Checks preservation of concrete state types and long-game fields
     * by the inherited abort handler.
     *
     * @param original the concrete variant state
     */
    @ParameterizedTest
    @MethodSource("variantStates")
    void abortPreservesVariantStateTypeAndProperties(GameState original) {
        GameState actual = engine.processCommand(original, new Abort());

        assertNotSame(original, actual);
        assertEquals(original.getClass(), actual.getClass());
        assertEquals(ABORTED, actual.getGamePhase());
        assertEquals(AWAITING_MOVE, original.getGamePhase());
        assertPreservedBaseState(original, actual);

        if (original instanceof LongGameState longOriginal) {
            LongGameState longActual = assertInstanceOf(LongGameState.class, actual);

            assertEquals(longOriginal.getHeadMoves(), longActual.getHeadMoves());
            assertSame(longOriginal.getFirstTurns(), longActual.getFirstTurns());
        } else if (original instanceof ShortGameState shortOriginal) {
            ShortGameState shortActual = assertInstanceOf(ShortGameState.class, actual);

            assertSame(shortOriginal.getAllowedEntries(), shortActual.getAllowedEntries());
        }
    }

    /**
     * Checks that abort dispatch forwards its original arguments
     * to an overridden handler.
     */
    @Test
    void abortIsDispatchedToOverriddenHandler() {
        TestEngineWithoutEntry recordingEngine = new TestEngineWithoutEntry() {
            @Override
            protected GameState processAbort(GameState gameState, Abort action) {
                return recordAction(gameState, action);
            }
        };

        GameState original = state(WHITE, AWAITING_DICE);
        GameState expected = state(WHITE, ABORTED);
        Abort action = new Abort();
        recordingEngine.nextState = expected;

        assertSame(expected, recordingEngine.processCommand(original, action));
        assertSame(original, recordingEngine.receivedState);
        assertSame(action, recordingEngine.receivedAction);
    }

    /**
     * Checks that a non-double preserves the original roll order.
     */
    @Test
    void movementPoolPreservesDifferentDiceValuesAndTheirOrder() {
        assertEquals(List.of(3, 5), engine.movementPool(new DiceRolled(WHITE, 3, 5)));
        assertEquals(List.of(5, 3), engine.movementPool(new DiceRolled(WHITE, 5, 3)));
    }

    /**
     * Checks that every supported double produces four equal movement values.
     *
     * @param value the value appearing on both dice
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void movementPoolExpandsDoubles(int value) {
        assertEquals(List.of(value, value, value, value),
                engine.movementPool(new DiceRolled(WHITE, value, value)));
    }

    /**
     * Checks that both regular and double movement pools are unmodifiable.
     */
    @Test
    void movementPoolIsUnmodifiable() {
        List<Integer> regular = engine.movementPool(new DiceRolled(WHITE, 3, 5));
        List<Integer> doubled = engine.movementPool(new DiceRolled(WHITE, 4, 4));

        assertThrows(UnsupportedOperationException.class, () -> regular.add(1));
        assertThrows(UnsupportedOperationException.class, () -> doubled.set(0, 1));
    }

    /*
     * Verifies dispatch arguments and the handler result.
     * The recording handler deliberately performs no real game transition.
     */
    private void assertDispatch(GameAction action, GamePhase phase) {
        engine.receivedState = null;
        engine.receivedAction = null;

        GameState original = state(action.getActiveColor(), phase);
        GameState expected = state(action.getActiveColor(), FINISHED);
        engine.nextState = expected;

        GameState actual = engine.processCommand(original, action);

        assertSame(expected, actual);
        assertSame(original, engine.receivedState);
        assertSame(action, engine.receivedAction);
        assertEquals(phase, original.getGamePhase());
    }

    /*
     * Verifies rejection before dispatch and returns the exception
     * for additional message checks.
     */
    private EngineCommandException assertRejected(GameState original, GameAction action) {
        engine.receivedAction = null;
        engine.receivedState = null;

        EngineCommandException exception = assertThrows(
                EngineCommandException.class, () -> engine.processCommand(original, action));

        assertNull(engine.receivedAction);
        assertNull(engine.receivedState);
        return exception;
    }

    /*
     * Checks every base property other than the phase.
     * Immutable referenced data should be reused by the state builder.
     */
    private static void assertPreservedBaseState(GameState original, GameState actual) {
        assertSame(original.getBoard(), actual.getBoard());
        assertSame(original.getActiveColor(), actual.getActiveColor());
        assertSame(original.getDiceValues(), actual.getDiceValues());
        assertSame(original.getRemainingDiceValues(), actual.getRemainingDiceValues());
        assertSame(original.getAllowedMoves(), actual.getAllowedMoves());
        assertSame(original.getAllowedBearOffs(), actual.getAllowedBearOffs());
        assertSame(original.getWinner(), actual.getWinner());
    }

    /*
     * Creates a minimal state for common validation and dispatch.
     * Board contents are unnecessary for these tests.
     */
    private static GameState state(StandardCheckerColor color, GamePhase phase) {
        return GameState.builder()
                .activeColor(color)
                .gamePhase(phase)
                .diceValues(List.of())
                .remainingDiceValues(List.of())
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .build();
    }

    /*
     * Supplies actual variant states with non-empty base and variant-specific data.
     * This exercises the Lombok builders used by production state classes.
     */
    private static Stream<GameState> variantStates() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(0).push(StandardChecker.WHITE_MAN);
        board.getBorneOff(BLACK).push(StandardChecker.BLACK_MAN);

        LongGameState longState = LongGameState.builder()
                .board(board.getState())
                .activeColor(WHITE)
                .gamePhase(AWAITING_MOVE)
                .diceValues(List.of(6, 6))
                .remainingDiceValues(List.of(6, 6))
                .allowedMoves(Map.of(1, Set.of(6)))
                .allowedBearOffs(Map.of(24, Set.of(6)))
                .winner(null)
                .headMoves(2)
                .firstTurns(Map.of(WHITE, true, BLACK, false))
                .build();

        ShortGameState shortState = ShortGameState.builder()
                .board(board.getState())
                .activeColor(BLACK)
                .gamePhase(AWAITING_MOVE)
                .diceValues(List.of(3, 5))
                .remainingDiceValues(List.of(5))
                .allowedMoves(Map.of(7, Set.of(5)))
                .allowedBearOffs(Map.of(24, Set.of(5)))
                .allowedEntries(Map.of(5, Set.of(5)))
                .winner(null)
                .build();

        return Stream.of(longState, shortState);
    }

    /*
     * Records initialization and player action handlers.
     * Retains the default entry and abort implementations.
     */
    private static class TestEngineWithoutEntry extends AbstractBackgammonEngine<GameState> {

        GameSetup receivedSetup;
        GameState receivedState;
        GameAction receivedAction;
        GameState nextState;

        @Override
        protected GameState createInitialState(GameSetup initialSetup) {
            receivedSetup = initialSetup;
            return nextState;
        }

        @Override
        protected GameState processDiceRolled(GameState gameState, DiceRolled action) {
            return recordAction(gameState, action);
        }

        @Override
        protected GameState processMove(GameState gameState, Move action) {
            return recordAction(gameState, action);
        }

        @Override
        protected GameState processBearOff(GameState gameState, BearOff action) {
            return recordAction(gameState, action);
        }

        /*
         * Stores handler arguments and supplies a predefined result.
         */
        protected GameState recordAction(GameState state, GameAction action) {
            receivedState = state;
            receivedAction = action;
            return nextState;
        }

        /*
         * Exposes the protected movement pool helper.
         */
        List<Integer> movementPool(DiceRolled action) {
            return createMovementPool(action);
        }
    }

    /*
     * Adds a recording entry handler while preserving default abort behavior.
     */
    private static final class TestEngine extends TestEngineWithoutEntry {

        @Override
        protected GameState processEnter(GameState gameState, Enter action) {
            return recordAction(gameState, action);
        }
    }
}
