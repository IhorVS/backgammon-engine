package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.Command;

/**
 * A player or system action submitted to a backgammon engine.
 *
 * <p>Player actions require a color matching the active side. System {@link Abort}
 * actions require no color. The engine checks phase restrictions before processing.</p>
 *
 * <p>Implementations must be immutable.</p>
 */
public interface GameAction extends Command {

    /**
     * Returns the color of the side performing this action.
     *
     * @return the active color for player actions, or {@code null} for system abort
     */
    StandardCheckerColor getActiveColor();
}
