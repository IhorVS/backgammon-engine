package ivs.games.engine.backgammon.longgame;

import ivs.games.engine.backgammon.state.AllowedMove;
import lombok.NonNull;

import java.util.Map;
import java.util.Set;

/**
 * Determines legal movements for the active side in long backgammon.
 *
 * <p>Uses the board snapshot, original dice values, remaining movement pool and turn restrictions
 * supplied through a movement context. Does not modify the context or retain data belonging
 * to an individual game.
 *
 * <p>Implementations must be thread-safe and return immutable maps and nested sets.
 * Move resolution is not implemented yet.
 */
class LongMoveResolver {

    /**
     * Determines legal single-die movements, including bearing off.
     *
     * <p>The result is grouped by source route point relative to the active side.
     * Each movement specifies its destination route point, or null for bearing off,
     * and the die value consumed. Only movements permitted by the rules for using
     * the remaining movement pool are included.
     *
     * <p>The current placeholder always returns an empty map.
     *
     * @param context the current data required to resolve movements
     * @return the immutable map of legal movements, currently always empty
     * @throws NullPointerException if context is null
     */
    Map<Integer, Set<AllowedMove>> resolve(@NonNull MoveContext context) {
        // TODO Determine the head departure limit from the context when generating movement candidates.
        // TODO Check candidate continuations against dice usage rules.
        return Map.of();
    }
}
