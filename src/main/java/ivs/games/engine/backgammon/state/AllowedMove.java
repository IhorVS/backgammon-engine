package ivs.games.engine.backgammon.state;

import lombok.Value;

/**
 * Describes a legal movement and the die value consumed by that movement.
 *
 * <p>The source cell and active color are supplied by the containing game state.
 * A null destination denotes bearing off.
 *
 * <p>Stores the supplied values without validation.
 */
@SuppressWarnings("ClassCanBeRecord")
@Value
public class AllowedMove {

    /** The destination playing cell index, or null when bearing off. */
    Integer destination;

    /** The die value consumed by this movement. */
    int diceValue;
}
