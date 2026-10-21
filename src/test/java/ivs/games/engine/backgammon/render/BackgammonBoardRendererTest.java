package ivs.games.engine.backgammon.render;

import ivs.games.engine.backgammon.longgame.LongBackgammonEngine;
import ivs.games.engine.backgammon.longgame.LongGameState;
import ivs.games.engine.backgammon.shortgame.ShortBackgammonEngine;
import ivs.games.engine.backgammon.shortgame.ShortGameState;
import ivs.games.engine.backgammon.state.GameSetup;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.BLACK;
import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.WHITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgammonBoardRendererTest {

    private final BackgammonBoardRenderer renderer = new BackgammonBoardRenderer();

    @Test
    void rendersLongGamePointNumbersFromEitherSide() {
        LongGameState state = new LongBackgammonEngine().startGame(new GameSetup(WHITE));

        assertEquals(List.of(24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13),
                readIndices(renderer.render(state, WHITE)));
        assertEquals(List.of(12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1),
                readIndices(renderer.render(state, BLACK)));
        assertTrue(renderer.render(state, WHITE).contains("WM"));
    }

    @Test
    void rendersShortGamePointNumbersFromEitherSide() {
        ShortGameState state = new ShortBackgammonEngine().startGame(new GameSetup(WHITE));

        assertEquals(List.of(24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13),
                readIndices(renderer.render(state, WHITE)));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
                readIndices(renderer.render(state, BLACK)));
        assertTrue(renderer.render(state, WHITE).contains("BAR"));
        assertTrue(renderer.render(state, WHITE).contains("BORNE OFF"));
    }

    private static List<Integer> readIndices(String board) {
        return Arrays.stream(board.split("\\R")[0].trim().split("\\s+"))
                .map(Integer::parseInt)
                .toList();
    }
}
