package ivs.games.engine.backgammon.longgame;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.state.GameState;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Stores game data specific to long backgammon.
 *
 * <p>Supplied references are stored without validation or copying.
 * The creating code is responsible for data consistency and immutability of referenced objects.
 */
@Getter
@SuperBuilder(toBuilder = true)
public final class LongGameState extends GameState {

    /**
     * The number of checkers moved off the active side's head during the current turn.
     *
     * <p>Reset to zero when the turn ends.
     */
    private final int headMoves;

    /**
     * Whether each side has not yet completed its first turn, indexed by color.
     *
     * <p>Contains both standard colors. Each flag is initially true and becomes false
     * when the corresponding side completes its first turn, including a turn without legal movements.
     */
    private final Map<StandardCheckerColor, Boolean> firstTurns;
}
