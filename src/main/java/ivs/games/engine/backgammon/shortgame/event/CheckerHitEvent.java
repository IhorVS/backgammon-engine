package ivs.games.engine.backgammon.shortgame.event;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.GameEvent;

/**
 * Records an opposing checker being hit onto its bar.
 *
 * @param hitterColor the side that hit the checker
 * @param hitColor the side whose checker was hit
 * @param routePoint the destination route point from the hitter's perspective
 */
public record CheckerHitEvent(
        StandardCheckerColor hitterColor,
        StandardCheckerColor hitColor,
        int routePoint) implements GameEvent {
}
