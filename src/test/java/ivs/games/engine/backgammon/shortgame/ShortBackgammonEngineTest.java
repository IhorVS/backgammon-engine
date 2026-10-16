package ivs.games.engine.backgammon.shortgame;

import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardChecker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.command.BearOff;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Enter;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.state.GamePhase;
import ivs.games.engine.backgammon.state.GameSetup;
import ivs.games.engine.exception.EngineCommandException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.BLACK;
import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.WHITE;
import static ivs.games.engine.backgammon.shortgame.BoardGeometry.ADDRESSING;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_DICE;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_MOVE;
import static ivs.games.engine.backgammon.state.GamePhase.FINISHED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests short backgammon initialization, action validation and state transitions.
 *
 * <p>The move resolver is mocked. Tests verify engine behavior independently
 * of legal continuation calculation and mandatory dice usage rules.</p>
 */
class ShortBackgammonEngineTest {

    private final ShortMoveResolver resolver = mock(ShortMoveResolver.class);
    private final ShortBackgammonEngine engine = new ShortBackgammonEngine(resolver);

    /**
     * Verifies that a null resolver is rejected.
     */
    @SuppressWarnings("DataFlowIssue")
    @Test
    void rejectsNullResolver() {
        assertThrows(NullPointerException.class, () -> new ShortBackgammonEngine(null));
    }

    /**
     * Verifies the initial arrangement, empty special cells and initial turn data.
     * Initialization does not invoke the resolver.
     *
     * @param color the starting color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void createsInitialState(StandardCheckerColor color) {
        ShortGameState actual = engine.createInitialState(new GameSetup(color));
        var board = new StandardBoard<>(actual.getBoard());

        assertEquals(color, actual.getActiveColor());
        assertEquals(AWAITING_DICE, actual.getGamePhase());
        assertTrue(actual.getDiceValues().isEmpty());
        assertTrue(actual.getRemainingDiceValues().isEmpty());
        assertTrue(actual.getAllowedMoves().isEmpty());
        assertTrue(actual.getAllowedBearOffs().isEmpty());
        assertTrue(actual.getAllowedEntries().isEmpty());
        assertNull(actual.getWinner());

        assertEquals(2, board.getCell(ADDRESSING.toBoardIndex(WHITE, 1)).size());
        assertEquals(5, board.getCell(ADDRESSING.toBoardIndex(WHITE, 12)).size());
        assertEquals(3, board.getCell(ADDRESSING.toBoardIndex(WHITE, 17)).size());
        assertEquals(5, board.getCell(ADDRESSING.toBoardIndex(WHITE, 19)).size());

        assertEquals(2, board.getCell(ADDRESSING.toBoardIndex(BLACK, 1)).size());
        assertEquals(5, board.getCell(ADDRESSING.toBoardIndex(BLACK, 12)).size());
        assertEquals(3, board.getCell(ADDRESSING.toBoardIndex(BLACK, 17)).size());
        assertEquals(5, board.getCell(ADDRESSING.toBoardIndex(BLACK, 19)).size());

        int whiteCount = actual.getBoard().getCells().stream()
                .flatMap(List::stream)
                .mapToInt(checker -> checker.getColor() == WHITE ? 1 : 0)
                .sum();
        int blackCount = actual.getBoard().getCells().stream()
                .flatMap(List::stream)
                .mapToInt(checker -> checker.getColor() == BLACK ? 1 : 0)
                .sum();

        assertEquals(15, whiteCount);
        assertEquals(15, blackCount);

        assertTrue(board.getBar(WHITE).isEmpty());
        assertTrue(board.getBar(BLACK).isEmpty());
        assertTrue(board.getBorneOff(WHITE).isEmpty());
        assertTrue(board.getBorneOff(BLACK).isEmpty());

        assertSame(StandardChecker.WHITE_MAN, board.getCell(ADDRESSING.toBoardIndex(WHITE, 1)).peek());
        assertSame(StandardChecker.BLACK_MAN, board.getCell(ADDRESSING.toBoardIndex(BLACK, 1)).peek());

        verifyNoInteractions(resolver);
    }

    /**
     * Verifies that a roll is passed to the resolver and its three option maps
     * are stored without modifying the original state.
     */
    @Test
    void diceRollStoresResolvedOptions() {
        ShortGameState original = state(new StandardBoard<>(), WHITE, AWAITING_DICE, List.of());

        Map<Integer, Set<Integer>> moves = Map.of(1, Set.of(3));
        Map<Integer, Set<Integer>> bearOffs = Map.of(24, Set.of(5));
        Map<Integer, Set<Integer>> entries = Map.of(3, Set.of(3));
        resolveWith(moves, bearOffs, entries);

        ShortGameState actual = engine.processDiceRolled(original, new DiceRolled(WHITE, 3, 5));

        ArgumentCaptor<MoveContext> captor = ArgumentCaptor.forClass(MoveContext.class);
        verify(resolver).resolve(captor.capture());
        MoveContext context = captor.getValue();

        assertSame(original.getBoard(), context.getBoardState());
        assertEquals(WHITE, context.getActiveColor());
        assertEquals(List.of(3, 5), context.getDiceValues());
        assertEquals(List.of(3, 5), context.getMovementPool());

        assertEquals(AWAITING_MOVE, actual.getGamePhase());
        assertEquals(WHITE, actual.getActiveColor());
        assertEquals(List.of(3, 5), actual.getDiceValues());
        assertEquals(List.of(3, 5), actual.getRemainingDiceValues());
        assertSame(moves, actual.getAllowedMoves());
        assertSame(bearOffs, actual.getAllowedBearOffs());
        assertSame(entries, actual.getAllowedEntries());

        assertEquals(AWAITING_DICE, original.getGamePhase());
        assertTrue(original.getRemainingDiceValues().isEmpty());
    }

    /**
     * Verifies that doubles are expanded before resolving actions.
     */
    @Test
    void diceRollExpandsDoubleBeforeResolution() {
        ShortGameState original = state(new StandardBoard<>(), WHITE, AWAITING_DICE, List.of());
        resolveWith(Map.of(1, Set.of(4)), Map.of(), Map.of());

        ShortGameState actual = engine.processDiceRolled(original, new DiceRolled(WHITE, 4, 4));

        ArgumentCaptor<MoveContext> captor = ArgumentCaptor.forClass(MoveContext.class);
        verify(resolver).resolve(captor.capture());

        assertEquals(List.of(4, 4), captor.getValue().getDiceValues());
        assertEquals(List.of(4, 4, 4, 4), captor.getValue().getMovementPool());
        assertEquals(List.of(4, 4, 4, 4), actual.getRemainingDiceValues());
    }

    /**
     * Verifies automatic passing when the resolver reports no action after a roll.
     */
    @Test
    void diceRollWithoutAvailableActionsPassesTurn() {
        ShortGameState original = state(new StandardBoard<>(), WHITE, AWAITING_DICE, List.of());
        resolveWith(Map.of(), Map.of(), Map.of());

        ShortGameState actual = engine.processDiceRolled(original, new DiceRolled(WHITE, 3, 5));

        assertTurnPassed(actual);
        assertSame(original.getBoard(), actual.getBoard());
        verify(resolver).resolve(any(MoveContext.class));
    }

    /**
     * Verifies movement, hitting and consumption of one repeated die value.
     * Resolved continuation options are stored in the resulting state.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void movementHitsOpponentAndResolvesContinuation(StandardCheckerColor color) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        StandardBoard<StandardChecker> board = new StandardBoard<>();

        board.getCell(ADDRESSING.toBoardIndex(color, 8))
                .setState(List.of(man(color), man(color)));
        board.getCell(ADDRESSING.toBoardIndex(color, 11)).push(man(opponent));
        board.getBar(opponent).push(man(opponent));

        ShortGameState original = state(board, color, AWAITING_MOVE, List.of(3, 3));
        Map<Integer, Set<Integer>> moves = Map.of(11, Set.of(3));
        resolveWith(moves, Map.of(), Map.of());

        ShortGameState actual = engine.processMove(original, new Move(color, 8, 3));

        assertEquals(List.of(man(color)), actual.getBoard().getCells()
                .get(ADDRESSING.toBoardIndex(color, 8)));
        assertEquals(List.of(man(color)), actual.getBoard().getCells()
                .get(ADDRESSING.toBoardIndex(color, 11)));
        assertEquals(List.of(man(opponent), man(opponent)),
                actual.getBoard().getBars().get(opponent));
        assertEquals(List.of(3), actual.getRemainingDiceValues());
        assertSame(moves, actual.getAllowedMoves());
        assertEquals(AWAITING_MOVE, actual.getGamePhase());
        assertEquals(color, actual.getActiveColor());

        ArgumentCaptor<MoveContext> captor = ArgumentCaptor.forClass(MoveContext.class);
        verify(resolver).resolve(captor.capture());

        assertSame(actual.getBoard(), captor.getValue().getBoardState());
        assertEquals(List.of(3), captor.getValue().getMovementPool());
        assertEquals(original.getDiceValues(), captor.getValue().getDiceValues());

        assertEquals(2, original.getBoard().getCells().get(ADDRESSING.toBoardIndex(color, 8)).size());
        assertEquals(List.of(man(opponent)), original.getBoard().getCells()
                .get(ADDRESSING.toBoardIndex(color, 11)));
        assertEquals(1, original.getBoard().getBars().get(opponent).size());
        assertEquals(List.of(3, 3), original.getRemainingDiceValues());
    }

    /**
     * Verifies that movement to an empty or friendly point does not hit a checker.
     *
     * @param destinationCount the number of friendly destination checkers
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void movementToEmptyOrFriendlyPointDoesNotHit(int destinationCount) {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 8)).push(StandardChecker.WHITE_MAN);
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 11))
                .setState(Collections.nCopies(destinationCount, StandardChecker.WHITE_MAN));

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(3, 2));
        resolveWith(Map.of(11, Set.of(2)), Map.of(), Map.of());

        ShortGameState actual = engine.processMove(original, new Move(WHITE, 8, 3));

        assertTrue(actual.getBoard().getCells().get(ADDRESSING.toBoardIndex(WHITE, 8)).isEmpty());
        assertEquals(destinationCount + 1,
                actual.getBoard().getCells().get(ADDRESSING.toBoardIndex(WHITE, 11)).size());
        assertTrue(actual.getBoard().getBars().get(BLACK).isEmpty());
    }

    /**
     * Verifies that entry removes one checker from the bar, hits a blot,
     * and preserves resolver-provided entry continuation options.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void entryHitsOpponentAndResolvesContinuation(StandardCheckerColor color) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        StandardBoard<StandardChecker> board = new StandardBoard<>();

        board.getBar(color).setState(List.of(man(color), man(color)));
        board.getCell(ADDRESSING.toBoardIndex(color, 3)).push(man(opponent));

        ShortGameState original = state(board, color, AWAITING_MOVE, List.of(3, 5));
        Map<Integer, Set<Integer>> entries = Map.of(5, Set.of(5));
        resolveWith(Map.of(), Map.of(), entries);

        ShortGameState actual = engine.processEnter(original, new Enter(color, 3));

        assertEquals(List.of(man(color)), actual.getBoard().getBars().get(color));
        assertEquals(List.of(man(opponent)), actual.getBoard().getBars().get(opponent));
        assertEquals(List.of(man(color)), actual.getBoard().getCells()
                .get(ADDRESSING.toBoardIndex(color, 3)));
        assertEquals(List.of(5), actual.getRemainingDiceValues());
        assertSame(entries, actual.getAllowedEntries());
        assertEquals(AWAITING_MOVE, actual.getGamePhase());

        ArgumentCaptor<MoveContext> captor = ArgumentCaptor.forClass(MoveContext.class);
        verify(resolver).resolve(captor.capture());
        assertSame(actual.getBoard(), captor.getValue().getBoardState());
        assertEquals(List.of(5), captor.getValue().getMovementPool());

        assertEquals(2, original.getBoard().getBars().get(color).size());
        assertTrue(original.getBoard().getBars().get(opponent).isEmpty());
    }

    /**
     * Verifies entry onto empty and friendly points without hitting.
     *
     * @param destinationCount the number of friendly destination checkers
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void entryToEmptyOrFriendlyPointDoesNotHit(int destinationCount) {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getBar(WHITE).push(StandardChecker.WHITE_MAN);
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 3))
                .setState(Collections.nCopies(destinationCount, StandardChecker.WHITE_MAN));

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(3, 5));
        resolveWith(Map.of(3, Set.of(5)), Map.of(), Map.of());

        ShortGameState actual = engine.processEnter(original, new Enter(WHITE, 3));

        assertTrue(actual.getBoard().getBars().get(WHITE).isEmpty());
        assertTrue(actual.getBoard().getBars().get(BLACK).isEmpty());
        assertEquals(destinationCount + 1,
                actual.getBoard().getCells().get(ADDRESSING.toBoardIndex(WHITE, 3)).size());
        assertEquals(Map.of(3, Set.of(5)), actual.getAllowedMoves());
    }

    /**
     * Verifies that non-final bearing off consumes a value and stores recalculated options.
     */
    @Test
    void bearingOffResolvesContinuationBeforeVictory() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 24))
                .setState(List.of(StandardChecker.WHITE_MAN, StandardChecker.WHITE_MAN));
        board.getBorneOff(WHITE).setState(Collections.nCopies(13, StandardChecker.WHITE_MAN));

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(1, 2));
        Map<Integer, Set<Integer>> bearOffs = Map.of(24, Set.of(2));
        resolveWith(Map.of(), bearOffs, Map.of());

        ShortGameState actual = engine.processBearOff(original, new BearOff(WHITE, 24, 1));

        assertEquals(14, actual.getBoard().getBorneOff().get(WHITE).size());
        assertEquals(1, actual.getBoard().getCells().get(ADDRESSING.toBoardIndex(WHITE, 24)).size());
        assertEquals(List.of(2), actual.getRemainingDiceValues());
        assertEquals(AWAITING_MOVE, actual.getGamePhase());
        assertSame(bearOffs, actual.getAllowedBearOffs());
        assertNull(actual.getWinner());
        verify(resolver).resolve(any(MoveContext.class));

        assertEquals(13, original.getBoard().getBorneOff().get(WHITE).size());
        assertEquals(2, original.getBoard().getCells().get(ADDRESSING.toBoardIndex(WHITE, 24)).size());
    }

    /**
     * Verifies that the fifteenth borne-off checker finishes the game
     * without invoking the resolver or switching the active side.
     *
     * @param color the winning color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void finalBearingOffFinishesGameWithoutResolution(StandardCheckerColor color) {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(color, 24)).push(man(color));
        board.getBorneOff(color).setState(Collections.nCopies(14, man(color)));

        ShortGameState original = state(board, color, AWAITING_MOVE, List.of(1, 2));
        ShortGameState actual = engine.processBearOff(original, new BearOff(color, 24, 1));

        assertEquals(FINISHED, actual.getGamePhase());
        assertEquals(color, actual.getWinner());
        assertEquals(color, actual.getActiveColor());
        assertEquals(15, actual.getBoard().getBorneOff().get(color).size());
        assertEquals(List.of(2), actual.getRemainingDiceValues());
        assertTrue(actual.getAllowedMoves().isEmpty());
        assertTrue(actual.getAllowedBearOffs().isEmpty());
        assertTrue(actual.getAllowedEntries().isEmpty());

        assertNull(original.getWinner());
        assertEquals(AWAITING_MOVE, original.getGamePhase());
        assertEquals(14, original.getBoard().getBorneOff().get(color).size());
        verifyNoInteractions(resolver);
    }

    /**
     * Verifies passing after the last movement value is consumed.
     * Even non-empty mock options cannot keep an exhausted turn active.
     */
    @Test
    void exhaustedMovementPoolPassesTurn() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 8)).push(StandardChecker.WHITE_MAN);

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(3));
        resolveWith(Map.of(11, Set.of(2)), Map.of(), Map.of());

        ShortGameState actual = engine.processMove(original, new Move(WHITE, 8, 3));

        assertTurnPassed(actual);
        assertEquals(1, actual.getBoard().getCells().get(ADDRESSING.toBoardIndex(WHITE, 11)).size());
    }

    /**
     * Verifies passing with unused values when the resolver reports no continuation.
     */
    @Test
    void noAvailableContinuationPassesTurnWithUnusedValues() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getBar(WHITE).push(StandardChecker.WHITE_MAN);

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(3, 5));
        resolveWith(Map.of(), Map.of(), Map.of());

        ShortGameState actual = engine.processEnter(original, new Enter(WHITE, 3));

        assertTurnPassed(actual);
        assertEquals(1, actual.getBoard().getCells().get(ADDRESSING.toBoardIndex(WHITE, 3)).size());
    }

    /**
     * Verifies acceptance of a movement to an empty, friendly or single-opponent point.
     *
     * @param destinationCount the destination stack size
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void movementValidationAcceptsOpenDestinations(int destinationCount) {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 8)).push(StandardChecker.WHITE_MAN);

        StandardChecker destinationChecker = destinationCount == 1
                ? StandardChecker.BLACK_MAN : StandardChecker.WHITE_MAN;

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 11))
                .setState(Collections.nCopies(destinationCount, destinationChecker));

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(3))
                .toBuilder()
                .allowedMoves(Map.of(8, Set.of(3)))
                .build();

        assertDoesNotThrow(() -> engine.validateMove(original, new Move(WHITE, 8, 3)));
        verifyNoInteractions(resolver);
    }

    /**
     * Verifies movement rejection for bar occupancy, missing dice,
     * invalid source ownership, blocked destinations and resolved exclusions.
     */
    @Test
    void movementValidationRejectsInvalidPositionsAndOptions() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 8)).push(StandardChecker.WHITE_MAN);

        ShortGameState valid = state(board, WHITE, AWAITING_MOVE, List.of(3))
                .toBuilder()
                .allowedMoves(Map.of(8, Set.of(3)))
                .build();
        Move action = new Move(WHITE, 8, 3);

        assertThrows(EngineCommandException.class,
                () -> engine.validateMove(valid.toBuilder().remainingDiceValues(List.of(2)).build(), action));

        assertThrows(EngineCommandException.class,
                () -> engine.validateMove(valid.toBuilder().allowedMoves(Map.of()).build(), action));

        assertThrows(EngineCommandException.class,
                () -> engine.validateMove(valid.toBuilder().allowedMoves(Map.of(8, Set.of(2))).build(), action));

        board.getBar(WHITE).push(StandardChecker.WHITE_MAN);
        ShortGameState barred = valid.toBuilder().board(board.getState()).build();
        assertThrows(EngineCommandException.class, () -> engine.validateMove(barred, action));
        board.getBar(WHITE).clear();

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 8)).clear();
        ShortGameState emptySource = valid.toBuilder().board(board.getState()).build();
        assertThrows(EngineCommandException.class, () -> engine.validateMove(emptySource, action));

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 8)).push(StandardChecker.BLACK_MAN);
        ShortGameState opposingSource = valid.toBuilder().board(board.getState()).build();
        assertThrows(EngineCommandException.class, () -> engine.validateMove(opposingSource, action));

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 8)).setState(List.of(StandardChecker.WHITE_MAN));
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 11))
                .setState(List.of(StandardChecker.BLACK_MAN, StandardChecker.BLACK_MAN));

        ShortGameState blocked = valid.toBuilder().board(board.getState()).build();
        assertThrows(EngineCommandException.class, () -> engine.validateMove(blocked, action));

        verifyNoInteractions(resolver);
    }

    /**
     * Verifies entry validation for empty, friendly and single-opponent points.
     *
     * @param destinationCount the destination stack size
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void entryValidationAcceptsOpenDestinations(int destinationCount) {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getBar(WHITE).push(StandardChecker.WHITE_MAN);

        StandardChecker destinationChecker = destinationCount == 1
                ? StandardChecker.BLACK_MAN : StandardChecker.WHITE_MAN;

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 3))
                .setState(Collections.nCopies(destinationCount, destinationChecker));

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(3))
                .toBuilder()
                .allowedEntries(Map.of(3, Set.of(3)))
                .build();

        assertDoesNotThrow(() -> engine.validateEnter(original, new Enter(WHITE, 3)));
        verifyNoInteractions(resolver);
    }

    /**
     * Verifies that entry points accepted by common route validation
     * but outside the entry range are rejected.
     *
     * @param to the invalid entry point
     */
    @ParameterizedTest
    @ValueSource(ints = {7, 24})
    void entryValidationRejectsPointsOutsideEntryRange(int to) {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getBar(WHITE).push(StandardChecker.WHITE_MAN);
        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(3));

        assertThrows(EngineCommandException.class,
                () -> engine.validateEnter(original, new Enter(WHITE, to)));
    }

    /**
     * Verifies rejection of entry without a bar checker, with unavailable dice,
     * onto a blocked point or outside the resolved entry options.
     */
    @Test
    void entryValidationRejectsInvalidPositionsAndOptions() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getBar(WHITE).push(StandardChecker.WHITE_MAN);

        ShortGameState valid = state(board, WHITE, AWAITING_MOVE, List.of(3))
                .toBuilder()
                .allowedEntries(Map.of(3, Set.of(3)))
                .build();
        Enter action = new Enter(WHITE, 3);

        assertThrows(EngineCommandException.class,
                () -> engine.validateEnter(valid.toBuilder().remainingDiceValues(List.of(2)).build(), action));

        assertThrows(EngineCommandException.class,
                () -> engine.validateEnter(valid.toBuilder().allowedEntries(Map.of()).build(), action));

        assertThrows(EngineCommandException.class,
                () -> engine.validateEnter(valid.toBuilder().allowedEntries(Map.of(3, Set.of(2))).build(), action));

        board.getBar(WHITE).clear();
        ShortGameState emptyBar = valid.toBuilder().board(board.getState()).build();
        assertThrows(EngineCommandException.class, () -> engine.validateEnter(emptyBar, action));

        board.getBar(WHITE).push(StandardChecker.WHITE_MAN);
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 3))
                .setState(List.of(StandardChecker.BLACK_MAN, StandardChecker.BLACK_MAN));

        ShortGameState blocked = valid.toBuilder().board(board.getState()).build();
        assertThrows(EngineCommandException.class, () -> engine.validateEnter(blocked, action));

        verifyNoInteractions(resolver);
    }

    /**
     * Verifies exact bearing off even when an earlier home point is occupied.
     */
    @Test
    void bearingOffValidationAcceptsExactValueWithEarlierChecker() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 19)).push(StandardChecker.WHITE_MAN);
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 24)).push(StandardChecker.WHITE_MAN);

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(1))
                .toBuilder()
                .allowedBearOffs(Map.of(24, Set.of(1)))
                .build();

        assertDoesNotThrow(() -> engine.validateBearOff(original, new BearOff(WHITE, 24, 1)));
        verifyNoInteractions(resolver);
    }

    /**
     * Verifies oversize bearing off from the earliest occupied home point.
     */
    @Test
    void bearingOffValidationAcceptsOversizeValueAtFurthestChecker() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 23)).push(StandardChecker.WHITE_MAN);
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 24)).push(StandardChecker.WHITE_MAN);

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(3))
                .toBuilder()
                .allowedBearOffs(Map.of(23, Set.of(3)))
                .build();

        assertDoesNotThrow(() -> engine.validateBearOff(original, new BearOff(WHITE, 23, 3)));
        verifyNoInteractions(resolver);
    }

    /**
     * Verifies rejection when a die cannot reach the bearing-off boundary.
     */
    @Test
    void bearingOffValidationRejectsInsufficientValue() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 19)).push(StandardChecker.WHITE_MAN);

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(5))
                .toBuilder()
                .allowedBearOffs(Map.of(19, Set.of(5)))
                .build();

        EngineCommandException exception = assertThrows(
                EngineCommandException.class,
                () -> engine.validateBearOff(original, new BearOff(WHITE, 19, 5)));

        assertEquals("Die value 5 is insufficient to bear off from route point 19", exception.getMessage());
    }

    /**
     * Verifies that oversize bearing off is rejected when an earlier point
     * still contains an active checker.
     */
    @Test
    void bearingOffValidationRejectsOversizeValueWithEarlierChecker() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 19)).push(StandardChecker.WHITE_MAN);
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 24)).push(StandardChecker.WHITE_MAN);

        ShortGameState original = state(board, WHITE, AWAITING_MOVE, List.of(2))
                .toBuilder()
                .allowedBearOffs(Map.of(24, Set.of(2)))
                .build();

        assertThrows(EngineCommandException.class,
                () -> engine.validateBearOff(original, new BearOff(WHITE, 24, 2)));
    }

    /**
     * Verifies bearing-off rejection for bar occupancy, checkers outside home,
     * unavailable dice, invalid sources and resolved exclusions.
     */
    @Test
    void bearingOffValidationRejectsInvalidPositionsAndOptions() {
        StandardBoard<StandardChecker> board = new StandardBoard<>();
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 24)).push(StandardChecker.WHITE_MAN);

        ShortGameState valid = state(board, WHITE, AWAITING_MOVE, List.of(1))
                .toBuilder()
                .allowedBearOffs(Map.of(24, Set.of(1)))
                .build();
        BearOff action = new BearOff(WHITE, 24, 1);

        assertThrows(EngineCommandException.class,
                () -> engine.validateBearOff(valid.toBuilder().remainingDiceValues(List.of(2)).build(), action));

        assertThrows(EngineCommandException.class,
                () -> engine.validateBearOff(valid.toBuilder().allowedBearOffs(Map.of()).build(), action));

        assertThrows(EngineCommandException.class,
                () -> engine.validateBearOff(
                        valid.toBuilder().allowedBearOffs(Map.of(24, Set.of(2))).build(), action));

        board.getBar(WHITE).push(StandardChecker.WHITE_MAN);
        ShortGameState barred = valid.toBuilder().board(board.getState()).build();
        assertThrows(EngineCommandException.class, () -> engine.validateBearOff(barred, action));
        board.getBar(WHITE).clear();

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 18)).push(StandardChecker.WHITE_MAN);
        ShortGameState outsideHome = valid.toBuilder().board(board.getState()).build();
        assertThrows(EngineCommandException.class, () -> engine.validateBearOff(outsideHome, action));
        board.getCell(ADDRESSING.toBoardIndex(WHITE, 18)).clear();

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 24)).clear();
        ShortGameState emptySource = valid.toBuilder().board(board.getState()).build();
        assertThrows(EngineCommandException.class, () -> engine.validateBearOff(emptySource, action));

        board.getCell(ADDRESSING.toBoardIndex(WHITE, 24)).push(StandardChecker.BLACK_MAN);
        ShortGameState opposingSource = valid.toBuilder().board(board.getState()).build();
        assertThrows(EngineCommandException.class, () -> engine.validateBearOff(opposingSource, action));

        verifyNoInteractions(resolver);
    }

    /*
     * Configures resolver output without calculating any game rules.
     * The maps are deliberately controlled by individual engine tests.
     */
    private void resolveWith(
            Map<Integer, Set<Integer>> moves,
            Map<Integer, Set<Integer>> bearOffs,
            Map<Integer, Set<Integer>> entries) {

        doAnswer(invocation -> {
            MoveContext context = invocation.getArgument(0);
            context.setAllowedMoves(moves)
                    .setAllowedBearOffs(bearOffs)
                    .setAllowedEntries(entries);
            return null;
        }).when(resolver).resolve(any(MoveContext.class));
    }

    /*
     * Creates a state for direct testing of overridden methods.
     * Resolved options are supplied explicitly by tests when needed.
     */
    private static ShortGameState state(
            StandardBoard<StandardChecker> board,
            StandardCheckerColor color,
            GamePhase phase,
            List<Integer> remaining) {

        return ShortGameState.builder()
                .board(board.getState())
                .activeColor(color)
                .gamePhase(phase)
                .diceValues(List.of(3, 5))
                .remainingDiceValues(remaining)
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .allowedEntries(Map.of())
                .winner(null)
                .build();
    }

    /*
     * Checks all properties reset when a turn ends.
     */
    /*
     * Checks all properties reset when play passes to the black side.
     */
    private static void assertTurnPassed(ShortGameState state) {
        assertEquals(BLACK, state.getActiveColor());
        assertEquals(AWAITING_DICE, state.getGamePhase());
        assertTrue(state.getDiceValues().isEmpty());
        assertTrue(state.getRemainingDiceValues().isEmpty());
        assertTrue(state.getAllowedMoves().isEmpty());
        assertTrue(state.getAllowedBearOffs().isEmpty());
        assertTrue(state.getAllowedEntries().isEmpty());
        assertNull(state.getWinner());
    }

    /*
     * Selects a checker constant for the requested color.
     */
    private static StandardChecker man(StandardCheckerColor color) {
        return color == WHITE ? StandardChecker.WHITE_MAN : StandardChecker.BLACK_MAN;
    }
}
