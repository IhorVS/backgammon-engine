package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.Value;

/**
 * Moves the active side's top checker from its bar to a side-relative route point.
 *
 * <p>The engine converts the destination route point to a physical board index using the active color,
 * determines the consumed die value and validates entry according to game rules.
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
     * The destination route point for the active side, from 1 to 24.
     */
    int to;
}
