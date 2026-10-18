package ivs.games.engine.backgammon.event;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.GameEvent;

/**
 * Records an active-side checker being borne off.
 *
 * @param color the side bearing off the checker
 * @param from the source route point
 * @param diceValue the movement value used
 */
public record CheckerBorneOffEvent(StandardCheckerColor color, int from, int diceValue) implements GameEvent {
}
