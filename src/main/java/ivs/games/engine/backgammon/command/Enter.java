package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Value;

/**
 * Moves the active side's top checker from its bar to a side-relative route point.
 *
 * <p>The engine converts the destination route point to a physical board index using the active color,
 * uses the destination as the consumed die value and validates entry according to game rules.
 * In variants allowing hits, hitting is performed as part of the same state transition.</p>
 *
 * <p>This action is accepted only while awaiting a movement in a variant supporting bar entry.</p>
 */
@SuppressWarnings("ClassCanBeRecord")
@Value
public class Enter implements GameAction {

    /**
     * The color of the side entering the checker.
     */
    StandardCheckerColor activeColor;

    /**
     * The destination route point; short backgammon requires a value from 1 to 6.
     */
    int to;

    /**
     * Returns the die value consumed by this entry.
     *
     * @return the destination route point, equal to the required die value
     */
    public int getDiceValue() {
        return to;
    }
}
