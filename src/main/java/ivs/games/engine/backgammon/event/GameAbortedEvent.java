package ivs.games.engine.backgammon.event;

import ivs.games.engine.GameEvent;

/**
 * Records that the game was aborted.
 */
public record GameAbortedEvent() implements GameEvent {
}
