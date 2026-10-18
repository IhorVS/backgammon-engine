package ivs.games.engine.backgammon.event;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.GameEvent;

/**
 * Records the winning side when the game finishes.
 *
 * @param winner the side that won
 */
public record GameFinishedEvent(StandardCheckerColor winner) implements GameEvent {
}
