package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Value;

/**
 * Moves the active side's top checker between playing cells.
 *
 * <p>The engine determines the consumed die value and validates movement according to game rules.
 * In variants allowing hits, hitting is performed as part of the same state transition.
 *
 * <p>This action is accepted only while awaiting a movement.
 */
@SuppressWarnings("ClassCanBeRecord")
@Value
public class Move implements GameAction {

    /**
     * The color of the side performing the movement.
     */
    StandardCheckerColor activeColor;

    /**
     * The source playing cell index, from 0 to 23.
     */
    int from;

    /**
     * The destination playing cell index, from 0 to 23.
     */
    int to;
}
