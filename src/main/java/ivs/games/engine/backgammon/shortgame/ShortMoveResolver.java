package ivs.games.engine.backgammon.shortgame;

import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ivs.games.engine.backgammon.shortgame.BoardGeometry.ADDRESSING;
import static ivs.games.engine.backgammon.shortgame.BoardGeometry.BEAR_OFF_POINT;
import static ivs.games.engine.backgammon.shortgame.BoardGeometry.HOME_START_POINT;
import static ivs.games.engine.backgammon.shortgame.BoardGeometry.LAST_POINT;

/**
 * Calculates legal actions for the active side in short backgammon.
 *
 * <p>While active checkers remain on the bar, only entries are permitted.
 * Otherwise, ordinary movements and, when all active checkers are home,
 * bearing-off actions are considered.</p>
 *
 * <p>A destination occupied by two or more opposing checkers is blocked.
 * A single opposing checker is hit as part of movement or entry.</p>
 *
 * <p>Actions must allow the maximum possible number of remaining movement
 * values to be used. If only one value can be used, the highest playable
 * value is required. Doubles must already be expanded in the movement pool.</p>
 *
 * <p>Route points increase from one to twenty-four.
 * The supplied position must contain no mixed-color stacks.
 * The movement pool contains up to four values, each between one and six.</p>
 *
 * <p>Resolution preserves input board and collection references.
 * All three option maps are replaced with immutable maps containing
 * immutable, non-empty sets.</p>
 *
 * <p>The resolver retains no game data and is thread-safe when concurrent
 * calls use separate contexts.</p>
 */
class ShortMoveResolver {

    /*
     * Distinguishes movements, bar entries and bearing-off actions
     * during continuation analysis.
     */
    private enum ActionType {
        MOVE,
        ENTER,
        BEAR_OFF
    }

    /*
     * Describes an individually legal action.
     * Point identifies the source for movement and bearing off,
     * or the destination for entry.
     */
    private record Candidate(ActionType type, int point, int diceValue) {
    }

    /**
     * Calculates and stores permitted first actions.
     *
     * <p>Each retained action permits a continuation using the maximum
     * possible number of remaining movement values.</p>
     *
     * @param context the current board and movement data
     * @throws NullPointerException if context is null
     */
    void resolve(@NonNull MoveContext context) {
        List<Candidate> candidates = calculateCandidates(context);
        List<Candidate> bestCandidates = new ArrayList<>(candidates.size());

        int poolSize = context.getMovementPool().size();
        int maximum = 0;

        for (Candidate candidate : candidates) {
            int usedValues;

            if (poolSize == 1) {
                usedValues = 1;
            } else {
                MoveContext next = afterAction(context, candidate);
                usedValues = 1 + maximumUsage(next);
            }

            if (usedValues > maximum) {
                maximum = usedValues;
                bestCandidates.clear();
            }

            if (usedValues == maximum) {
                bestCandidates.add(candidate);
            }
        }

        /*
         * The higher-value rule applies only when at most one action
         * can be performed during the remaining turn.
         */
        if (maximum == 1) {
            int highest = bestCandidates.stream()
                    .mapToInt(Candidate::diceValue)
                    .max()
                    .orElse(0);

            bestCandidates.removeIf(candidate -> candidate.diceValue() != highest);
        }

        Map<Integer, Set<Integer>> moves = new HashMap<>();
        Map<Integer, Set<Integer>> bearOffs = new HashMap<>();
        Map<Integer, Set<Integer>> entries = new HashMap<>();

        for (Candidate candidate : bestCandidates) {
            Map<Integer, Set<Integer>> options = switch (candidate.type()) {
                case MOVE -> moves;
                case ENTER -> entries;
                case BEAR_OFF -> bearOffs;
            };

            options.computeIfAbsent(candidate.point(), ignored -> new HashSet<>())
                    .add(candidate.diceValue());
        }

        context.setAllowedMoves(makeImmutable(moves))
                .setAllowedBearOffs(makeImmutable(bearOffs))
                .setAllowedEntries(makeImmutable(entries));
    }

    /*
     * Generates actions permitted by individual movement values.
     * Mandatory use of multiple values is checked separately.
     * A non-empty active bar restricts the result to entry actions.
     */
    private List<Candidate> calculateCandidates(MoveContext context) {
        List<Candidate> candidates = new ArrayList<>();

        if (context.getMovementPool().isEmpty()) {
            return candidates;
        }

        var boardState = context.getBoardState();
        var color = context.getActiveColor();
        var cells = boardState.getCells();
        Set<Integer> values = new HashSet<>(context.getMovementPool());

        if (!boardState.getBars().get(color).isEmpty()) {
            for (int diceValue : values) {
                int destinationIndex = ADDRESSING.toBoardIndex(color, diceValue);
                var destination = cells.get(destinationIndex);

                if (destination.isEmpty()
                        || destination.getLast().getColor() == color
                        || destination.size() == 1) {
                    candidates.add(new Candidate(ActionType.ENTER, diceValue, diceValue));
                }
            }

            return candidates;
        }

        /*
         * The earliest occupied active route point determines whether
         * every active checker is home and where oversize bearing off is allowed.
         */
        int firstOccupied = BEAR_OFF_POINT;

        for (int point = 1; point <= LAST_POINT; point++) {
            var cell = cells.get(ADDRESSING.toBoardIndex(color, point));

            if (!cell.isEmpty() && cell.getLast().getColor() == color) {
                firstOccupied = point;
                break;
            }
        }

        boolean allHome = firstOccupied >= HOME_START_POINT;

        for (int from = firstOccupied; from <= LAST_POINT; from++) {
            var source = cells.get(ADDRESSING.toBoardIndex(color, from));

            if (source.isEmpty() || source.getLast().getColor() != color) {
                continue;
            }

            for (int diceValue : values) {
                int to = from + diceValue;

                if (to >= BEAR_OFF_POINT) {
                    if (allHome && (to == BEAR_OFF_POINT || from == firstOccupied)) {
                        candidates.add(new Candidate(ActionType.BEAR_OFF, from, diceValue));
                    }

                    continue;
                }

                var destination = cells.get(ADDRESSING.toBoardIndex(color, to));

                if (destination.isEmpty()
                        || destination.getLast().getColor() == color
                        || destination.size() == 1) {
                    candidates.add(new Candidate(ActionType.MOVE, from, diceValue));
                }
            }
        }

        return candidates;
    }

    /*
     * Finds the maximum number of movement values usable from this position.
     * A legal action with one remaining value requires no working board.
     * Search stops when every remaining value can be consumed.
     */
    private int maximumUsage(MoveContext context) {
        int poolSize = context.getMovementPool().size();

        if (poolSize == 0) {
            return 0;
        }

        List<Candidate> candidates = calculateCandidates(context);

        if (candidates.isEmpty()) {
            return 0;
        }

        if (poolSize == 1) {
            return 1;
        }

        int maximum = 0;

        for (Candidate candidate : candidates) {
            MoveContext next = afterAction(context, candidate);
            int usedValues = 1 + maximumUsage(next);

            maximum = Math.max(maximum, usedValues);

            if (maximum == poolSize) {
                return maximum;
            }
        }

        return maximum;
    }

    /*
     * Applies an individually legal action to an independent working board.
     * Hits are performed before movement or entry.
     * One occurrence of the selected movement value is consumed.
     */
    private MoveContext afterAction(MoveContext context, Candidate candidate) {
        var color = context.getActiveColor();
        var board = new StandardBoard<>(context.getBoardState());

        if (candidate.type() == ActionType.BEAR_OFF) {
            int sourceIndex = ADDRESSING.toBoardIndex(color, candidate.point());
            board.moveToBorneOff(sourceIndex);
        } else {
            int to = candidate.type() == ActionType.ENTER
                    ? candidate.point()
                    : candidate.point() + candidate.diceValue();

            int destinationIndex = ADDRESSING.toBoardIndex(color, to);
            var destination = board.getCell(destinationIndex);

            if (!destination.isEmpty() && destination.getTopCheckerColor() != color) {
                // Candidate generation has already ensured that the opposing checker is alone.
                board.moveToBar(destinationIndex);
            }

            if (candidate.type() == ActionType.ENTER) {
                board.moveFromBar(color, destinationIndex);
            } else {
                int sourceIndex = ADDRESSING.toBoardIndex(color, candidate.point());
                board.move(sourceIndex, destinationIndex);
            }
        }

        List<Integer> remaining = new ArrayList<>(context.getMovementPool());
        remaining.remove(Integer.valueOf(candidate.diceValue()));

        return context.toBuilder()
                .boardState(board.getState())
                .movementPool(List.copyOf(remaining))
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .allowedEntries(Map.of())
                .build();
    }

    /*
     * Copies calculated options into an immutable map with immutable value sets.
     */
    private Map<Integer, Set<Integer>> makeImmutable(Map<Integer, Set<Integer>> source) {
        return source.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> Set.copyOf(entry.getValue())));
    }
}
