package ivs.games.engine.backgammon.shortgame;

import com.google.common.collect.ImmutableBiMap;
import ivs.games.accessories.backgammonboard.BoardAddressing;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.experimental.UtilityClass;

/**
 * Defines side-relative addressing for short backgammon.
 * White moves through physical indices 0 to 23.
 * Black moves through physical indices 23 to 0.
 * Route points increase from 1 to 24 for either side.
 */
@UtilityClass
class BoardGeometry {

    /** The last side-relative route point before bearing off. */
    static final int LAST_POINT = BoardAddressing.PLAYING_CELL_COUNT;

    /** The first side-relative route point belonging to the home. */
    static final int HOME_START_POINT = LAST_POINT - BoardAddressing.PLAYING_CELL_COUNT / 4 + 1;

    /** The virtual destination route point for exact bearing off. */
    static final int BEAR_OFF_POINT = LAST_POINT + 1;

    static final BoardAddressing<StandardCheckerColor> ADDRESSING = new BoardAddressing<>(
            StandardCheckerColor.WHITE, createMapping(false),
            StandardCheckerColor.BLACK, createMapping(true));

    /*
     * Creates a complete route with the requested physical direction.
     */
    private ImmutableBiMap<Integer, Integer> createMapping(boolean reversed) {
        ImmutableBiMap.Builder<Integer, Integer> mapping = ImmutableBiMap.builder();

        for (int point = 1; point <= BoardAddressing.PLAYING_CELL_COUNT; point++) {
            int index = reversed ? BoardAddressing.PLAYING_CELL_COUNT - point : point - 1;
            mapping.put(point, index);
        }

        return mapping.build();
    }
}
