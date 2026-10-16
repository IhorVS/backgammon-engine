package ivs.games.engine.backgammon.shortgame;

import ivs.games.engine.backgammon.state.GameState;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.Set;

/**
 * Stores an immutable short backgammon snapshot.
 *
 * <p>Supplied references are stored without validation or copying.
 * Creating code must ensure data consistency and collection immutability.</p>
 */
@Getter
@SuperBuilder(toBuilder = true)
public final class ShortGameState extends GameState {

    /**
     * Legal die values for bar entry, grouped by destination route point.
     */
    private final Map<Integer, Set<Integer>> allowedEntries;
}
