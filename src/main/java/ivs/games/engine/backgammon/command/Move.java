package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Value;

/**
 * Moves the active side's top checker between side-relative route points.
 *
 * <p>The engine converts route points to physical board indices using the active color,
 * determines the consumed die value and validates movement according to game rules.
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
     * The source route point for the active side, from 1 to 24.
     */
    int from;

    /**
     * The destination route point for the active side, from 1 to 24.
     */
    int to;
}
