package ivs.games.engine.backgammon.state;

import ivs.games.accessories.backgammonboard.BoardState;
import ivs.games.accessories.backgammonboard.Checker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
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
 */
@Getter
@SuperBuilder(toBuilder = true)
public class GameState {

    /** The board snapshot. */
    private final BoardState<? extends Checker<?, ?>> board;

    /** The color of the active side. */
    private final StandardCheckerColor activeColor;

    /** The current game phase. */
    private final GamePhase gamePhase;

    /** The two values from the current dice roll, or an empty list while awaiting dice. */
    private final List<Integer> diceValues;

    /** The remaining movement values, including repeated values for doubles. */
    private final List<Integer> remainingDiceValues;

    /** Legal movements grouped by source playing cell index, including bearing-off options. */
    private final Map<Integer, Set<AllowedMove>> allowedMoves;

    /** The winner's color, or null if the game has not been won. */
    private final StandardCheckerColor winner;
}
