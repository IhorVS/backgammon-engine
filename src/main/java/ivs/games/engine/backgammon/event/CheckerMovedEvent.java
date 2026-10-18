package ivs.games.engine.backgammon.event;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.GameEvent;

/**
 * Records an active-side checker moving between route points.
 *
 * @param color the side moving the checker
 * @param from the source route point
 * @param to the destination route point
 * @param diceValue the movement value used
 */
public record CheckerMovedEvent(
        StandardCheckerColor color,
        int from,
        int to,
        int diceValue) implements GameEvent {
}
