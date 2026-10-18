package ivs.games.engine.backgammon;

import ivs.games.accessories.backgammonboard.BoardAddressing;
import ivs.games.accessories.backgammonboard.CheckerColor;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.AbstractGameEngine;
import ivs.games.engine.backgammon.command.Abort;
import ivs.games.engine.backgammon.command.BearOff;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Enter;
import ivs.games.engine.backgammon.command.GameAction;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.event.GameAbortedEvent;
import ivs.games.engine.backgammon.state.GamePhase;
import ivs.games.engine.backgammon.state.GameSetup;
import ivs.games.engine.backgammon.state.GameState;
import ivs.games.engine.exception.EngineCommandException;
import lombok.NonNull;
import org.apache.commons.lang3.Validate;

import java.util.List;

/**
 * Provides common initialization checks, action validation and dispatch for backgammon engines.
 *
 * <p>The engine does not retain state belonging to an individual game.
 * Implementations must be thread-safe and return immutable states without modifying supplied data.</p>
 *
 * <p>Movement actions use side-relative route points from one through twenty-four.
 * Concrete engines convert them to physical board indices according to their addressing schemes.</p>
 *
 * <p>Concrete engines create the initial position and implement state transitions according to their rules.</p>
 *
 * <p>A system abort action is permitted in any non-terminal phase and requires no active color.
 * The default abort handler changes only the game phase to {@code ABORTED}, preserving the current position
 * and other state properties. Finished and aborted games reject all further actions.</p>
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
    private static final String ILLEGAL_DIE_VALUE = "Die value must be between %d and %d: %d";

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
     * Checks that the game permits further actions and validates player actions.
     *
     * <p>Finished and aborted games reject every action, including system abort.</p>
     *
     * <p>A system abort action requires no color or action-specific validation.
     * Other actions require a non-null color matching the active side and are checked
     * for their required phase and basic parameters.</p>
     *
     * @param gameState the current game state
     * @param action    the action to validate
     * @throws EngineCommandException if the game is finished or aborted, a player action color is invalid,
     *                                the action type is unsupported, or action-specific validation fails
     */
    @Override
    protected final void validateCommand(S gameState, GameAction action) {
        validateGameOver(gameState.getGamePhase());
        if (action instanceof Abort) {
            // System abort requires no color or action-specific validation.
            return;
        }

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

    /*
     * Rejects further actions when the game has finished or been aborted.
     * All other phases pass this check.
     */
    private static void validateGameOver(@NonNull GamePhase gamePhase) {
        EngineCommandException.validate(
                GamePhase.FINISHED != gamePhase && GamePhase.ABORTED != gamePhase,
                "Game is already %s; no further actions are permitted".formatted(gamePhase));
    }

    /**
     * Checks the phase and destination route point for an entry action.
     *
     * <p>Variant support and game-specific entry rules are checked during processing.</p>
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
     * Checks the phase, selected die value and source route point for a bearing-off action.
     *
     * <p>Game-specific bearing-off rules are checked during processing.</p>
     *
     * @param gameState the current game state
     * @param bearOff   the bearing-off action to validate
     * @throws EngineCommandException if the phase, die value or source route point is invalid
     */
    protected void validateBearOff(S gameState, BearOff bearOff) {
        requirePhase(gameState, GamePhase.AWAITING_MOVE);

        int diceValue = bearOff.getDiceValue();
        EngineCommandException.validate(
                diceValue >= MIN_DICE_VALUE && diceValue <= MAX_DICE_VALUE,
                ILLEGAL_DIE_VALUE, MIN_DICE_VALUE, MAX_DICE_VALUE, diceValue);

        int bearOffFrom = bearOff.getFrom();
        EngineCommandException.validate(
                bearOffFrom >= 1 && bearOffFrom <= BoardAddressing.PLAYING_CELL_COUNT,
                WRONG_SOURCE_POINT, BoardAddressing.PLAYING_CELL_COUNT, bearOffFrom);
    }

    /**
     * Checks the phase, source route point, selected die value and calculated destination bounds.
     *
     * <p>Game-specific movement rules are checked during processing.</p>
     *
     * @param gameState the current game state
     * @param move      the movement action to validate
     * @throws EngineCommandException if the phase, source, die value or calculated destination is invalid
     */
    protected void validateMove(S gameState, Move move) {
        requirePhase(gameState, GamePhase.AWAITING_MOVE);

        int diceValue = move.getDiceValue();
        EngineCommandException.validate(
                diceValue >= MIN_DICE_VALUE && diceValue <= MAX_DICE_VALUE,
                ILLEGAL_DIE_VALUE, MIN_DICE_VALUE, MAX_DICE_VALUE, diceValue);

        int moveFrom = move.getFrom();
        EngineCommandException.validate(
                moveFrom >= 1 && moveFrom <= BoardAddressing.PLAYING_CELL_COUNT,
                WRONG_SOURCE_POINT, BoardAddressing.PLAYING_CELL_COUNT, moveFrom);

        int moveTo = moveFrom + diceValue;
        EngineCommandException.validate(
                moveTo <= BoardAddressing.PLAYING_CELL_COUNT,
                WRONG_DESTINATION_POINT, BoardAddressing.PLAYING_CELL_COUNT, moveTo);
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
     * Dispatches a validated action to its corresponding handler.
     *
     * <p>Abort actions are handled by {@link #processAbort(GameState, Abort)}.
     * Other actions are dispatched to their game-specific handlers.</p>
     *
     * @param gameState the current game state
     * @param action    the validated action
     * @return the non-null resulting game state
     * @throws EngineCommandException if the action type is unsupported or its handler rejects the action
     */
    @Override
    protected final S doProcessCommand(S gameState, GameAction action) {
        return switch (action) {
            case DiceRolled dice -> processDiceRolled(gameState, dice);
            case Move move -> processMove(gameState, move);
            case BearOff bearOff -> processBearOff(gameState, bearOff);
            case Enter enter -> processEnter(gameState, enter);
            case Abort abort -> processAbort(gameState, abort);
            default -> throw new EngineCommandException(
                    UNSUPPORTED_ACTION_TYPE.formatted(action.getClass().getSimpleName()));
        };
    }

    /**
     * Aborts the game while preserving its state at the time of the action.
     *
     * <p>The default implementation creates a state through the current state's builder
     * and changes only its phase to {@code ABORTED}. The board position, active color,
     * dice and other copied state properties remain unchanged.</p>
     *
     * <p>The supplied state is not modified. The resulting state rejects all further
     * actions through the common command validation.</p>
     *
     * <p>Concrete state types must provide a builder that preserves their runtime type
     * and additional properties, or their engines must override this method.</p>
     *
     * @param gameState the current state in a non-terminal phase
     * @param abort     the validated abort action
     * @return the non-null resulting state with phase {@code ABORTED}
     */
    @SuppressWarnings({"unused", "unchecked"})
    protected S processAbort(S gameState, Abort abort) {
        return (S) gameState.toBuilder()
                .gamePhase(GamePhase.ABORTED)
                .events(List.of(new GameAbortedEvent()))
                .build();
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
     * <p>If no movements are available, completes the turn and switches the active side.</p>
     *
     * @param gameState the state awaiting dice
     * @param action    the validated dice result
     * @return the non-null resulting game state
     */
    protected abstract S processDiceRolled(S gameState, DiceRolled action);

    /**
     * Validates and applies a movement using a side-relative source point and selected die value.
     *
     * <p>Consumes one die value and recalculates available actions before determining
     * whether the active side can continue or play passes to the opposite side.</p>
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
     * or switching the active side.</p>
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
     * to validate entry, consume one die value and determine the resulting state.</p>
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

    /*
     * Rejects an action when the current game phase differs
     * from the phase required by that action.
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
     * <p>Different values provide two movements. A double provides four equal movements.</p>
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
