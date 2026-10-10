package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Value;

/**
 * Moves the active side's top checker from a playing cell to its borne-off cell.
 *
 * <p>The engine determines the consumed die value and validates bearing off according to game rules.
 * This action is accepted only while awaiting a movement.
 */
@SuppressWarnings("ClassCanBeRecord")
@Value
public class BearOff implements GameAction {

    /**
     * The color of the side bearing off the checker.
     */
    StandardCheckerColor activeColor;

    /**
     * The source playing cell index, from 0 to 23.
     */
    int from;
}
