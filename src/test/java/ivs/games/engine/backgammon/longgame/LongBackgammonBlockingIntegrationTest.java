package ivs.games.engine.backgammon.longgame;

import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardChecker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.state.GameState;
import ivs.games.engine.exception.EngineCommandException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.BLACK;
import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.WHITE;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.ADDRESSING;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_DICE;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_MOVE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests six-point blocking rules through the public long backgammon engine API.
 *
 * <p>Positions are described using the opponent's route:
 * a checker beyond the block must have a greater route point
 * than the block's final point.</p>
 *
 * <p>Each scenario uses the real move resolver and both active colors.
 * Each side has fifteen checkers, including borne-off checkers.</p>
 */
class LongBackgammonBlockingIntegrationTest {

    private final LongBackgammonEngine engine = new LongBackgammonEngine();

    /**
     * Verifies that moving a checker from opponent-relative point 2 to 7
     * cannot complete a six-point block when all opposing checkers remain at point 1.
     * Other legal movements remain available.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void rejectsSixPointBlockWithoutOpponentBeyondIt(StandardCheckerColor color) {
        LongGameState original = position(
                color,
                Map.of(2, 4, 3, 3, 4, 3, 5, 3, 6, 2),
                Map.of(1, 15),
                0);

        LongGameState rolled = engine.processCommand(original, new DiceRolled(color, 5, 5));
        int from = activePoint(color, 2);
        int to = activePoint(color, 7);

        assertEquals(AWAITING_MOVE, rolled.getGamePhase());
        assertFalse(rolled.getAllowedMoves().isEmpty());
        assertFalse(rolled.getAllowedMoves().getOrDefault(from, java.util.Set.of()).contains(5));

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(rolled, new Move(color, from, 5)));

        assertEquals(List.of(5, 5, 5, 5), rolled.getRemainingDiceValues());
        assertEquals(4, stackSize(rolled, color, from));
        assertEquals(0, stackSize(rolled, color, to));
        assertSame(original.getBoard(), rolled.getBoard());
    }

    /**
     * Verifies that the same six-point block is permitted when
     * an opposing checker has reached point 8, beyond the block ending at point 7.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void allowsSixPointBlockWithOpponentBeyondIt(StandardCheckerColor color) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);

        LongGameState original = position(
                color,
                Map.of(2, 4, 3, 3, 4, 3, 5, 3, 6, 2),
                Map.of(1, 14, 8, 1),
                0);

        LongGameState rolled = engine.processCommand(original, new DiceRolled(color, 5, 5));
        int from = activePoint(color, 2);

        assertTrue(rolled.getAllowedMoves().getOrDefault(from, java.util.Set.of()).contains(5));

        LongGameState moved = engine.processCommand(rolled, new Move(color, from, 5));

        assertEquals(3, stackSize(moved, color, from));
        assertEquals(1, stackSize(moved, color, activePoint(color, 7)));
        assertEquals(1, stackSize(moved, opponent, 8));
        assertEquals(AWAITING_MOVE, moved.getGamePhase());
        assertEquals(List.of(5, 5, 5), moved.getRemainingDiceValues());

        assertEquals(4, stackSize(original, color, from));
        assertEquals(0, stackSize(original, color, activePoint(color, 7)));
    }

    /**
     * Verifies that an opposing borne-off checker permits the block,
     * even when every opposing checker still on the board remains behind it.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void allowsSixPointBlockWhenOpponentHasBorneOffChecker(StandardCheckerColor color) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);

        LongGameState original = position(
                color,
                Map.of(2, 4, 3, 3, 4, 3, 5, 3, 6, 2),
                Map.of(1, 14),
                1);

        LongGameState rolled = engine.processCommand(original, new DiceRolled(color, 5, 5));
        int from = activePoint(color, 2);

        assertTrue(rolled.getAllowedMoves().getOrDefault(from, java.util.Set.of()).contains(5));

        LongGameState moved = engine.processCommand(rolled, new Move(color, from, 5));

        assertEquals(3, stackSize(moved, color, from));
        assertEquals(1, stackSize(moved, color, activePoint(color, 7)));
        assertEquals(1, moved.getBoard().getBorneOff().get(opponent).size());
        assertEquals(14, stackSize(moved, opponent, 1));
        assertEquals(List.of(5, 5, 5), moved.getRemainingDiceValues());
    }

    /**
     * Verifies that moving the only checker from point 2 to point 7
     * leaves five occupied points rather than creating a six-point block.
     * The source must be evaluated after the movement.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void allowsMovementWhenEmptyingSourcePreventsSixPointBlock(StandardCheckerColor color) {
        LongGameState original = position(
                color,
                Map.of(2, 1, 3, 4, 4, 4, 5, 3, 6, 3),
                Map.of(1, 15),
                0);

        LongGameState rolled = engine.processCommand(original, new DiceRolled(color, 5, 5));
        int from = activePoint(color, 2);

        assertTrue(rolled.getAllowedMoves().getOrDefault(from, java.util.Set.of()).contains(5));

        LongGameState moved = engine.processCommand(rolled, new Move(color, from, 5));

        assertEquals(0, stackSize(moved, color, from));
        assertEquals(1, stackSize(moved, color, activePoint(color, 7)));

        assertEquals(4, stackSize(moved, color, activePoint(color, 3)));
        assertEquals(4, stackSize(moved, color, activePoint(color, 4)));
        assertEquals(3, stackSize(moved, color, activePoint(color, 5)));
        assertEquals(3, stackSize(moved, color, activePoint(color, 6)));

        assertEquals(1, stackSize(original, color, from));
        assertEquals(0, stackSize(original, color, activePoint(color, 7)));
    }

    /**
     * Verifies that the opponent's route does not wrap past bearing off
     * when consecutive occupied points are counted.
     * Points 22, 23, 24, 1, 2 and 3 are two separate three-point blocks.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void doesNotJoinBlocksAcrossOpponentRouteBoundary(StandardCheckerColor color) {
        LongGameState original = position(
                color,
                Map.of(1, 4, 2, 3, 22, 3, 23, 3, 24, 2),
                Map.of(4, 15),
                0);

        LongGameState rolled = engine.processCommand(original, new DiceRolled(color, 2, 2));
        int from = activePoint(color, 1);

        assertEquals(AWAITING_MOVE, rolled.getGamePhase());
        assertTrue(rolled.getAllowedMoves().getOrDefault(from, java.util.Set.of()).contains(2));

        LongGameState moved = engine.processCommand(rolled, new Move(color, from, 2));

        assertEquals(3, stackSize(moved, color, from));
        assertEquals(1, stackSize(moved, color, activePoint(color, 3)));
        assertEquals(3, stackSize(moved, color, activePoint(color, 2)));
        assertEquals(3, stackSize(moved, color, activePoint(color, 22)));
        assertEquals(3, stackSize(moved, color, activePoint(color, 23)));
        assertEquals(2, stackSize(moved, color, activePoint(color, 24)));
    }

    /*
     * Builds a scenario awaiting dice.
     * Both placement maps use the opponent's route coordinates.
     * Active checkers occupy the proposed block; opposing checkers
     * are placed behind or beyond it as specified by the scenario.
     */
    private static LongGameState position(
            StandardCheckerColor color,
            Map<Integer, Integer> activeStacks,
            Map<Integer, Integer> opponentStacks,
            int opponentBorneOff) {

        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        StandardChecker ownChecker = color == WHITE
                ? StandardChecker.WHITE_MAN : StandardChecker.BLACK_MAN;
        StandardChecker opposingChecker = opponent == WHITE
                ? StandardChecker.WHITE_MAN : StandardChecker.BLACK_MAN;

        StandardBoard<StandardChecker> board = new StandardBoard<>();

        activeStacks.forEach((point, count) ->
                board.getCell(ADDRESSING.toBoardIndex(opponent, point))
                        .setState(Collections.nCopies(count, ownChecker)));

        opponentStacks.forEach((point, count) ->
                board.getCell(ADDRESSING.toBoardIndex(opponent, point))
                        .setState(Collections.nCopies(count, opposingChecker)));

        board.getBorneOff(opponent)
                .setState(Collections.nCopies(opponentBorneOff, opposingChecker));

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
                .firstTurns(Map.of(WHITE, false, BLACK, false))
                .build();
    }

    /*
     * Converts an opponent-relative position to an active-side route point.
     * Commands must use active-side coordinates.
     */
    private static int activePoint(StandardCheckerColor color, int opponentPoint) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        int index = ADDRESSING.toBoardIndex(opponent, opponentPoint);

        return ADDRESSING.toRoutePoint(color, index);
    }

    /*
     * Reads a stack through the specified side's route coordinates.
     */
    private static int stackSize(GameState state, StandardCheckerColor color, int point) {
        int index = ADDRESSING.toBoardIndex(color, point);

        return state.getBoard().getCells().get(index).size();
    }
}
