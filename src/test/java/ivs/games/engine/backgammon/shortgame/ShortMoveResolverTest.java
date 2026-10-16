package ivs.games.engine.backgammon.shortgame;

import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardChecker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.WHITE;
import static ivs.games.engine.backgammon.shortgame.BoardGeometry.ADDRESSING;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests short backgammon action resolution independently of the game engine.
 *
 * <p>Scenarios cover ordinary movement, bar entry, bearing off,
 * mandatory dice usage and preservation of supplied data.</p>
 */
@SuppressWarnings("DataFlowIssue")
class ShortMoveResolverTest {

    private final ShortMoveResolver resolver = new ShortMoveResolver();

    /**
     * Verifies that a null context is rejected.
     */
    @Test
    void rejectsNullContext() {
        assertThrows(NullPointerException.class, () -> resolver.resolve(null));
    }

    /**
     * Verifies that an empty movement pool clears previously stored options.
     */
    @Test
    void emptyPoolClearsPreviousOptions() {
        MoveContext context = context(WHITE, Map.of(8, 1), Map.of(), 0, List.of())
                .setAllowedMoves(Map.of(8, Set.of(3)))
                .setAllowedBearOffs(Map.of(24, Set.of(1)))
                .setAllowedEntries(Map.of(3, Set.of(3)));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of());
    }

    /**
     * Verifies that a side with all checkers borne off has no actions.
     */
    @Test
    void noRemainingCheckersProducesNoActions() {
        MoveContext context = context(WHITE, Map.of(), Map.of(), 0, List.of(3, 5));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of());
    }

    /**
     * Verifies movement to an empty, single-opponent or friendly point.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void permitsMovementToOpenPoints(StandardCheckerColor color) {
        MoveContext empty = context(
                color, Map.of(8, 1), Map.of(), 0, List.of(3));
        MoveContext blot = context(
                color, Map.of(8, 1), Map.of(11, 1), 0, List.of(3));
        MoveContext friendly = context(
                color, Map.of(8, 1, 11, 1), Map.of(), 0, List.of(3));

        resolver.resolve(empty);
        resolver.resolve(blot);
        resolver.resolve(friendly);

        assertOptions(empty, Map.of(8, Set.of(3)), Map.of(), Map.of());
        assertOptions(blot, Map.of(8, Set.of(3)), Map.of(), Map.of());
        assertOptions(friendly, Map.of(8, Set.of(3), 11, Set.of(3)), Map.of(), Map.of());
    }

    /**
     * Verifies that two or more opposing checkers close a destination.
     *
     * @param count the opposing destination stack size
     */
    @ParameterizedTest
    @ValueSource(ints = {2, 3, 15})
    void rejectsMovementOntoClosedPoint(int count) {
        MoveContext context = context(
                WHITE, Map.of(8, 1), Map.of(11, count), 0, List.of(3));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of());
    }

    /**
     * Verifies that opposing source checkers do not generate active movements.
     */
    @Test
    void doesNotGenerateMovesForOpposingCheckers() {
        MoveContext context = context(
                WHITE, Map.of(8, 1), Map.of(4, 1), 0, List.of(3));

        resolver.resolve(context);

        assertOptions(context, Map.of(8, Set.of(3)), Map.of(), Map.of());
    }

    /**
     * Verifies that a non-empty active bar permits entries only,
     * even when ordinary movements would otherwise be available.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void occupiedBarPermitsOnlyEntries(StandardCheckerColor color) {
        MoveContext context = context(
                color, Map.of(8, 1), Map.of(), 1, List.of(3));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of(3, Set.of(3)));
    }

    /**
     * Verifies entry onto empty, friendly and single-opponent points.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void permitsEntryToOpenPoints(StandardCheckerColor color) {
        MoveContext empty = context(
                color, Map.of(), Map.of(), 1, List.of(3));
        MoveContext friendly = context(
                color, Map.of(3, 1), Map.of(), 1, List.of(3));
        MoveContext blot = context(
                color, Map.of(), Map.of(3, 1), 1, List.of(3));

        resolver.resolve(empty);
        resolver.resolve(friendly);
        resolver.resolve(blot);

        assertOptions(empty, Map.of(), Map.of(), Map.of(3, Set.of(3)));
        assertOptions(friendly, Map.of(), Map.of(), Map.of(3, Set.of(3)));
        assertOptions(blot, Map.of(), Map.of(), Map.of(3, Set.of(3)));
    }

    /**
     * Verifies that a closed entry point cannot be entered.
     *
     * @param count the opposing entry stack size
     */
    @ParameterizedTest
    @ValueSource(ints = {2, 3, 15})
    void rejectsEntryOntoClosedPoint(int count) {
        MoveContext context = context(
                WHITE, Map.of(8, 1), Map.of(3, count), 1, List.of(3));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of());
    }

    /**
     * Verifies that the last bar checker may enter and then move
     * using the other die. Both entry orders remain available.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void lastEntryAllowsOrdinaryMovementInContinuation(StandardCheckerColor color) {
        MoveContext context = context(
                color, Map.of(), Map.of(), 1, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(),
                Map.of(1, Set.of(1), 2, Set.of(2)));
    }

    /**
     * Verifies that ordinary movements cannot be used in continuations
     * while another active checker remains on the bar.
     *
     * <p>Only entry with one is possible. Entry with two is blocked.
     * Moving the entered checker using two must not be considered.</p>
     */
    @Test
    void remainingBarCheckerPreventsOrdinaryContinuation() {
        MoveContext context = context(
                WHITE, Map.of(), Map.of(2, 2), 2, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of(1, Set.of(1)));
    }

    /**
     * Verifies that the higher entry value is required when either entry
     * is individually possible but the remaining die cannot be used.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void requiresHigherEntryValueWhenOnlyOneActionIsPossible(StandardCheckerColor color) {
        MoveContext context = context(
                color, Map.of(), Map.of(3, 2), 1, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of(2, Set.of(2)));
    }

    /**
     * Verifies that entry remains possible with the lower die
     * when the higher entry point is blocked.
     */
    @Test
    void permitsLowerEntryValueWhenHigherEntryIsBlocked() {
        MoveContext context = context(
                WHITE, Map.of(), Map.of(2, 2, 3, 2), 1, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of(1, Set.of(1)));
    }

    /**
     * Verifies that a checker outside the home prevents bearing off.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void checkerOutsideHomePreventsBearingOff(StandardCheckerColor color) {
        MoveContext context = context(
                color, Map.of(18, 1, 24, 1), Map.of(), 0, List.of(1));

        resolver.resolve(context);

        assertOptions(context, Map.of(18, Set.of(1)), Map.of(), Map.of());
    }

    /**
     * Verifies that a bar checker prevents bearing off
     * even when every active checker on the board is home.
     */
    @Test
    void barCheckerPreventsBearingOff() {
        MoveContext context = context(
                WHITE, Map.of(24, 1), Map.of(), 1, List.of(1));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of(1, Set.of(1)));
    }

    /**
     * Verifies exact bearing off while another active checker
     * occupies an earlier home point.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void permitsExactBearingOffWithEarlierHomeChecker(StandardCheckerColor color) {
        MoveContext context = context(
                color, Map.of(19, 1, 24, 1), Map.of(), 0, List.of(1));

        resolver.resolve(context);

        assertOptions(context,
                Map.of(19, Set.of(1)),
                Map.of(24, Set.of(1)),
                Map.of());
    }

    /**
     * Verifies exact bearing off from the first home point.
     */
    @Test
    void permitsExactBearingOffFromFirstHomePoint() {
        MoveContext context = context(
                WHITE, Map.of(19, 1), Map.of(), 0, List.of(6));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(19, Set.of(6)), Map.of());
    }

    /**
     * Verifies that an insufficient bearing-off value produces
     * an ordinary movement rather than a bearing-off option.
     */
    @Test
    void insufficientBearingOffValueProducesOrdinaryMovement() {
        MoveContext context = context(
                WHITE, Map.of(19, 1), Map.of(), 0, List.of(5));

        resolver.resolve(context);

        assertOptions(context, Map.of(19, Set.of(5)), Map.of(), Map.of());
    }

    /**
     * Verifies that oversize bearing off is allowed only
     * from the earliest occupied home point.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void oversizeBearingOffRequiresEarliestOccupiedHomePoint(StandardCheckerColor color) {
        MoveContext context = context(
                color, Map.of(23, 1, 24, 1), Map.of(), 0, List.of(3));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(23, Set.of(3)), Map.of());
    }

    /**
     * Verifies that an opposing checker on an earlier home point
     * does not prevent active oversize bearing off.
     */
    @Test
    void opposingEarlierCheckerDoesNotPreventOversizeBearingOff() {
        MoveContext context = context(
                WHITE, Map.of(24, 1), Map.of(19, 1), 0, List.of(6));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(24, Set.of(6)), Map.of());
    }

    /**
     * Verifies that moving before bearing off is required when
     * it permits both dice to be used instead of only one.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void requiresMovementBeforeBearingOffToUseBothValues(StandardCheckerColor color) {
        MoveContext context = context(
                color, Map.of(23, 1), Map.of(), 0, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(23, Set.of(1)), Map.of(), Map.of());
    }

    /**
     * Verifies that the higher bearing-off value is required
     * when only one checker action can be performed.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void requiresHigherBearingOffValueWhenOnlyOneActionIsPossible(StandardCheckerColor color) {
        MoveContext context = context(
                color, Map.of(24, 1), Map.of(), 0, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(24, Set.of(2)), Map.of());
    }

    /**
     * Verifies that the higher movement value is required when both
     * first destinations are open but their shared continuation is blocked.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void requiresHigherMovementValueWhenOnlyOneActionIsPossible(StandardCheckerColor color) {
        MoveContext context = context(
                color, Map.of(8, 1), Map.of(11, 2), 0, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(8, Set.of(2)), Map.of(), Map.of());
    }

    /**
     * Verifies that two source checkers allow both values to be used
     * on separate checkers even when a combined movement is blocked.
     */
    @Test
    void separateCheckersPermitBothValuesWithoutCombinedMovement() {
        MoveContext context = context(
                WHITE, Map.of(8, 2), Map.of(11, 2), 0, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(8, Set.of(1, 2)), Map.of(), Map.of());
    }

    /**
     * Verifies that only the die order permitting two movements is retained.
     *
     * <p>The one-step destination is closed, but moving two first
     * permits a subsequent one-step movement.</p>
     */
    @Test
    void retainsOrderAllowingBothMovementValues() {
        MoveContext context = context(
                WHITE, Map.of(8, 1), Map.of(9, 2), 0, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(8, Set.of(2)), Map.of(), Map.of());
    }

    /**
     * Verifies that an open final point does not permit jumping
     * over two blocked intermediate destinations.
     */
    @Test
    void rejectsCombinedMovementWhenBothIntermediatePointsAreClosed() {
        MoveContext context = context(
                WHITE, Map.of(8, 1), Map.of(9, 2, 10, 2), 0, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of());
    }

    /**
     * Verifies that hitting a blot permits a continuation from that point.
     *
     * <p>Entry with one hits a blot and then uses two.
     * Entry with two is blocked, so one must remain a legal first action.</p>
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void hitDuringEntryAllowsContinuation(StandardCheckerColor color) {
        MoveContext context = context(
                color, Map.of(), Map.of(1, 1, 2, 2), 1, List.of(1, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(), Map.of(), Map.of(1, Set.of(1)));
    }

    /**
     * Verifies that repeated double values do not create duplicate options
     * and that a continuation can consume all four values.
     */
    @Test
    void doubleProducesUniqueFirstOption() {
        MoveContext context = context(
                WHITE, Map.of(8, 1), Map.of(), 0, List.of(2, 2, 2, 2));

        resolver.resolve(context);

        assertOptions(context, Map.of(8, Set.of(2)), Map.of(), Map.of());
        assertEquals(List.of(2, 2, 2, 2), context.getMovementPool());
    }

    /**
     * Verifies that movement and exact bearing off both remain available
     * when either first action permits all four double values to be used.
     *
     * <p>The checker at point 19 uses three values: two movements
     * through points 21 and 23, followed by bearing off.
     * The checker initially at point 23 uses one value for exact bearing off.</p>
     */
    @Test
    void doubleKeepsAllFirstActionsAllowingMaximumUsage() {
        MoveContext context = context(
                WHITE, Map.of(19, 1, 23, 1), Map.of(), 0, List.of(2, 2, 2, 2));

        resolver.resolve(context);

        assertOptions(context,
                Map.of(19, Set.of(2)),
                Map.of(23, Set.of(2)),
                Map.of());
    }

    /**
     * Verifies that resolution preserves all input fields
     * and previously supplied option collections.
     */
    @Test
    void preservesInputDataAndReplacesPreviousOptions() {
        Map<Integer, Set<Integer>> previousMoves = Map.of(1, Set.of(6));
        Map<Integer, Set<Integer>> previousBearOffs = Map.of(24, Set.of(6));
        Map<Integer, Set<Integer>> previousEntries = Map.of(6, Set.of(6));

        MoveContext context = context(
                WHITE, Map.of(8, 1), Map.of(11, 1), 0, List.of(3, 2))
                .setAllowedMoves(previousMoves)
                .setAllowedBearOffs(previousBearOffs)
                .setAllowedEntries(previousEntries);

        var boardState = context.getBoardState();
        var cells = boardState.getCells();
        var bars = boardState.getBars();
        var borneOff = boardState.getBorneOff();
        List<Integer> diceValues = context.getDiceValues();
        List<Integer> movementPool = context.getMovementPool();

        resolver.resolve(context);

        assertSame(boardState, context.getBoardState());
        assertSame(cells, context.getBoardState().getCells());
        assertSame(bars, context.getBoardState().getBars());
        assertSame(borneOff, context.getBoardState().getBorneOff());
        assertSame(diceValues, context.getDiceValues());
        assertSame(movementPool, context.getMovementPool());
        assertEquals(WHITE, context.getActiveColor());

        assertEquals(Map.of(1, Set.of(6)), previousMoves);
        assertEquals(Map.of(24, Set.of(6)), previousBearOffs);
        assertEquals(Map.of(6, Set.of(6)), previousEntries);

        assertOptions(context, Map.of(8, Set.of(2, 3)), Map.of(), Map.of());
        assertEquals(1, cells.get(ADDRESSING.toBoardIndex(WHITE, 8)).size());
        assertEquals(StandardChecker.BLACK_MAN,
                cells.get(ADDRESSING.toBoardIndex(WHITE, 11)).getLast());
    }

    /**
     * Verifies immutability of all output maps and their non-empty nested sets.
     */
    @Test
    void outputMapsAndNestedSetsAreUnmodifiable() {
        MoveContext movement = context(
                WHITE, Map.of(8, 1), Map.of(), 0, List.of(3));
        MoveContext entry = context(
                WHITE, Map.of(), Map.of(), 1, List.of(3));
        MoveContext bearOff = context(
                WHITE, Map.of(24, 1), Map.of(), 0, List.of(1));

        resolver.resolve(movement);
        resolver.resolve(entry);
        resolver.resolve(bearOff);

        assertThrows(UnsupportedOperationException.class,
                () -> movement.getAllowedMoves().put(1, Set.of(1)));
        assertThrows(UnsupportedOperationException.class,
                () -> movement.getAllowedMoves().get(8).add(1));

        assertThrows(UnsupportedOperationException.class,
                () -> entry.getAllowedEntries().put(1, Set.of(1)));
        assertThrows(UnsupportedOperationException.class,
                () -> entry.getAllowedEntries().get(3).add(1));

        assertThrows(UnsupportedOperationException.class,
                () -> bearOff.getAllowedBearOffs().put(19, Set.of(6)));
        assertThrows(UnsupportedOperationException.class,
                () -> bearOff.getAllowedBearOffs().get(24).add(2));
    }

    /*
     * Creates a context using active-side route coordinates for both maps.
     * Unplaced checkers are put into the corresponding borne-off cells,
     * keeping fifteen checkers per side.
     */
    private static MoveContext context(
            StandardCheckerColor color,
            Map<Integer, Integer> ownStacks,
            Map<Integer, Integer> opponentStacks,
            int barCount,
            List<Integer> movementPool) {

        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        StandardChecker ownChecker = color == WHITE
                ? StandardChecker.WHITE_MAN : StandardChecker.BLACK_MAN;
        StandardChecker opposingChecker = opponent == WHITE
                ? StandardChecker.WHITE_MAN : StandardChecker.BLACK_MAN;

        StandardBoard<StandardChecker> board = new StandardBoard<>();

        ownStacks.forEach((point, count) ->
                board.getCell(ADDRESSING.toBoardIndex(color, point))
                        .setState(Collections.nCopies(count, ownChecker)));

        opponentStacks.forEach((point, count) ->
                board.getCell(ADDRESSING.toBoardIndex(color, point))
                        .setState(Collections.nCopies(count, opposingChecker)));

        board.getBar(color).setState(Collections.nCopies(barCount, ownChecker));

        int ownCount = ownStacks.values().stream().mapToInt(Integer::intValue).sum();
        int opponentCount = opponentStacks.values().stream().mapToInt(Integer::intValue).sum();

        board.getBorneOff(color)
                .setState(Collections.nCopies(15 - ownCount - barCount, ownChecker));
        board.getBorneOff(opponent)
                .setState(Collections.nCopies(15 - opponentCount, opposingChecker));

        return MoveContext.builder()
                .boardState(board.getState())
                .activeColor(color)
                .diceValues(movementPool.isEmpty()
                        ? List.of()
                        : List.of(movementPool.getFirst(), movementPool.getLast()))
                .movementPool(movementPool)
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .allowedEntries(Map.of())
                .build();
    }

    /*
     * Compares all three option maps against the scenario expectations.
     */
    private static void assertOptions(
            MoveContext context,
            Map<Integer, Set<Integer>> moves,
            Map<Integer, Set<Integer>> bearOffs,
            Map<Integer, Set<Integer>> entries) {

        assertAll(
                () -> assertEquals(moves, context.getAllowedMoves()),
                () -> assertEquals(bearOffs, context.getAllowedBearOffs()),
                () -> assertEquals(entries, context.getAllowedEntries()));
    }
}
