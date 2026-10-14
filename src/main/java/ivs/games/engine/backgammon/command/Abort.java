package ivs.games.engine.backgammon.command;

import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;

public class Abort implements GameAction {
    @Override
    public StandardCheckerColor getActiveColor() {
        return null;
    }
}
