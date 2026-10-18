package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;

/**
 * Requests system cancellation of a game without declaring a winner.
 *
 * <p>No player color is required. The command is accepted in either non-terminal
 * phase. Finished and aborted games reject it along with all other actions.</p>
 *
 * <p>The default handler changes only the phase to {@code ABORTED}.
 * Board contents, dice, available actions and variant-specific data are preserved.</p>
 */
public class Abort implements GameAction {

    /**
     * Returns no player color because this is a system action.
     *
     * @return {@code null}
     */
    @Override
    public StandardCheckerColor getActiveColor() {
        return null;
    }
}
