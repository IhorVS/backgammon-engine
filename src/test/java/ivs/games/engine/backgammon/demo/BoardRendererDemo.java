package ivs.games.engine.backgammon.demo;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.longgame.LongBackgammonEngine;
import ivs.games.engine.backgammon.render.BackgammonBoardRenderer;
import ivs.games.engine.backgammon.shortgame.ShortBackgammonEngine;
import ivs.games.engine.backgammon.state.GameSetup;

/**
 * Test-scope console demo for both standard backgammon variants.
 */
public final class BoardRendererDemo {

    private BoardRendererDemo() {
    }

    public static void main(String[] args) {
        var renderer = new BackgammonBoardRenderer();
        var longState = new LongBackgammonEngine().startGame(new GameSetup(StandardCheckerColor.WHITE));
        var shortState = new ShortBackgammonEngine().startGame(new GameSetup(StandardCheckerColor.WHITE));

        System.out.println("Long backgammon - white perspective");
        System.out.println(renderer.render(longState, StandardCheckerColor.WHITE));
        System.out.println();
        System.out.println("Long backgammon - black perspective");
        System.out.println(renderer.render(longState, StandardCheckerColor.BLACK));
        System.out.println();
        System.out.println("Short backgammon - white perspective");
        System.out.println(renderer.render(shortState, StandardCheckerColor.WHITE));
        System.out.println();
        System.out.println("Short backgammon - black perspective");
        System.out.println(renderer.render(shortState, StandardCheckerColor.BLACK));
    }
}
