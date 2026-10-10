package ivs.games.engine.backgammon;

import ivs.games.accessories.backgammonboard.CheckerColor;
import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.AbstractGameEngine;
import ivs.games.engine.CommandResult;
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
 * <p>Concrete engines create the initial position and implement state transitions according to their rules.
 *
 * @param <S> the concrete game state type
 */
public abstract class AbstractBackgammonEngine<S extends GameState>
        extends AbstractGameEngine<S, GameAction, GameSetup> {

    protected static final int MIN_DICE_VALUE = 1;
    protected static final int MAX_DICE_VALUE = 6;

    private static final String WRONG_SOURCE_INDEX = "Source cell index must be between 0 and 23: %d";
    private static final String WRONG_DESTINATION_INDEX = "Destination cell index must be between 0 and 23: %d";
    private static final String UNSUPPORTED_ACTION_TYPE = "Unsupported action type: %s";

    /**
     * Validates the setup and creates the initial game state.
     *
     * @param initialSetup the non-null initial setup
     * @return the result containing the initial state
     */
    @Override
    protected final CommandResult<S> doStartGame(GameSetup initialSetup) {
        StandardCheckerColor startingColor = initialSetup.getStartingColor();
        Validate.notNull(startingColor, "Starting color must not be null");
        return createInitialState(initialSetup);
    }

    /**
     * Checks the active color, game phase and basic action parameters.
     *
     * @param state  the current game state
     * @param action the action to validate
     * @throws EngineCommandException if the action is not permitted
     */
    @Override
    protected final void validateCommand(S state, GameAction action) {
        CheckerColor actionActiveColor = action.getActiveColor();
        EngineCommandException.validate(actionActiveColor != null, "Active color must not be null");

        StandardCheckerColor activeColor = state.getActiveColor();
        EngineCommandException.validate(actionActiveColor == activeColor,
                "Action color must match the active player's color: %s", activeColor);

        switch (action) {
            case DiceRolled diceRolled -> validateDiceRolled(state, diceRolled);
            case Move move -> validateMove(state, move);
            case BearOff bearOff -> validateBearOff(state, bearOff);
            case Enter enter -> validateEnter(state, enter);
            default -> throw new EngineCommandException(
                    UNSUPPORTED_ACTION_TYPE.formatted(action.getClass().getSimpleName()));
        }
    }

    /**
     * Checks the phase and destination cell index for an entry action.
     *
     * <p>Variant support and game-specific entry rules are checked during processing.
     *
     * @param state the current game state
     * @param enter the entry action to validate
     * @throws EngineCommandException if the phase or destination cell index is invalid
     */
    protected void validateEnter(S state, Enter enter) {
        requirePhase(state, GamePhase.AWAITING_MOVE);

        int enterTo = enter.getTo();
        EngineCommandException.validate(
                enterTo >= 0 && enterTo < StandardBoard.PLAYING_CELL_COUNT,
                WRONG_DESTINATION_INDEX, enterTo);
    }

    /**
     * Checks the phase and source cell index for a bearing-off action.
     *
     * <p>Game-specific bearing-off rules are checked during processing.
     *
     * @param state   the current game state
     * @param bearOff the bearing-off action to validate
     * @throws EngineCommandException if the phase or source cell index is invalid
     */
    protected void validateBearOff(S state, BearOff bearOff) {
        requirePhase(state, GamePhase.AWAITING_MOVE);

        int bearOffFrom = bearOff.getFrom();
        EngineCommandException.validate(
                bearOffFrom >= 0 && bearOffFrom < StandardBoard.PLAYING_CELL_COUNT,
                WRONG_SOURCE_INDEX, bearOffFrom);
    }

    /**
     * Checks the phase, cell indices and distinctness of the source and destination cells.
     *
     * <p>Game-specific movement rules are checked during processing.
     *
     * @param state the current game state
     * @param move  the movement action to validate
     * @throws EngineCommandException if the phase is invalid, an index is outside the board or the indices are equal
     */
    protected void validateMove(S state, Move move) {
        requirePhase(state, GamePhase.AWAITING_MOVE);

        int moveFrom = move.getFrom();
        int moveTo = move.getTo();

        EngineCommandException.validate(
                moveFrom >= 0 && moveFrom < StandardBoard.PLAYING_CELL_COUNT,
                WRONG_SOURCE_INDEX, moveFrom);

        EngineCommandException.validate(
                moveTo >= 0 && moveTo < StandardBoard.PLAYING_CELL_COUNT,
                WRONG_DESTINATION_INDEX, moveTo);

        EngineCommandException.validate(
                moveFrom != moveTo,
                "Source and destination cells must differ: %d", moveFrom);
    }

    /**
     * Checks the phase and both die values for a dice-result action.
     *
     * @param state the current game state
     * @param dice  the dice-result action to validate
     * @throws EngineCommandException if the phase is invalid or either die value is outside the supported range
     */
    protected void validateDiceRolled(S state, DiceRolled dice) {
        requirePhase(state, GamePhase.AWAITING_DICE);

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
     * @param state  the current game state
     * @param action the validated action
     * @return the processing result containing the new state
     */
    @Override
    protected final CommandResult<S> doProcessCommand(S state, GameAction action) {
        return switch (action) {
            case DiceRolled dice -> processDiceRolled(state, dice);
            case Move move -> processMove(state, move);
            case BearOff bearOff -> processBearOff(state, bearOff);
            case Enter enter -> processEnter(state, enter);
            default -> throw new EngineCommandException(
                    UNSUPPORTED_ACTION_TYPE.formatted(action.getClass().getSimpleName()));
        };
    }

    /**
     * Creates the initial board arrangement and a state awaiting dice.
     *
     * @param initialSetup the validated initial setup
     * @return the non-null result containing the initial state
     */
    protected abstract CommandResult<S> createInitialState(GameSetup initialSetup);

    /**
     * Stores the roll, expands doubles and computes available movements.
     *
     * <p>If no movements are available, completes the turn and switches the active side.
     *
     * @param state  the state awaiting dice
     * @param action the validated dice result
     * @return the non-null result containing the new state
     */
    protected abstract CommandResult<S> processDiceRolled(S state, DiceRolled action);

    /**
     * Validates and applies a movement between playing cells.
     *
     * <p>Consumes one die value and checks for game completion before computing further movements
     * or switching the active side.
     *
     * @param state  the state awaiting a movement
     * @param action the movement to process
     * @return the non-null result containing the new state
     * @throws EngineCommandException if the movement violates game rules
     */
    protected abstract CommandResult<S> processMove(S state, Move action);

    /**
     * Validates and applies bearing off from a playing cell.
     *
     * <p>Consumes one die value and checks for game completion before computing further movements
     * or switching the active side.
     *
     * @param state  the state awaiting a movement
     * @param action the bearing-off action to process
     * @return the non-null result containing the new state
     * @throws EngineCommandException if bearing off violates game rules
     */
    protected abstract CommandResult<S> processBearOff(S state, BearOff action);

    /**
     * Processes entry from the active side's bar.
     *
     * <p>The default implementation rejects entry. Variants supporting a bar must override this method
     * to validate entry, consume one die value and determine the resulting state.
     *
     * @param state  the state awaiting a movement
     * @param action the entry action to process
     * @return the non-null result containing the new state
     * @throws EngineCommandException if entry is unsupported or violates game rules
     */
    @SuppressWarnings("unused")
    protected CommandResult<S> processEnter(S state, Enter action) {
        throw new EngineCommandException("Bar entry is not supported by this game variant");
    }

    /**
     * Requires the state to be in the specified phase.
     *
     * @param state         the current game state
     * @param expectedPhase the phase required by the action
     * @throws EngineCommandException if the current phase differs
     */
    private void requirePhase(S state, GamePhase expectedPhase) {
        GamePhase gamePhase = state.getGamePhase();
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
