package ivs.games.engine.backgammon;

import ivs.games.accessories.backgammonboard.CheckerColor;
import ivs.games.accessories.backgammonboard.BoardAddressing;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.AbstractGameEngine;
import ivs.games.engine.backgammon.command.BearOff;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Enter;
import ivs.games.engine.backgammon.command.GameAction;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.state.GamePhase;
import ivs.games.engine.backgammon.state.GameSetup;
import ivs.games.engine.backgammon.state.GameState;
import ivs.games.engine.exception.EngineCommandException;
import org.apache.commons.lang3.Validate;

import java.util.List;

/**
 * Provides common initialization checks, action validation and dispatch for backgammon engines.
 *
 * <p>The engine does not retain state belonging to an individual game.
 * Implementations must be thread-safe and return immutable states without modifying supplied data.
 *
 * <p>Movement actions use side-relative route points from one through twenty-four.
 * Concrete engines convert them to physical board indices according to their addressing schemes.
 *
 * <p>Concrete engines create the initial position and implement state transitions according to their rules.
 *
 * @param <S> the concrete game state type
 */
public abstract class AbstractBackgammonEngine<S extends GameState>
        extends AbstractGameEngine<S, GameAction, GameSetup> {

    protected static final int MIN_DICE_VALUE = 1;
    protected static final int MAX_DICE_VALUE = 6;

    private static final String WRONG_SOURCE_POINT = "Source route point must be between 1 and %d: %d";
    private static final String WRONG_DESTINATION_POINT = "Destination route point must be between 1 and %d: %d";
    private static final String UNSUPPORTED_ACTION_TYPE = "Unsupported action type: %s";

    /**
     * Validates the setup and creates the initial game state.
     *
     * @param initialSetup the non-null initial setup
     * @return the non-null initial game state
     */
    @Override
    protected final S doStartGame(GameSetup initialSetup) {
        StandardCheckerColor startingColor = initialSetup.getStartingColor();
        Validate.notNull(startingColor, "Starting color must not be null");
        return createInitialState(initialSetup);
    }

    /**
     * Checks the active color, game phase and basic action parameters.
     *
     * @param gameState the current game state
     * @param action    the action to validate
     * @throws EngineCommandException if the action is not permitted
     */
    @Override
    protected final void validateCommand(S gameState, GameAction action) {
        CheckerColor actionActiveColor = action.getActiveColor();
        EngineCommandException.validate(actionActiveColor != null, "Active color must not be null");

        StandardCheckerColor activeColor = gameState.getActiveColor();
        EngineCommandException.validate(actionActiveColor == activeColor,
                "Action color must match the active player's color: %s", activeColor);

        switch (action) {
            case DiceRolled diceRolled -> validateDiceRolled(gameState, diceRolled);
            case Move move -> validateMove(gameState, move);
            case BearOff bearOff -> validateBearOff(gameState, bearOff);
            case Enter enter -> validateEnter(gameState, enter);
            default -> throw new EngineCommandException(
                    UNSUPPORTED_ACTION_TYPE.formatted(action.getClass().getSimpleName()));
        }
    }

    /**
     * Checks the phase and destination route point for an entry action.
     *
     * <p>Variant support and game-specific entry rules are checked during processing.
     *
     * @param gameState the current game state
     * @param enter     the entry action to validate
     * @throws EngineCommandException if the phase or destination route point is invalid
     */
    protected void validateEnter(S gameState, Enter enter) {
        requirePhase(gameState, GamePhase.AWAITING_MOVE);

        int enterTo = enter.getTo();
        EngineCommandException.validate(
                enterTo >= 1 && enterTo <= BoardAddressing.PLAYING_CELL_COUNT,
                WRONG_DESTINATION_POINT, BoardAddressing.PLAYING_CELL_COUNT, enterTo);
    }

    /**
     * Checks the phase and source route point for a bearing-off action.
     *
     * <p>Game-specific bearing-off rules are checked during processing.
     *
     * @param gameState the current game state
     * @param bearOff   the bearing-off action to validate
     * @throws EngineCommandException if the phase or source route point is invalid
     */
    protected void validateBearOff(S gameState, BearOff bearOff) {
        requirePhase(gameState, GamePhase.AWAITING_MOVE);

        int bearOffFrom = bearOff.getFrom();
        EngineCommandException.validate(
                bearOffFrom >= 1 && bearOffFrom <= BoardAddressing.PLAYING_CELL_COUNT,
                WRONG_SOURCE_POINT, BoardAddressing.PLAYING_CELL_COUNT, bearOffFrom);
    }

    /**
     * Checks the phase, route point bounds and distinctness of the source and destination points.
     *
     * <p>Game-specific movement rules are checked during processing.
     *
     * @param gameState the current game state
     * @param move      the movement action to validate
     * @throws EngineCommandException if the phase is invalid, a route point is out of range or the points are equal
     */
    protected void validateMove(S gameState, Move move) {
        requirePhase(gameState, GamePhase.AWAITING_MOVE);

        int moveFrom = move.getFrom();
        int moveTo = move.getTo();

        EngineCommandException.validate(
                moveFrom >= 1 && moveFrom <= BoardAddressing.PLAYING_CELL_COUNT,
                WRONG_SOURCE_POINT, BoardAddressing.PLAYING_CELL_COUNT, moveFrom);

        EngineCommandException.validate(
                moveTo >= 1 && moveTo <= BoardAddressing.PLAYING_CELL_COUNT,
                WRONG_DESTINATION_POINT, BoardAddressing.PLAYING_CELL_COUNT, moveTo);

        EngineCommandException.validate(
                moveFrom != moveTo,
                "Source and destination route points must differ: %d", moveFrom);
    }

    /**
     * Checks the phase and both die values for a dice-result action.
     *
     * @param gameState the current game state
     * @param dice      the dice-result action to validate
     * @throws EngineCommandException if the phase is invalid or either die value is outside the supported range
     */
    protected void validateDiceRolled(S gameState, DiceRolled dice) {
        requirePhase(gameState, GamePhase.AWAITING_DICE);

        int diceFirst = dice.getFirst();
        EngineCommandException.validate(
                diceFirst >= MIN_DICE_VALUE && diceFirst <= MAX_DICE_VALUE,
                "First die value must be between 1 and 6: %d", diceFirst);

        int diceSecond = dice.getSecond();
        EngineCommandException.validate(
                diceSecond >= MIN_DICE_VALUE && diceSecond <= MAX_DICE_VALUE,
                "Second die value must be between 1 and 6: %d", diceSecond);
    }

    /**
     * Dispatches a validated action to its game-specific handler.
     *
     * @param gameState the current game state
     * @param action    the validated action
     * @return the non-null resulting game state
     */
    @Override
    protected final S doProcessCommand(S gameState, GameAction action) {
        return switch (action) {
            case DiceRolled dice -> processDiceRolled(gameState, dice);
            case Move move -> processMove(gameState, move);
            case BearOff bearOff -> processBearOff(gameState, bearOff);
            case Enter enter -> processEnter(gameState, enter);
            default -> throw new EngineCommandException(
                    UNSUPPORTED_ACTION_TYPE.formatted(action.getClass().getSimpleName()));
        };
    }

    /**
     * Creates the initial board arrangement and a state awaiting dice.
     *
     * @param initialSetup the validated initial setup
     * @return the non-null initial game state
     */
    protected abstract S createInitialState(GameSetup initialSetup);

    /**
     * Stores the roll, expands doubles and computes available movements.
     *
     * <p>If no movements are available, completes the turn and switches the active side.
     *
     * @param gameState the state awaiting dice
     * @param action    the validated dice result
     * @return the non-null resulting game state
     */
    protected abstract S processDiceRolled(S gameState, DiceRolled action);

    /**
     * Validates and applies a movement specified by side-relative route points.
     *
     * <p>Consumes one die value and checks for game completion before computing further movements
     * or switching the active side.
     *
     * @param gameState the state awaiting a movement
     * @param action    the movement to process
     * @return the non-null resulting game state
     * @throws EngineCommandException if the movement violates game rules
     */
    protected abstract S processMove(S gameState, Move action);

    /**
     * Validates and applies bearing off from a side-relative route point.
     *
     * <p>Consumes one die value and checks for game completion before computing further movements
     * or switching the active side.
     *
     * @param gameState the state awaiting a movement
     * @param action    the bearing-off action to process
     * @return the non-null resulting game state
     * @throws EngineCommandException if bearing off violates game rules
     */
    protected abstract S processBearOff(S gameState, BearOff action);

    /**
     * Processes entry from the active side's bar to a side-relative route point.
     *
     * <p>The default implementation rejects entry. Variants supporting a bar must override this method
     * to validate entry, consume one die value and determine the resulting state.
     *
     * @param gameState the state awaiting a movement
     * @param action    the entry action to process
     * @return the non-null resulting game state
     * @throws EngineCommandException if entry is unsupported or violates game rules
     */
    @SuppressWarnings("unused")
    protected S processEnter(S gameState, Enter action) {
        throw new EngineCommandException("Bar entry is not supported by this game variant");
    }

    /**
     * Requires the state to be in the specified phase.
     *
     * @param gameState     the current game state
     * @param expectedPhase the phase required by the action
     * @throws EngineCommandException if the current phase differs
     */
    private void requirePhase(S gameState, GamePhase expectedPhase) {
        GamePhase gamePhase = gameState.getGamePhase();
        EngineCommandException.validate(
                gamePhase == expectedPhase,
                "Action requires phase %s, but current phase is %s", expectedPhase, gamePhase);
    }

    /**
     * Creates the movement value pool from a validated dice result.
     *
     * <p>Different values provide two movements. A double provides four equal movements.
     *
     * @param action the validated dice result
     * @return the immutable movement value pool
     */
    @SuppressWarnings("unused")
    protected final List<Integer> createMovementPool(DiceRolled action) {
        int first = action.getFirst();
        int second = action.getSecond();

        return first == second ? List.of(first, first, first, first) : List.of(first, second);
    }
}

