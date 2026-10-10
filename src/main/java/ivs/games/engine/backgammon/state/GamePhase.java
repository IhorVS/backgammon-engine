package ivs.games.engine.backgammon.state;

/**
 * The phase of a backgammon game, determining which action types are permitted.
 */
public enum GamePhase {

    /**
     * Waiting for the session to submit the current player's rolled dice values.
     *
     * <p>Only an action supplying the dice result is permitted.
     */
    AWAITING_DICE,

    /**
     * Waiting for the current player to move a checker.
     *
     * <p>Only movement actions supported by the game variant are permitted.
     */
    AWAITING_MOVE,

    /**
     * The game has ended.
     *
     * <p>No further actions are permitted.
     */
    FINISHED
}
