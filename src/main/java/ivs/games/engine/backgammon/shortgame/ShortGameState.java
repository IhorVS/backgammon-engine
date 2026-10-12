package ivs.games.engine.backgammon.shortgame;

import ivs.games.engine.backgammon.state.AllowedMove;
import ivs.games.engine.backgammon.state.GameState;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Set;

/**
 * Stores a short backgammon snapshot, including legal entries from the active side's bar.
 *
 * <p>Stores supplied references without validation or copying. Creating code is responsible
 * for supplying immutable collections. Entry options must have a non-null destination.
 */
@Getter
@SuperBuilder(toBuilder = true)
public final class ShortGameState extends GameState {

    /** Legal entries from the active side's bar, or an empty set when none are available. */
    private final Set<AllowedMove> entries;
}
