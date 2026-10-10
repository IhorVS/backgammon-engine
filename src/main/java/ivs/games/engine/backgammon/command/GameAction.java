package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.Command;

/**
 * An action submitted on behalf of a side in a backgammon game.
 *
 * <p>The engine checks that the active color matches the current player's color
 * and that the action type is permitted in the current game phase.
 *
 * <p>Implementations must be immutable.
 */
public interface GameAction extends Command {

    /**
     * Returns the color of the side performing this action.
     *
     * @return the non-null active color
     */
    StandardCheckerColor getActiveColor();
}
