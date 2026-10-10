package ivs.games.engine.backgammon.state;

import ivs.games.accessories.backgammonboard.BoardState;
import ivs.games.accessories.backgammonboard.Checker;
import ivs.games.accessories.backgammonboard.CheckerColor;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Stores game data shared by backgammon variants.
 *
 * <p>Supplied references are stored without validation or copying.
 * The creating code is responsible for data consistency and immutability of referenced objects.
 *
 * <p>Subclasses may add variant-specific properties and must preserve the same immutability contract.
 */
@Getter
@SuperBuilder(toBuilder = true)
public class GameState {

    /**
     * The board snapshot, including playing cells, bars and borne-off cells.
     */
    private final BoardState<? extends Checker<?, ?>> board;

    /**
     * The color of the side whose turn it is.
     */
    private final StandardCheckerColor activeColor;

    /**
     * The game phase determining which action types are permitted.
     */
    private final GamePhase gamePhase;

    /**
     * The two values of the original roll without expanding doubles.
     *
     * <p>Empty while awaiting dice.
     */
    private final List<Integer> diceValues;

    /**
     * The remaining movement values for the current turn.
     *
     * <p>A double initially provides four equal values. Each movement consumes one occurrence.
     * Empty while awaiting dice.
     */
    private final List<Integer> remainingDiceValues;

    /**
     * The winner's color, or {@code null} while the game is in progress.
     */
    private final CheckerColor winner;
}
