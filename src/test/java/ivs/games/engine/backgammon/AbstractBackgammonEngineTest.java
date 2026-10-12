package ivs.games.engine.backgammon;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.command.BearOff;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Enter;
import ivs.games.engine.backgammon.command.GameAction;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.state.GamePhase;
import ivs.games.engine.backgammon.state.GameSetup;
import ivs.games.engine.backgammon.state.GameState;
import ivs.games.engine.exception.EngineCommandException;
import ivs.games.engine.exception.EngineException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.BLACK;
import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.WHITE;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_DICE;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_MOVE;
import static ivs.games.engine.backgammon.state.GamePhase.FINISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests common setup validation, action validation, dispatch and movement pool creation.
 *
 * <p>Test handlers record their arguments and return a predefined state without applying game rules.
 */
class AbstractBackgammonEngineTest {

    private final TestEngine engine = new TestEngine();

    /**
     * Checks that either starting color is accepted and the setup reaches the initialization handler unchanged.
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
     * Checks that a missing starting color prevents initialization and is wrapped as an engine failure.
     */
    @Test
    void startGameRejectsNullStartingColorBeforeCallingHandler() {
        EngineException exception = assertThrows(
                EngineException.class, () -> engine.startGame(new GameSetup(null)));

        assertInstanceOf(NullPointerException.class, exception.getCause());
        assertNull(engine.receivedSetup);
    }

    /**
     * Checks that a valid dice-result action reaches its handler while awaiting dice.
     */
    @Test
    void diceRolledIsDispatchedToItsHandler() {
        assertDispatch(new DiceRolled(WHITE, 3, 5), AWAITING_DICE);
    }

    /**
     * Checks that a movement with valid route points reaches its handler while awaiting a movement.
     */
    @Test
    void moveIsDispatchedToItsHandler() {
        assertDispatch(new Move(WHITE, 1, 6), AWAITING_MOVE);
    }

    /**
     * Checks that a bearing-off action with a valid route point and selected die value reaches its handler.
     */
    @Test
    void bearOffIsDispatchedToItsHandler() {
        assertDispatch(new BearOff(WHITE, 24, 1), AWAITING_MOVE);
    }

    /**
     * Checks that an entry action with a valid route point reaches the overridden entry handler.
     */
    @Test
    void enterIsDispatchedToItsHandler() {
        assertDispatch(new Enter(WHITE, 5), AWAITING_MOVE);
    }

    /**
     * Checks that an action without an active color is rejected before processing.
     */
    @Test
    void rejectsNullActionColorBeforeCallingHandler() {
        assertRejected(state(WHITE, AWAITING_DICE), new DiceRolled(null, 3, 5));
    }

    /**
     * Checks that a side cannot submit an action during the other side's turn.
     */
    @Test
    void rejectsActionFromOtherSideBeforeCallingHandler() {
        assertRejected(state(WHITE, AWAITING_DICE), new DiceRolled(BLACK, 3, 5));
    }

    /**
     * Checks that an action cannot match a state with no active color.
     */
    @Test
    void rejectsNullStateColorBeforeCallingHandler() {
        assertRejected(state(null, AWAITING_DICE), new DiceRolled(WHITE, 3, 5));
    }

    /**
     * Checks that dice results are rejected outside the dice phase, including after game completion.
     *
     * @param phase the phase in which a dice result is not permitted
     */
    @ParameterizedTest
    @EnumSource(value = GamePhase.class, names = {"AWAITING_MOVE", "FINISHED"})
    void rejectsDiceRolledInWrongPhase(GamePhase phase) {
        assertRejected(state(WHITE, phase), new DiceRolled(WHITE, 3, 5));
    }

    /**
     * Checks that every supported movement type is rejected outside the movement phase.
     *
     * @param phase the phase in which movements are not permitted
     */
    @ParameterizedTest
    @EnumSource(value = GamePhase.class, names = {"AWAITING_DICE", "FINISHED"})
    void rejectsAllMovementsInWrongPhase(GamePhase phase) {
        GameState state = state(WHITE, phase);

        assertRejected(state, new Move(WHITE, 1, 6));
        assertRejected(state, new BearOff(WHITE, 24, 1));
        assertRejected(state, new Enter(WHITE, 5));
    }

    /**
     * Checks that a missing phase prevents dispatch.
     */
    @Test
    void rejectsNullPhaseBeforeCallingHandler() {
        assertRejected(state(WHITE, null), new DiceRolled(WHITE, 3, 5));
    }

    /**
     * Checks that first-die values outside the supported range are rejected.
     *
     * @param value the invalid first-die value
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 7, Integer.MAX_VALUE})
    void rejectsInvalidFirstDieValue(int value) {
        assertRejected(state(WHITE, AWAITING_DICE), new DiceRolled(WHITE, value, 3));
    }

    /**
     * Checks that second-die values outside the supported range are rejected.
     *
     * @param value the invalid second-die value
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 7, Integer.MAX_VALUE})
    void rejectsInvalidSecondDieValue(int value) {
        assertRejected(state(WHITE, AWAITING_DICE), new DiceRolled(WHITE, 3, value));
    }

    /**
     * Checks that both die values accept the inclusive boundaries of the supported range.
     *
     * @param first  the first die value
     * @param second the second die value
     */
    @ParameterizedTest
    @CsvSource({"1, 1", "1, 6", "6, 1", "6, 6"})
    void acceptsDiceBoundaryValues(int first, int second) {
        GameState state = state(WHITE, AWAITING_DICE);
        engine.nextState = state;
        DiceRolled action = new DiceRolled(WHITE, first, second);

        assertSame(state, engine.processCommand(state, action));
        assertSame(action, engine.receivedAction);
    }

    /**
     * Checks that movement source points outside the inclusive range from one to twenty-four are rejected.
     *
     * @param point the invalid source route point
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 25, Integer.MAX_VALUE})
    void rejectsInvalidMoveSource(int point) {
        assertRejected(state(WHITE, AWAITING_MOVE), new Move(WHITE, point, 5));
    }

    /**
     * Checks that movement destination points outside the inclusive range from one to twenty-four are rejected.
     *
     * @param point the invalid destination route point
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 25, Integer.MAX_VALUE})
    void rejectsInvalidMoveDestination(int point) {
        assertRejected(state(WHITE, AWAITING_MOVE), new Move(WHITE, 5, point));
    }

    /**
     * Checks that a movement cannot have the same source and destination route point.
     */
    @Test
    void rejectsMoveWithEqualPoints() {
        assertRejected(state(WHITE, AWAITING_MOVE), new Move(WHITE, 5, 5));
    }

    /**
     * Checks that bearing-off source points outside the inclusive range from one to twenty-four are rejected.
     *
     * @param point the invalid source route point
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 25, Integer.MAX_VALUE})
    void rejectsInvalidBearOffSource(int point) {
        assertRejected(state(WHITE, AWAITING_MOVE), new BearOff(WHITE, point, 1));
    }

    /**
     * Checks that entry destination points outside the inclusive range from one to twenty-four are rejected.
     *
     * @param point the invalid destination route point
     */
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 25, Integer.MAX_VALUE})
    void rejectsInvalidEnterDestination(int point) {
        assertRejected(state(WHITE, AWAITING_MOVE), new Enter(WHITE, point));
    }

    /**
     * Checks both inclusive route boundaries for every movement action type.
     *
     * <p>These checks concern route point bounds only, not game-specific movement legality.
     *
     * @param point the route point at a supported boundary
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 24})
    void acceptsMovementBoundaryPoints(int point) {
        assertDispatch(new Move(WHITE, point, 5), AWAITING_MOVE);
        assertDispatch(new Move(WHITE, 5, point), AWAITING_MOVE);
        assertDispatch(new BearOff(WHITE, point, 1), AWAITING_MOVE);
        assertDispatch(new Enter(WHITE, point), AWAITING_MOVE);
    }

    /**
     * Checks that an unknown action type is rejected before any processing handler is called.
     */
    @Test
    void rejectsUnsupportedActionBeforeCallingHandler() {
        GameAction action = mock(GameAction.class);
        when(action.getActiveColor()).thenReturn(WHITE);

        assertRejected(state(WHITE, AWAITING_MOVE), action);
    }

    /**
     * Checks that the default entry handler rejects bar entry when a variant does not override it.
     */
    @Test
    void defaultEnterHandlerRejectsBarEntry() {
        TestEngineWithoutEntry engine = new TestEngineWithoutEntry();

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(state(WHITE, AWAITING_MOVE), new Enter(WHITE, 5)));
    }

    /**
     * Checks that a non-double produces two movement values in the original roll order.
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
     * Checks that neither a regular pool nor a double pool permits modification.
     */
    @Test
    void movementPoolIsUnmodifiable() {
        List<Integer> regular = engine.movementPool(new DiceRolled(WHITE, 3, 5));
        List<Integer> doubled = engine.movementPool(new DiceRolled(WHITE, 4, 4));

        assertThrows(UnsupportedOperationException.class, () -> regular.add(1));
        assertThrows(UnsupportedOperationException.class, () -> doubled.set(0, 1));
    }

    /**
     * Checks that dispatch rejects an unsupported action even when preliminary validation is bypassed.
     */
    @Test
    void doProcessCommandRejectsUnsupportedAction() {
        GameAction action = mock(GameAction.class);

        assertThrows(EngineCommandException.class,
                () -> engine.doProcessCommand(state(WHITE, AWAITING_MOVE), action));

        assertNull(engine.receivedAction);
        assertNull(engine.receivedState);
    }

    /**
     * Verifies that processing forwards the original arguments and returns the handler's predefined state.
     *
     * <p>The handler does not implement a real game transition. The finished phase distinguishes its result
     * from the input state.
     *
     * @param action the action to dispatch
     * @param phase  the phase permitting the action
     */
    private void assertDispatch(GameAction action, GamePhase phase) {
        GameState original = state(action.getActiveColor(), phase);
        GameState expected = state(action.getActiveColor(), FINISHED);
        engine.nextState = expected;

        GameState actual = engine.processCommand(original, action);

        assertSame(expected, actual);
        assertSame(original, engine.receivedState);
        assertSame(action, engine.receivedAction);
        assertEquals(phase, original.getGamePhase());
    }

    /**
     * Verifies rule rejection and confirms that no processing handler receives the action.
     *
     * @param state  the state used for validation
     * @param action the action expected to be rejected
     */
    private void assertRejected(GameState state, GameAction action) {
        engine.receivedAction = null;
        engine.receivedState = null;

        assertThrows(EngineCommandException.class, () -> engine.processCommand(state, action));

        assertNull(engine.receivedAction);
        assertNull(engine.receivedState);
    }

    /**
     * Creates a minimal state containing only properties read by common validation.
     *
     * <p>The board is omitted because route point bounds do not depend on its contents.
     *
     * @param color the active color, possibly null for a rejection test
     * @param phase the phase, possibly null for a rejection test
     * @return the test state
     */
    private static GameState state(StandardCheckerColor color, GamePhase phase) {
        return GameState.builder()
                .activeColor(color)
                .gamePhase(phase)
                .diceValues(List.of())
                .remainingDiceValues(List.of())
                .build();
    }

    /**
     * Records calls to initialization and processing handlers without implementing game rules.
     *
     * <p>Retains the default entry handler to test variants that do not support bar entry.
     * Mutable fields belong only to this test fixture.
     */
    private static class TestEngineWithoutEntry extends AbstractBackgammonEngine<GameState> {

        GameSetup receivedSetup;
        GameState receivedState;
        GameAction receivedAction;
        GameState nextState;

        /**
         * Records the setup and returns the predefined state.
         */
        @Override
        protected GameState createInitialState(GameSetup initialSetup) {
            receivedSetup = initialSetup;
            return nextState;
        }

        /**
         * Records dispatch of a dice-result action.
         */
        @Override
        protected GameState processDiceRolled(GameState gameState, DiceRolled action) {
            return recordAction(gameState, action);
        }

        /**
         * Records dispatch of a movement action.
         */
        @Override
        protected GameState processMove(GameState gameState, Move action) {
            return recordAction(gameState, action);
        }

        /**
         * Records dispatch of a bearing-off action.
         */
        @Override
        protected GameState processBearOff(GameState gameState, BearOff action) {
            return recordAction(gameState, action);
        }

        /**
         * Records handler arguments and returns the predefined state.
         *
         * @param state  the state received by the handler
         * @param action the action received by the handler
         * @return the predefined game state
         */
        protected GameState recordAction(GameState state, GameAction action) {
            receivedState = state;
            receivedAction = action;
            return nextState;
        }

        /**
         * Exposes movement pool creation for direct testing.
         *
         * @param action the dice result with supported values
         * @return the movement value pool
         */
        List<Integer> movementPool(DiceRolled action) {
            return createMovementPool(action);
        }
    }

    /**
     * Adds a recording entry handler to test dispatch of all supported action types.
     */
    private static final class TestEngine extends TestEngineWithoutEntry {

        /**
         * Records dispatch of an entry action.
         */
        @Override
        protected GameState processEnter(GameState gameState, Enter action) {
            return recordAction(gameState, action);
        }
    }
}
