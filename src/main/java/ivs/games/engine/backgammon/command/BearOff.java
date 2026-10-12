package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Value;

/**
 * Bears off the active side's top checker from a side-relative route point using the specified die value.
 *
 * <p>The engine converts the source route point to a physical board index using the active color
 * and validates bearing off with the selected die value according to game rules.
 *
 * <p>This action is accepted only while awaiting a movement.
 */
@SuppressWarnings("ClassCanBeRecord")
@Value
public class BearOff implements GameAction {

    /**
     * The color of the side bearing off the checker.
     */
    StandardCheckerColor activeColor;

    /**
     * The source route point for the active side, from 1 to 24.
     */
    int from;

    /**
     * The selected die value to consume, from 1 to 6.
     */
    int diceValue;
}
