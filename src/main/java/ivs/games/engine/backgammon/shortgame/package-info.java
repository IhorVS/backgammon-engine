/**
 * Short backgammon engine, state, addressing and internal move resolution.
 *
 * <p>Each side starts with two checkers at route point 1, five at 12, three at 17
 * and five at 19. White's physical route increases from 0 to 23; black's decreases
 * from 23 to 0. The home is route points 19 through 24 for either side.</p>
 *
 * <p>Two or more opposing checkers close a destination. A single opposing checker
 * is hit and sent to its bar. While the active bar is occupied, only entries
 * are permitted. Entry point and consumed die value are equal, from 1 to 6.</p>
 *
 * <p>The resolver retains actions permitting maximum use of remaining dice.
 * If only one action is possible, the higher playable value is required.
 * After the last bar entry, remaining dice may be used for ordinary actions.</p>
 *
 * <p>All remaining active checkers must be home before bearing off. Exact values
 * are allowed at matching points; oversize values require the earliest occupied
 * home point. A hit checker must reenter and return home before bearing off resumes.</p>
 */
package ivs.games.engine.backgammon.shortgame;
