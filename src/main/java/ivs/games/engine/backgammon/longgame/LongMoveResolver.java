package ivs.games.engine.backgammon.longgame;

import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ivs.games.engine.backgammon.longgame.BoardGeometry.ADDRESSING;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.BEAR_OFF_POINT;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.HEAD_POINT;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.HOME_START_POINT;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.LAST_POINT;

/**
 * Calculates legal movement options for the active side in long backgammon.
 *
 * <p>Applies the following rules:
 * <ul>
 *     <li>Only the active side's top checker can be moved from an occupied point.</li>
 *     <li>Each action consumes one remaining die value. Doubles are represented by four equal pool values.</li>
 *     <li>Checkers move toward increasing side-relative route points.</li>
 *     <li>A destination must be empty or occupied by the active side. Hitting is not permitted.</li>
 *     <li>One checker may leave the head per turn. On the side's first turn, doubles of three,
 *         four or six permit two head departures.</li>
 *     <li>Bearing off is permitted only when all active checkers remaining on the board are in the home.</li>
 *     <li>An exact die value permits bearing off from the corresponding home point.
 *         An oversize value permits bearing off only from the occupied point furthest from the exit.</li>
 *     <li>A movement must not form six consecutive occupied points with no opposing checker beyond
 *         the prime on the opponent's route. A borne-off opposing checker counts as having passed it.</li>
 *     <li>The maximum possible number of remaining die values must be used.
 *         If only one action is possible, the highest playable value must be used.</li>
 * </ul>
 *
 * <p>The first-turn head exception applies to either side without an additional check
 * that two head departures are necessary. Prime checks examine positions after each
 * individual movement and do not wrap past the opponent's bearing-off boundary.
 *
 * <p>The supplied context must describe a valid long backgammon position with no mixed-color
 * stacks or checkers on the bar. The original roll contains two values; the movement pool
 * contains up to four remaining values, each between one and six.
 *
 * <p>The resolver checks continuations using independent working boards and contexts.
 * It retains only permitted first actions; the player chooses among those actions.
 *
 * <p>The supplied board snapshot and context input fields remain unchanged.
 * Both context option maps are replaced with immutable maps containing immutable, non-empty sets.
 * Map keys are source route points of the active side; set elements are permitted die values.
 *
 * <p>The resolver retains no game data and is thread-safe when concurrent calls use separate contexts.
 */
class LongMoveResolver {

    /** The number of consecutive occupied points forming a prime. */
    private static final int PRIME_LENGTH = 6;

    /**
     * Contains mutable movement and bearing-off candidates during rule validation.
     *
     * @param moves    die values for movements, grouped by source route point
     * @param bearOffs die values for bearing off, grouped by source route point
     */
    private record MoveCandidates(
            Map<Integer, Set<Integer>> moves,
            Map<Integer, Set<Integer>> bearOffs) {

        /** Creates empty mutable candidate maps. */
        private MoveCandidates() {
            this(new HashMap<>(), new HashMap<>());
        }
    }

    /**
     * Contains an action and the maximum number of movement values usable through it.
     *
     * @param from       the source route point
     * @param diceValue  the first consumed movement value
     * @param bearOff    whether the first action removes a checker from the board
     * @param usedValues the maximum number of consumed values, including the first action
     */
    private record ScoredMove(int from, int diceValue, boolean bearOff, int usedValues) {
    }

    /**
     * Generates legal first actions, checks mandatory dice usage and stores the resulting options.
     *
     * @param context the current movement calculation context
     * @throws NullPointerException if context is null
     */
    void resolve(@NonNull MoveContext context) {
        MoveCandidates candidates = new MoveCandidates();

        calculateSingleMoves(context, candidates);
        checkHeadMove(context, candidates);
        checkBearOff(context, candidates);
        checkBlocking(context, candidates);
        checkDiceUsage(context, candidates);

        context
                .setAllowedMoves(makeImmutable(candidates.moves()))
                .setAllowedBearOffs(makeImmutable(candidates.bearOffs()));
    }

    /**
     * Retains first actions allowing maximum use of the remaining movement values.
     *
     * <p>If only one value can be used, retains actions consuming the highest playable value.
     *
     * @param context    the current movement calculation context
     * @param candidates the mutable candidates to filter
     */
    private void checkDiceUsage(MoveContext context, MoveCandidates candidates) {
        List<ScoredMove> scores = new ArrayList<>();

        // Evaluate continuations for ordinary movements and bearing off.
        scoreMoves(context, candidates.moves(), false, scores);
        scoreMoves(context, candidates.bearOffs(), true, scores);

        int maximum = scores.stream().mapToInt(ScoredMove::usedValues).max().orElse(0);
        int highest = scores.stream().mapToInt(ScoredMove::diceValue).max().orElse(0);

        // Rebuild the maps using only first actions that satisfy dice usage rules.
        candidates.moves().clear();
        candidates.bearOffs().clear();

        for (ScoredMove score : scores) {
            if (score.usedValues() != maximum || maximum == 1 && score.diceValue() != highest) {
                continue;
            }

            Map<Integer, Set<Integer>> target = score.bearOff() ? candidates.bearOffs() : candidates.moves();
            target.computeIfAbsent(score.from(), ignored -> new HashSet<>()).add(score.diceValue());
        }
    }

    /**
     * Evaluates the number of values usable by each action and its best continuation.
     *
     * @param context the current movement calculation context
     * @param options the actions to evaluate
     * @param bearOff whether the actions remove checkers from the board
     * @param scores  the list receiving evaluated actions
     */
    private void scoreMoves(
            MoveContext context, Map<Integer, Set<Integer>> options, boolean bearOff, List<ScoredMove> scores) {
        options.forEach((from, values) -> {
            for (int diceValue : values) {
                MoveContext next = afterMove(context, from, diceValue, bearOff);
                int usedValues = 1 + maximumUsage(next);
                scores.add(new ScoredMove(from, diceValue, bearOff, usedValues));
            }
        });
    }

    /**
     * Finds the maximum number of movement values usable from the supplied position.
     *
     * @param context the working position and remaining movement pool
     * @return the maximum number of usable values
     */
    private int maximumUsage(MoveContext context) {
        if (context.getMovementPool().isEmpty()) {
            return 0;
        }

        MoveCandidates moveCandidates = new MoveCandidates();

        calculateSingleMoves(context, moveCandidates);
        checkHeadMove(context, moveCandidates);
        checkBearOff(context, moveCandidates);
        checkBlocking(context, moveCandidates);

        int maximum = maximumUsage(context, moveCandidates.moves(), false);

        // No continuation can use more values than remain in the pool.
        if (maximum == context.getMovementPool().size()) {
            return maximum;
        }

        return Math.max(maximum, maximumUsage(context, moveCandidates.bearOffs(), true));
    }

    /**
     * Finds the best continuation among a group of individually legal actions.
     *
     * @param context the working position and remaining movement pool
     * @param options the actions to evaluate
     * @param bearOff whether the actions remove checkers from the board
     * @return the maximum number of usable values, including the first action
     */
    private int maximumUsage(MoveContext context, Map<Integer, Set<Integer>> options, boolean bearOff) {
        int maximum = 0;

        for (var entry : options.entrySet()) {
            for (int diceValue : entry.getValue()) {
                MoveContext next = afterMove(context, entry.getKey(), diceValue, bearOff);
                maximum = Math.max(maximum, 1 + maximumUsage(next));

                if (maximum == context.getMovementPool().size()) {
                    return maximum;
                }
            }
        }

        return maximum;
    }

    /**
     * Creates an independent working context after an individually legal action.
     *
     * <p>Preserves the active side, original roll and first-turn status.
     * Consumes one movement value and updates head departures for ordinary movements.
     *
     * @param context   the original working context
     * @param from      the source route point
     * @param diceValue the movement value to consume
     * @param bearOff   whether the action removes a checker from the board
     * @return the updated working context
     */
    private MoveContext afterMove(MoveContext context, int from, int diceValue, boolean bearOff) {
        StandardCheckerColor color = context.getActiveColor();
        var board = new StandardBoard<>(context.getBoardState());
        int sourceIndex = ADDRESSING.toBoardIndex(color, from);

        // Apply the action to an independent board.
        if (bearOff) {
            board.moveToBorneOff(sourceIndex);
        } else {
            board.move(sourceIndex, ADDRESSING.toBoardIndex(color, from + diceValue));
        }

        // Consume one occurrence, preserving the remaining occurrences of a double.
        List<Integer> movementPool = new ArrayList<>(context.getMovementPool());
        movementPool.remove(Integer.valueOf(diceValue));

        int headMoves = context.getHeadMoves();
        if (!bearOff && from == HEAD_POINT) {
            headMoves++;
        }

        return context.toBuilder()
                .boardState(board.getState())
                .movementPool(List.copyOf(movementPool))
                .headMoves(headMoves)
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .build();
    }

    /**
     * Removes movements that would form a prime with no opposing checker beyond it.
     *
     * @param context    the context containing the board and active color
     * @param candidates the mutable candidates to filter
     */
    private void checkBlocking(MoveContext context, MoveCandidates candidates) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(context.getActiveColor());

        // A borne-off opposing checker has already passed every possible prime.
        if (!context.getBoardState().getBorneOff().get(opponent).isEmpty()) {
            return;
        }

        // Determine the furthest point reached by any opposing checker.
        int furthestOpponent = furthestOpponent(context, opponent);

        // Check each movement without modifying or copying the board.
        candidates.moves().forEach((from, values) ->
                values.removeIf(diceValue ->
                        hasForbiddenPrime(context, opponent, from, from + diceValue, furthestOpponent)));

        // Remove source points with no remaining movement options.
        candidates.moves().entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Finds the furthest occupied point along the opponent's route.
     *
     * @param context  the context containing the board
     * @param opponent the opposing side
     * @return the furthest opposing route point, or zero if no opposing checker remains
     */
    private int furthestOpponent(MoveContext context, StandardCheckerColor opponent) {
        var cells = context.getBoardState().getCells();

        for (int point = LAST_POINT; point >= HEAD_POINT; point--) {
            var cell = cells.get(ADDRESSING.toBoardIndex(opponent, point));
            if (!cell.isEmpty() && cell.getLast().getColor() == opponent) {
                return point;
            }
        }

        return 0;
    }

    /**
     * Checks for a forbidden prime in the position after a proposed movement.
     *
     * <p>Points are examined along the opponent's route. A prime is forbidden when
     * no opposing checker has reached a point beyond its end.
     * The route does not wrap past the opponent's bearing-off boundary.
     *
     * @param context          the current movement calculation context
     * @param opponent         the opposing side
     * @param from             the source point on the active side's route
     * @param to               the destination point on the active side's route
     * @param furthestOpponent the furthest occupied point on the opponent's route
     * @return true if the resulting position contains a forbidden prime
     */
    private boolean hasForbiddenPrime(
            MoveContext context, StandardCheckerColor opponent, int from, int to, int furthestOpponent) {
        int consecutive = 0;

        for (int point = HEAD_POINT; point <= LAST_POINT; point++) {
            int index = ADDRESSING.toBoardIndex(opponent, point);
            int activePoint = ADDRESSING.toRoutePoint(context.getActiveColor(), index);

            if (occupiedAfterMove(context, activePoint, from, to)) {
                consecutive++;
            } else {
                consecutive = 0;
            }

            if (consecutive >= PRIME_LENGTH && furthestOpponent <= point) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether a point is occupied by the active side after a proposed movement.
     *
     * @param context the context containing the original board
     * @param point   the point to inspect on the active side's route
     * @param from    the source route point
     * @param to      the destination route point
     * @return true if an active checker occupies the point after the movement
     */
    private boolean occupiedAfterMove(MoveContext context, int point, int from, int to) {
        // The destination receives an active checker.
        if (point == to) {
            return true;
        }

        StandardCheckerColor color = context.getActiveColor();
        var cell = context.getBoardState().getCells().get(ADDRESSING.toBoardIndex(color, point));

        // The source remains occupied only if another checker stays behind.
        if (point == from) {
            return cell.size() > 1;
        }

        // All other cells retain their original contents.
        return !cell.isEmpty() && cell.getLast().getColor() == color;
    }

    /**
     * Removes oversize bearing-off values except at the occupied home point furthest from the exit.
     *
     * <p>Candidates are generated only when all active checkers remaining on the board are in the home.
     * Exact bearing-off values are retained at every occupied home point.
     *
     * @param context    the context containing the board and active color
     * @param candidates the mutable candidates to filter
     */
    private void checkBearOff(MoveContext context, MoveCandidates candidates) {
        // No bearing-off candidates require checking.
        if (candidates.bearOffs().isEmpty()) {
            return;
        }

        // Find the occupied home point furthest from bearing off.
        var cells = context.getBoardState().getCells();
        StandardCheckerColor color = context.getActiveColor();
        int firstOccupied = BEAR_OFF_POINT;

        for (int point = HOME_START_POINT; point <= LAST_POINT; point++) {
            var cell = cells.get(ADDRESSING.toBoardIndex(color, point));
            if (!cell.isEmpty() && cell.getLast().getColor() == color) {
                firstOccupied = point;
                break;
            }
        }

        // Keep exact values everywhere and oversize values only at the furthest occupied point.
        int furthestPoint = firstOccupied;
        candidates.bearOffs().forEach((from, values) ->
                values.removeIf(diceValue -> from + diceValue != BEAR_OFF_POINT && from != furthestPoint));

        // Remove source points with no remaining bearing-off options.
        candidates.bearOffs().entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Removes head departure candidates when the current turn's limit has been reached.
     *
     * <p>The first-turn doubles of three, four or six permit two head departures.
     * Other rolls permit one head departure.
     *
     * @param context    the context containing the original roll and head departure count
     * @param candidates the mutable candidates to filter
     */
    private void checkHeadMove(MoveContext context, MoveCandidates candidates) {
        // The first head departure is permitted on every turn.
        int headMoves = context.getHeadMoves();
        if (headMoves == 0) {
            return;
        }

        // No turn permits more than two departures; regular turns permit only one.
        if (headMoves >= 2 || !context.isFirstTurn()) {
            candidates.moves().remove(HEAD_POINT);
            return;
        }

        // After one departure on the first turn, check the original roll for an exception.
        int first = context.getDiceValues().getFirst();
        int second = context.getDiceValues().getLast();
        boolean allowsSecond = first == second && (first == 3 || first == 4 || first == 6);

        // Remove all head departure options unless the roll permits a second departure.
        if (!allowsSecond) {
            candidates.moves().remove(HEAD_POINT);
        }
    }

    /**
     * Generates single-die candidates, excluding destinations occupied by the opponent.
     *
     * <p>Bearing-off candidates are collected only when all active checkers remaining
     * on the board are in the home. Oversize die rules are checked separately.
     *
     * @param context    the current board, active color and remaining movement pool
     * @param candidates the mutable candidates receiving generated options
     */
    private void calculateSingleMoves(MoveContext context, MoveCandidates candidates) {
        Set<Integer> diceValues = new HashSet<>(context.getMovementPool());
        var cells = context.getBoardState().getCells();
        StandardCheckerColor color = context.getActiveColor();

        boolean allHome = allCheckersHome(context);

        for (int from = HEAD_POINT; from <= LAST_POINT; from++) {
            // Consider only cells with an active side's checker on top.
            var source = cells.get(ADDRESSING.toBoardIndex(color, from));
            if (source.isEmpty() || source.getLast().getColor() != color) {
                continue;
            }

            for (int diceValue : diceValues) {
                int to = from + diceValue;

                // Collect bearing-off candidates only when all active checkers are in the home.
                if (to > LAST_POINT) {
                    if (allHome) {
                        candidates.bearOffs().computeIfAbsent(from, ignored -> new HashSet<>()).add(diceValue);
                    }
                    continue;
                }

                // Exclude destinations occupied by the opponent.
                var destination = cells.get(ADDRESSING.toBoardIndex(color, to));
                if (!destination.isEmpty() && destination.getLast().getColor() != color) {
                    continue;
                }

                // Collect movements to empty cells or cells occupied by the active side.
                candidates.moves().computeIfAbsent(from, ignored -> new HashSet<>()).add(diceValue);
            }
        }
    }

    /**
     * Checks that no active checker remains on a route point outside the home.
     *
     * @param context the context containing the board and active color
     * @return true if all active checkers remaining on the board are in the home
     */
    private boolean allCheckersHome(MoveContext context) {
        var cells = context.getBoardState().getCells();
        StandardCheckerColor color = context.getActiveColor();

        for (int point = HEAD_POINT; point < HOME_START_POINT; point++) {
            var cell = cells.get(ADDRESSING.toBoardIndex(color, point));
            if (!cell.isEmpty() && cell.getLast().getColor() == color) {
                return false;
            }
        }

        return true;
    }

    /**
     * Copies calculated options into immutable maps with immutable nested sets.
     *
     * @param source the calculated options containing no empty sets
     * @return the immutable options
     */
    private Map<Integer, Set<Integer>> makeImmutable(Map<Integer, Set<Integer>> source) {
        Map<Integer, Set<Integer>> result = new HashMap<>();
        source.forEach((point, values) -> result.put(point, Set.copyOf(values)));
        return Map.copyOf(result);
    }
}
