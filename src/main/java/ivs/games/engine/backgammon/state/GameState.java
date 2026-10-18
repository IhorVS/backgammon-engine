package ivs.games.engine.backgammon.state;

import ivs.games.accessories.backgammonboard.BoardState;
import ivs.games.accessories.backgammonboard.Checker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.GameEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores an immutable game snapshot shared by backgammon variants.
 *
 * <p>Stores supplied references without validation or copying. Creating code is responsible
 * for the correctness and immutability of all supplied data, including nested collections.
 * The events list describes only the transition that produced this snapshot.</p>
 */
@Getter
@SuperBuilder(toBuilder = true)
public class GameState {

    /** The board snapshot using physical cell indices. */
    private final BoardState<? extends Checker<?, ?>> board;

    /** The color of the active side. */
    private final StandardCheckerColor activeColor;

    /** The current game phase. */
    private final GamePhase gamePhase;

    /** The two values from the current dice roll, or an empty list while awaiting dice. */
    private final List<Integer> diceValues;

    /**
     * The remaining movement values, including repeated values for doubles.
     *
     * <p>Cleared when a turn ends. Unused values may remain after victory;
     * system abort preserves the pool present at cancellation.</p>
     */
    private final List<Integer> remainingDiceValues;

    /** Legal die values for movements, grouped by the active side's source route point. */
    private final Map<Integer, Set<Integer>> allowedMoves;

    /** Legal die values for bearing off, grouped by the active side's source route point. */
    private final Map<Integer, Set<Integer>> allowedBearOffs;

    /** The winner's color, or null if the game has not been won. */
    private final StandardCheckerColor winner;

    /**
     * Events produced by the operation that created this state, in occurrence order.
     */
    @Builder.Default
    private final List<GameEvent> events = List.of();
}
