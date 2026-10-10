package ivs.games.engine.backgammon.state;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Value;

/**
 * Defines the starting side of a standard backgammon game.
 *
 * <p>The engine creates both sides and the initial checker arrangement.
 * The session determines which side starts.
 */
@SuppressWarnings("ClassCanBeRecord")
@Value
public class GameSetup {

    /**
     * The color of the side that takes the first turn.
     */
    StandardCheckerColor startingColor;
}
