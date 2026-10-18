package ivs.games.engine.backgammon.event;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.GameEvent;

/**
 * Records the side change after a turn ends.
 *
 * @param fromColor the side whose turn ended
 * @param toColor the side whose turn begins
 */
public record TurnChangedEvent(
        StandardCheckerColor fromColor,
        StandardCheckerColor toColor) implements GameEvent {
}
