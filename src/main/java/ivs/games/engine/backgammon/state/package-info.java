/**
 * Game setup, phases and snapshots shared by backgammon variants.
 *
 * <p>GameSetup supplies the starting side. GameState stores a physical board
 * snapshot, active color, phase, dice pool, available action maps and winner.</p>
 *
 * <p>State builders store references without validation or defensive copying.
 * Creating code must provide immutable collections, including nested sets.
 * Subclasses add variant properties and preserve the immutable state contract.</p>
 *
 * <p>AWAITING_DICE and AWAITING_MOVE accept their phase-specific commands and
 * system abort. FINISHED and ABORTED reject every command. Normal completion
 * records a winner; abort preserves all properties except the phase.</p>
 */
package ivs.games.engine.backgammon.state;
