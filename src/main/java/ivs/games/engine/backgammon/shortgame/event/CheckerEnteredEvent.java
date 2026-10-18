package ivs.games.engine.backgammon.shortgame.event;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.GameEvent;

/**
 * Records an active-side checker entering from the bar.
 *
 * @param color the side entering the checker
 * @param to the destination route point
 */
public record CheckerEnteredEvent(StandardCheckerColor color, int to) implements GameEvent {
}
