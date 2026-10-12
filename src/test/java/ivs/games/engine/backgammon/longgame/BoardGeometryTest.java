package ivs.games.engine.backgammon.longgame;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;

import static ivs.games.engine.backgammon.longgame.BoardGeometry.ADDRESSING;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.BEAR_OFF_POINT;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.BLACK_HEAD;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.BLACK_HOME_END;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.BLACK_HOME_START;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.FIRST_INDEX;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.HALF_SIZE;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.HEAD_POINT;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.HOME_END_POINT;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.HOME_SIZE;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.HOME_START_POINT;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.LAST_INDEX;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.LAST_POINT;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.WHITE_HEAD;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.WHITE_HOME_END;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.WHITE_HOME_START;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests long backgammon geometry constants and side-relative route configuration.
 */
class BoardGeometryTest {

    /**
     * Checks physical board boundaries, head indices and home ranges.
     */
    @Test
    void definesPhysicalGeometry() {
        assertEquals(0, FIRST_INDEX);
        assertEquals(23, LAST_INDEX);
        assertEquals(12, HALF_SIZE);
        assertEquals(6, HOME_SIZE);

        assertEquals(0, WHITE_HEAD);
        assertEquals(12, BLACK_HEAD);
        assertEquals(18, WHITE_HOME_START);
        assertEquals(23, WHITE_HOME_END);
        assertEquals(6, BLACK_HOME_START);
        assertEquals(11, BLACK_HOME_END);
    }

    /**
     * Checks shared route points for the head, home and exact bearing-off destination.
     */
    @Test
    void definesRouteGeometry() {
        assertEquals(1, HEAD_POINT);
        assertEquals(24, LAST_POINT);
        assertEquals(19, HOME_START_POINT);
        assertEquals(24, HOME_END_POINT);
        assertEquals(25, BEAR_OFF_POINT);
    }

    /**
     * Checks the white route point and its inverse conversion.
     *
     * @param point the route point
     */
    @ParameterizedTest(name = "White: route point {0}")
    @MethodSource("routePoints")
    void configuresWhiteRoutePoint(int point) {
        int expectedIndex = point - 1;

        assertEquals(expectedIndex, ADDRESSING.toBoardIndex(StandardCheckerColor.WHITE, point));
        assertEquals(point, ADDRESSING.toRoutePoint(StandardCheckerColor.WHITE, expectedIndex));
    }

    /**
     * Checks the black route point and its inverse conversion.
     *
     * @param point the route point
     */
    @ParameterizedTest(name = "Black: route point {0}")
    @MethodSource("routePoints")
    void configuresBlackRoutePoint(int point) {
        int expectedIndex = (point - 1 + 12) % 24;

        assertEquals(expectedIndex, ADDRESSING.toBoardIndex(StandardCheckerColor.BLACK, point));
        assertEquals(point, ADDRESSING.toRoutePoint(StandardCheckerColor.BLACK, expectedIndex));
    }

    /**
     * Supplies all route points for either side.
     *
     * @return the route points from one through twenty-four
     */
    private static IntStream routePoints() {
        return IntStream.rangeClosed(1, 24);
    }
}
