package ivs.games.engine.backgammon.longgame;

import com.google.common.collect.ImmutableBiMap;
import ivs.games.accessories.backgammonboard.BoardAddressing;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.experimental.UtilityClass;

/**
 * Defines board geometry and side-relative addressing for long backgammon.
 *
 * <p>Physical board indices run counterclockwise from zero at the bottom-left corner.
 * Route points run from one at each side's head to twenty-four before bearing off.
 *
 * <pre>
 *              WHITE HOME
 *       23  22  21  20  19  18      17  16  15  14  13  12
 *     +-------------------------+-------------------------+
 *     |                         |              BLACK HEAD |
 *     |                         |                         |
 *     | WHITE HEAD              |                         |
 *     +-------------------------+-------------------------+
 *        0   1   2   3   4   5       6   7   8   9  10  11
 *                                          BLACK HOME
 * </pre>
 *
 * <p>Both sides move toward increasing board indices, wrapping from twenty-three to zero.
 * White starts at board index zero; black starts at board index twelve.
 * Each side's home occupies route points nineteen through twenty-four.
 *
 * <pre>{@code
 * ADDRESSING.toBoardIndex(StandardCheckerColor.WHITE, 1)  == 0
 * ADDRESSING.toBoardIndex(StandardCheckerColor.BLACK, 1)  == 12
 * ADDRESSING.toRoutePoint(StandardCheckerColor.BLACK, 0)  == 13
 * ADDRESSING.toBoardIndex(StandardCheckerColor.BLACK, 24) == 11
 * }</pre>
 *
 * <p>A target route point beyond twenty-four is a bearing-off candidate and must not
 * be passed to {@code ADDRESSING.toBoardIndex()}. Bearing-off legality is checked by game rules.
 */
@UtilityClass
class BoardGeometry {

    /** The first physical playing cell index. */
    static final int FIRST_INDEX = 0;

    /** The last physical playing cell index. */
    static final int LAST_INDEX = BoardAddressing.PLAYING_CELL_COUNT - 1;

    /** The number of playing cells in each half of the board. */
    static final int HALF_SIZE = BoardAddressing.PLAYING_CELL_COUNT / 2;

    /** The number of playing cells in each side's home. */
    static final int HOME_SIZE = BoardAddressing.PLAYING_CELL_COUNT / 4;

    /** The white side's physical head index. */
    static final int WHITE_HEAD = FIRST_INDEX;

    /** The black side's physical head index. */
    static final int BLACK_HEAD = HALF_SIZE;

    /** The first physical cell index in the white side's home. */
    static final int WHITE_HOME_START = BoardAddressing.PLAYING_CELL_COUNT - HOME_SIZE;

    /** The last physical cell index in the white side's home. */
    static final int WHITE_HOME_END = LAST_INDEX;

    /** The first physical cell index in the black side's home. */
    static final int BLACK_HOME_START = HALF_SIZE - HOME_SIZE;

    /** The last physical cell index in the black side's home. */
    static final int BLACK_HOME_END = HALF_SIZE - 1;

    /** The head route point for either side. */
    static final int HEAD_POINT = 1;

    /** The last route point before bearing off. */
    static final int LAST_POINT = BoardAddressing.PLAYING_CELL_COUNT;

    /** The first route point in either side's home. */
    static final int HOME_START_POINT = LAST_POINT - HOME_SIZE + 1;

    /** The last route point in either side's home. */
    static final int HOME_END_POINT = LAST_POINT;

    /** The route point corresponding to an exact bearing-off destination. */
    static final int BEAR_OFF_POINT = LAST_POINT + 1;

    /** Immutable conversions between side-relative route points and physical board indices. */
    static final BoardAddressing<StandardCheckerColor> ADDRESSING = new BoardAddressing<>(
            StandardCheckerColor.WHITE, createMapping(WHITE_HEAD),
            StandardCheckerColor.BLACK, createMapping(BLACK_HEAD));

    /**
     * Creates a complete route starting at the specified physical head index.
     *
     * @param headIndex the physical head index
     * @return the immutable mapping from route points to physical board indices
     */
    private ImmutableBiMap<Integer, Integer> createMapping(int headIndex) {
        ImmutableBiMap.Builder<Integer, Integer> builder = ImmutableBiMap.builder();

        for (int point = HEAD_POINT; point <= LAST_POINT; point++) {
            int index = (point - HEAD_POINT + headIndex) % BoardAddressing.PLAYING_CELL_COUNT;
            builder.put(point, index);
        }

        return builder.build();
    }
}
