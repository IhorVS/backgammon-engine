/**
 * Long backgammon engine, state, addressing and internal move resolution.
 *
 * <p>Both sides begin with fifteen ordinary checkers on route point 1.
 * White starts at physical index 0 and black at 12. Both physical routes increase
 * with wraparound. The home is route points 19 through 24.</p>
 *
 * <p>One checker may leave the head per turn. On each side's first turn, doubles
 * of three, four or six permit two departures. The implementation applies this
 * exception without an additional necessity check. First-turn flags also change
 * when a side completes a turn without legal actions.</p>
 *
 * <p>Opposing occupied points are closed; hitting and bar entry are unsupported.
 * A six-point prime requires an opposing checker beyond its end on the opponent's
 * route. An opposing borne-off checker satisfies this requirement. Prime scans
 * do not wrap past the opponent's bearing-off boundary.</p>
 *
 * <p>The resolver enforces maximum dice usage and the higher-value rule when
 * only one action can be performed. Bearing off requires all checkers home;
 * oversize values apply only to the earliest occupied route point.</p>
 */
package ivs.games.engine.backgammon.longgame;
