package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Value;

/**
 * Moves the active side's top checker from a side-relative route point using the specified die value.
 *
 * <p>The engine computes the destination route point by adding the die value to the source,
 * converts route points to physical board indices and validates movement according to game rules.
 * In variants allowing hits, hitting is performed as part of the same state transition.</p>
 *
 * <p>This action is accepted only while awaiting a movement. Bearing off requires a separate
 * {@link BearOff} action.</p>
 */
@SuppressWarnings("ClassCanBeRecord")
@Value
public class Move implements GameAction {

    /** The color of the side performing the movement. */
    StandardCheckerColor activeColor;

    /** The source route point for the active side, from 1 to 24. */
    int from;

    /** The selected die value to consume, from 1 to 6. */
    int diceValue;
}
