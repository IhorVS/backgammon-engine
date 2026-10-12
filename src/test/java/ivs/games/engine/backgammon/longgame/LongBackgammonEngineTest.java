package ivs.games.engine.backgammon.longgame;

import ivs.games.accessories.backgammonboard.standard.StandardChecker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.state.AllowedMove;
import ivs.games.engine.backgammon.state.GameSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.BLACK;
import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.WHITE;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_DICE;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_MOVE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests engine construction, initial state creation and dice roll processing.
 */
class LongBackgammonEngineTest {

    /**
     * Checks that the public constructor creates an engine with the standard resolver.
     */
    @Test
    void createsEngineWithDefaultConstructor() {
        LongBackgammonEngine engine = assertDoesNotThrow(() -> new LongBackgammonEngine());

        assertNotNull(engine);
    }

    /**
     * Checks that the package constructor accepts a supplied resolver without invoking it.
     */
    @Test
    void createsEngineWithSuppliedResolver() {
        LongMoveResolver resolver = mock(LongMoveResolver.class);

        LongBackgammonEngine engine = assertDoesNotThrow(() -> new LongBackgammonEngine(resolver));

        assertNotNull(engine);
        verifyNoInteractions(resolver);
    }

    /**
     * Checks that the package constructor rejects a null resolver.
     */
    @SuppressWarnings("DataFlowIssue")
    @Test
    void rejectsNullResolver() {
        assertThrows(NullPointerException.class, () -> new LongBackgammonEngine(null));
    }

    /**
     * Checks the complete initial position and turn data for either starting side.
     *
     * <p>Expected indices and checker counts are specified independently of implementation constants.
     * Initialization must not invoke the move resolver.
     *
     * @param color the starting side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void createsInitialState(StandardCheckerColor color) {
        LongMoveResolver resolver = mock(LongMoveResolver.class);
        LongBackgammonEngine engine = new LongBackgammonEngine(resolver);

        LongGameState state = engine.createInitialState(new GameSetup(color));
        var cells = state.getBoard().getCells();

        assertEquals(24, cells.size());

        for (int index = 0; index < cells.size(); index++) {
            if (index == 0) {
                assertEquals(Collections.nCopies(15, StandardChecker.WHITE_MAN), cells.get(index));
            } else if (index == 12) {
                assertEquals(Collections.nCopies(15, StandardChecker.BLACK_MAN), cells.get(index));
            } else {
                assertTrue(cells.get(index).isEmpty(), "Cell must be empty: " + index);
            }
        }

        assertEquals(Map.of(WHITE, List.of(), BLACK, List.of()), state.getBoard().getBars());
        assertEquals(Map.of(WHITE, List.of(), BLACK, List.of()), state.getBoard().getBorneOff());

        assertSame(color, state.getActiveColor());
        assertSame(AWAITING_DICE, state.getGamePhase());
        assertEquals(List.of(), state.getDiceValues());
        assertEquals(List.of(), state.getRemainingDiceValues());
        assertEquals(Map.of(), state.getAllowedMoves());
        assertEquals(0, state.getHeadMoves());
        assertEquals(Map.of(WHITE, true, BLACK, true), state.getFirstTurns());
        assertNull(state.getWinner());

        verifyNoInteractions(resolver);
    }

    /**
     * Checks that dice processing supplies an updated movement context to the resolver
     * and stores the resolved movements using side-relative route points.
     *
     * <p>The board and active side remain unchanged, and the original snapshot still awaits dice.
     */
    @Test
    void processesDiceRollAndStoresAllowedMoves() {
        LongMoveResolver resolver = mock(LongMoveResolver.class);
        LongBackgammonEngine engine = new LongBackgammonEngine(resolver);
        LongGameState original = engine.createInitialState(new GameSetup(WHITE));

        List<Integer> movementPool = List.of(3, 5);
        Map<Integer, Set<AllowedMove>> allowedMoves = Map.of(
                1, Set.of(new AllowedMove(4, 3), new AllowedMove(6, 5)));
        when(resolver.resolve(any(MoveContext.class))).thenReturn(allowedMoves);

        LongGameState actual = engine.processDiceRolled(original, new DiceRolled(WHITE, 3, 5));

        ArgumentCaptor<MoveContext> captor = ArgumentCaptor.forClass(MoveContext.class);
        verify(resolver).resolve(captor.capture());
        MoveContext context = captor.getValue();

        assertSame(original.getBoard(), context.getBoard());
        assertSame(WHITE, context.getActiveColor());
        assertEquals(List.of(3, 5), context.getDiceValues());
        assertEquals(movementPool, context.getMovementPool());
        assertEquals(0, context.getHeadMoves());
        assertTrue(context.isFirstTurn());

        assertSame(allowedMoves, actual.getAllowedMoves());
        assertSame(AWAITING_MOVE, actual.getGamePhase());
        assertSame(WHITE, actual.getActiveColor());
        assertSame(original.getBoard(), actual.getBoard());
        assertEquals(List.of(3, 5), actual.getDiceValues());
        assertEquals(movementPool, actual.getRemainingDiceValues());
        assertEquals(original.getHeadMoves(), actual.getHeadMoves());
        assertEquals(original.getFirstTurns(), actual.getFirstTurns());
        assertNull(actual.getWinner());

        assertSame(AWAITING_DICE, original.getGamePhase());
        assertEquals(List.of(), original.getDiceValues());
        assertEquals(List.of(), original.getRemainingDiceValues());
        assertEquals(Map.of(), original.getAllowedMoves());
    }
}
