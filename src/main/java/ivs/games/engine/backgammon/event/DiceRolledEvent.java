package ivs.games.engine.backgammon.event;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.GameEvent;

/**
 * Records the dice values submitted for a side.
 *
 * @param color the side whose dice were rolled
 * @param first the first die value
 * @param second the second die value
 */
public record DiceRolledEvent(StandardCheckerColor color, int first, int second) implements GameEvent {
}
