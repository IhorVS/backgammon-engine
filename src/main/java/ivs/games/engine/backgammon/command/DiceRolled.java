package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Value;

/**
 * Supplies the result of rolling two dice for the active side.
 *
 * <p>The session generates the values. The engine accepts this action only while awaiting dice.</p>
 */
@SuppressWarnings("ClassCanBeRecord")
@Value
public class DiceRolled implements GameAction {

    /**
     * The color of the side whose dice were rolled.
     */
    StandardCheckerColor activeColor;

    /**
     * The first die value, from 1 to 6.
     */
    int first;

    /**
     * The second die value, from 1 to 6.
     */
    int second;
}
