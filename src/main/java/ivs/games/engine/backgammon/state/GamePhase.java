package ivs.games.engine.backgammon.state;

/**
 * The phase of a backgammon game, determining which action types are permitted.
 */
public enum GamePhase {

    /**
     * Waiting for the session to submit the current player's rolled dice values.
     *
     * <p>A dice-result action or system abort is permitted.</p>
     */
    AWAITING_DICE,

    /**
     * Waiting for the current player to move a checker.
     *
     * <p>Movement actions supported by the game variant or system abort are permitted.</p>
     */
    AWAITING_MOVE,

    /**
     * The game has ended.
     *
     * <p>No further actions are permitted.</p>
     */
    FINISHED,

    /**
     * The game has been aborted without a winner.
     *
     * <p>All other state data is preserved as it was at the moment of abortion.
     * No further actions are permitted.</p>
     */
    ABORTED
}
