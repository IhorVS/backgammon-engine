/**
 * Shared backgammon engine initialization, validation and command dispatch.
 *
 * <p>Engines process one command per call and return a new immutable snapshot.
 * Sessions own state history, command input, dice generation and publication.
 * No session loop or random dice generation is performed by an engine.</p>
 *
 * <p>Commands use side-relative route points from 1 to 24. States store board
 * snapshots using physical indices from 0 to 23. Variant addressing maps connect
 * these coordinate systems; both routes increase towards bearing off.</p>
 *
 * <p>Player actions require the active side's color. System abort requires no
 * color and changes only the phase. Finished and aborted games reject all actions.</p>
 *
 * <p>Concrete engines validate variant rules and return immutable action maps.
 * Invalid commands throw {@link ivs.games.engine.exception.EngineCommandException}.</p>
 */
package ivs.games.engine.backgammon;
