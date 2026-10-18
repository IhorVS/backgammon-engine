/**
 * Immutable player command values and the stateless system abort command.
 *
 * <p>DiceRolled supplies externally generated dice. Move identifies a source route
 * point and die value; its destination is their sum. BearOff identifies a source
 * and selected die value. Enter identifies a destination, which is also its die value.</p>
 *
 * <p>Player commands are validated against phase, active color and variant rules.
 * Abort is a colorless system command accepted only before a terminal phase.</p>
 */
package ivs.games.engine.backgammon.command;
