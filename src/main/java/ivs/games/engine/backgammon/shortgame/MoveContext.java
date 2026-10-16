package ivs.games.engine.backgammon.shortgame;

import ivs.games.accessories.backgammonboard.BoardState;
import ivs.games.accessories.backgammonboard.Checker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Holds input data and calculated action options for short backgammon.
 * Supplied references are stored without validation or copying.
 * Creating code must provide immutable board snapshots and collections.
 */
@Data
@Accessors(chain = true)
@Builder(toBuilder = true)
class MoveContext {

    BoardState<? extends Checker<?, ?>> boardState;

    StandardCheckerColor activeColor;

    List<Integer> diceValues;

    List<Integer> movementPool;

    /*
     * Movement and bearing-off maps use source route points as keys
     * and permitted die values as set elements.
     */
    Map<Integer, Set<Integer>> allowedMoves;

    Map<Integer, Set<Integer>> allowedBearOffs;

    /*
     * Entry options use destination route points as keys
     * and permitted die values as set elements.
     */
    Map<Integer, Set<Integer>> allowedEntries;
}
