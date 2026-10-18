package ivs.games.engine.backgammon.event;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.GameEvent;

/**
 * Records the creation of a game and its starting side.
 *
 * @param startingColor the side that begins the game
 */
public record GameStartedEvent(StandardCheckerColor startingColor) implements GameEvent {
}
