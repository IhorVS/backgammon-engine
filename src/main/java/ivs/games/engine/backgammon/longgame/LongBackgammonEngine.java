package ivs.games.engine.backgammon.longgame;

import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardBoardBuilder;
import ivs.games.accessories.backgammonboard.standard.StandardChecker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.AbstractBackgammonEngine;
import ivs.games.engine.backgammon.command.BearOff;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.state.AllowedMove;
import ivs.games.engine.backgammon.state.GamePhase;
import ivs.games.engine.backgammon.state.GameSetup;
import ivs.games.engine.exception.EngineCommandException;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ivs.games.engine.backgammon.longgame.BoardGeometry.ADDRESSING;
import static ivs.games.engine.backgammon.longgame.BoardGeometry.HEAD_POINT;

/**
 * Initializes long backgammon games and processes actions according to their rules.
 *
 * <p>The engine does not retain state belonging to an individual game.
 * Legal movements are determined by a shared move resolver.
 *
 * <p>Movement and bearing-off processing are not implemented yet.
 */
public class LongBackgammonEngine extends AbstractBackgammonEngine<LongGameState> {

    private static final String MOVEMENT_IS_UNAVAILABLE = "Movement value is unavailable: %d";

    /** The number of checkers belonging to each side. */
    private static final int CHECKERS_PER_SIDE = 15;

    /** Determines legal movements according to long backgammon rules. */
    private final LongMoveResolver moveResolver;

    /**
     * Creates an engine with the standard move resolver.
     */
    public LongBackgammonEngine() {
        this(new LongMoveResolver());
    }

    /**
     * Creates an engine with the supplied move resolver.
     *
     * @param moveResolver the non-null move resolver
     * @throws NullPointerException if moveResolver is null
     */
    LongBackgammonEngine(@NonNull LongMoveResolver moveResolver) {
        this.moveResolver = moveResolver;
    }

    /**
     * Creates the initial long backgammon position.
     *
     * <p>Each side starts with fifteen checkers on its head. The starting side awaits a dice roll.
     * Both sides retain their first-turn status, and no movement values have been assigned.
     *
     * @param initialSetup the validated initial setup
     * @return the non-null initial game state
     */
    @Override
    protected LongGameState createInitialState(GameSetup initialSetup) {
        var board = new StandardBoardBuilder<>()
                .cell(ADDRESSING.toBoardIndex(StandardCheckerColor.WHITE, HEAD_POINT),
                        Collections.nCopies(CHECKERS_PER_SIDE, StandardChecker.WHITE_MAN))
                .cell(ADDRESSING.toBoardIndex(StandardCheckerColor.BLACK, HEAD_POINT),
                        Collections.nCopies(CHECKERS_PER_SIDE, StandardChecker.BLACK_MAN))
                .build();

        Map<StandardCheckerColor, Boolean> firstTurns = Map.of(
                StandardCheckerColor.WHITE, true,
                StandardCheckerColor.BLACK, true
        );

        return LongGameState.builder()
                .board(board.getState())
                .activeColor(initialSetup.getStartingColor())
                .gamePhase(GamePhase.AWAITING_DICE)
                .diceValues(List.of())
                .remainingDiceValues(List.of())
                .winner(null)
                .headMoves(0)
                .firstTurns(firstTurns)
                .allowedMoves(Map.of())
                .build();
    }

    /**
     * Stores the dice roll, creates the movement pool and switches to the movement phase.
     *
     * @param gameState the state awaiting dice
     * @param action    the validated dice result
     * @return the non-null resulting game state
     */
    @Override
    protected LongGameState processDiceRolled(LongGameState gameState, DiceRolled action) {
        List<Integer> diceValues = List.of(action.getFirst(), action.getSecond());
        List<Integer> movementPool = createMovementPool(action);

        MoveContext moveContext = MoveContext.from(gameState)
                .setDiceValues(diceValues)
                .setMovementPool(movementPool);
        Map<Integer, Set<AllowedMove>> allowedMoves = moveResolver.resolve(moveContext);

        return gameState.toBuilder()
                .diceValues(diceValues)
                .remainingDiceValues(movementPool)
                .gamePhase(GamePhase.AWAITING_MOVE)
                .allowedMoves(allowedMoves)
                .build();
    }

    /**
     * Moves a checker between side-relative route points and recalculates available movements.
     *
     * <p>Consumes one matching movement value and updates the number of head departures.
     * If the movement pool or available movements are empty, passes play to the opposite side.
     * The supplied state remains unchanged.
     *
     * @param gameState the state awaiting a movement
     * @param action    the validated movement action
     * @return the non-null resulting game state
     * @throws EngineCommandException if the required movement value is unavailable
     */
    @Override
    protected LongGameState processMove(LongGameState gameState, Move action) {
        int from = action.getFrom();
        int to = action.getTo();
        int diceValue = to - from;

        // TODO Validate movement legality, source ownership and destination availability.

        // Consume one matching movement value.
        List<Integer> movementPool = new ArrayList<>(gameState.getRemainingDiceValues());
        EngineCommandException.validate(
                movementPool.remove(Integer.valueOf(diceValue)),
                MOVEMENT_IS_UNAVAILABLE, diceValue);

        // Move the checker on an independent working board.
        StandardCheckerColor activeColor = gameState.getActiveColor();
        var board = new StandardBoard<>(gameState.getBoard());
        board.move(ADDRESSING.toBoardIndex(activeColor, from), ADDRESSING.toBoardIndex(activeColor, to));

        // Prepare the updated movement context.
        int headMoves = gameState.getHeadMoves() + (from == HEAD_POINT ? 1 : 0);
        List<Integer> remainingDiceValues = List.copyOf(movementPool);
        MoveContext context = MoveContext.from(gameState)
                .setBoard(board.getState())
                .setMovementPool(remainingDiceValues)
                .setHeadMoves(headMoves);

        // Resolve movements for the updated position.
        Map<Integer, Set<AllowedMove>> allowedMoves = moveResolver.resolve(context);

        // Prepare the resulting game snapshot.
        var builder = gameState.toBuilder()
                .board(context.getBoard())
                .remainingDiceValues(remainingDiceValues)
                .headMoves(headMoves)
                .allowedMoves(allowedMoves);

        // Pass play to the opposite side when the current turn cannot continue.
        if (remainingDiceValues.isEmpty() || allowedMoves.isEmpty()) {
            Map<StandardCheckerColor, Boolean> firstTurns = new EnumMap<>(StandardCheckerColor.class);
            firstTurns.putAll(gameState.getFirstTurns());
            firstTurns.put(activeColor, false);

            builder.activeColor(StandardCheckerColor.getOpposite(activeColor))
                    .gamePhase(GamePhase.AWAITING_DICE)
                    .diceValues(List.of())
                    .remainingDiceValues(List.of())
                    .headMoves(0)
                    .firstTurns(Map.copyOf(firstTurns))
                    .allowedMoves(Map.of());
        }
        return builder.build();
    }

    /**
     * Bears off a checker from a side-relative route point using the selected die value.
     *
     * <p>Checks for victory before resolving further movements or passing play to the opposite side.
     * The supplied state remains unchanged.
     *
     * @param gameState the state awaiting a movement
     * @param action the validated bearing-off action
     * @return the non-null resulting game state
     * @throws EngineCommandException if the selected movement value is unavailable
     */
    @Override
    protected LongGameState processBearOff(LongGameState gameState, BearOff action) {
        int from = action.getFrom();
        int diceValue = action.getDiceValue();

        // TODO Validate bearing-off legality and source ownership.

        // Consume one occurrence of the selected movement value.
        List<Integer> movementPool = new ArrayList<>(gameState.getRemainingDiceValues());
        EngineCommandException.validate(
                movementPool.remove(Integer.valueOf(diceValue)),
                MOVEMENT_IS_UNAVAILABLE, diceValue);

        // Bear off the checker on an independent working board.
        StandardCheckerColor activeColor = gameState.getActiveColor();
        var board = new StandardBoard<>(gameState.getBoard());
        board.moveToBorneOff(ADDRESSING.toBoardIndex(activeColor, from));

        var boardState = board.getState();
        List<Integer> remainingDiceValues = List.copyOf(movementPool);
        var builder = gameState.toBuilder()
                .board(boardState)
                .remainingDiceValues(remainingDiceValues);

        // Finish the game immediately after the last checker is borne off.
        if (boardState.getBorneOff().get(activeColor).size() == CHECKERS_PER_SIDE) {
            return builder.gamePhase(GamePhase.FINISHED)
                    .winner(activeColor)
                    .allowedMoves(Map.of())
                    .build();
        }

        // Resolve movements for the updated position.
        MoveContext context = MoveContext.from(gameState)
                .setBoard(boardState)
                .setMovementPool(remainingDiceValues);
        Map<Integer, Set<AllowedMove>> allowedMoves = moveResolver.resolve(context);
        builder.allowedMoves(allowedMoves);

        // Pass play to the opposite side when the current turn cannot continue.
        if (remainingDiceValues.isEmpty() || allowedMoves.isEmpty()) {
            Map<StandardCheckerColor, Boolean> firstTurns = new EnumMap<>(StandardCheckerColor.class);
            firstTurns.putAll(gameState.getFirstTurns());
            firstTurns.put(activeColor, false);

            builder.activeColor(StandardCheckerColor.getOpposite(activeColor))
                    .gamePhase(GamePhase.AWAITING_DICE)
                    .diceValues(List.of())
                    .remainingDiceValues(List.of())
                    .headMoves(0)
                    .firstTurns(Map.copyOf(firstTurns))
                    .allowedMoves(Map.of());
        }
        return builder.build();
    }
}
