package ivs.games.engine.backgammon.longgame;

import ivs.games.accessories.backgammonboard.BoardState;
import ivs.games.accessories.backgammonboard.Checker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Contains current data required to resolve legal movements in long backgammon.
 *
 * <p>Stores supplied references without validation or copying. Creating code is responsible
 * for data consistency and immutability of the board snapshot and lists.
 */
@Data
@Accessors(chain = true)
@Builder(toBuilder = true)
class MoveContext {

    /** The current board snapshot. */
    BoardState<? extends Checker<?, ?>> board;

    /** The color of the side whose movements are being resolved. */
    StandardCheckerColor activeColor;

    /** The two original die values for the current turn. */
    List<Integer> diceValues;

    /** The remaining movement values, including repeated values for doubles. */
    List<Integer> movementPool;

    /** The number of head departures already performed during the current turn. */
    int headMoves;

    /** Whether the active side is taking its first turn. */
    boolean firstTurn;

    /**
     * Creates a movement context from the supplied game state.
     *
     * <p>Immutable board and collection references are shared with the state.
     *
     * @param state the game state supplying the context data
     * @return the populated movement context
     * @throws NullPointerException if state is null
     */
    static MoveContext from(@NonNull LongGameState state) {
        StandardCheckerColor activeColor = state.getActiveColor();

        return MoveContext.builder()
                .board(state.getBoard())
                .activeColor(activeColor)
                .diceValues(state.getDiceValues())
                .movementPool(state.getRemainingDiceValues())
                .headMoves(state.getHeadMoves())
                .firstTurn(state.getFirstTurns().get(activeColor))
                .build();
    }
}
