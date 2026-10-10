package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Value;

/**
 * Moves the active side's top checker from its bar to a playing cell.
 *
 * <p>The engine determines the consumed die value and validates entry according to game rules.
 * In variants allowing hits, hitting is performed as part of the same state transition.
 *
 * <p>This action is accepted only while awaiting a movement in a variant supporting bar entry.
 */
@SuppressWarnings("ClassCanBeRecord")
@Value
public class Enter implements GameAction {

    /**
     * The color of the side entering the checker.
     */
    StandardCheckerColor activeColor;

    /**
     * The destination playing cell index, from 0 to 23.
     */
    int to;
}
