package ivs.games.engine.backgammon.shortgame;

import ivs.games.accessories.backgammonboard.BoardState;
import ivs.games.accessories.backgammonboard.Checker;
import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardChecker;
import ivs.games.engine.GameEvent;
import ivs.games.engine.backgammon.AbstractBackgammonEngine;
import ivs.games.engine.backgammon.command.BearOff;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Enter;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.event.CheckerBorneOffEvent;
import ivs.games.engine.backgammon.event.CheckerMovedEvent;
import ivs.games.engine.backgammon.event.DiceRolledEvent;
import ivs.games.engine.backgammon.event.GameFinishedEvent;
import ivs.games.engine.backgammon.event.GameStartedEvent;
import ivs.games.engine.backgammon.event.TurnChangedEvent;
import ivs.games.engine.backgammon.shortgame.event.CheckerEnteredEvent;
import ivs.games.engine.backgammon.shortgame.event.CheckerHitEvent;
import ivs.games.engine.backgammon.state.GamePhase;
import ivs.games.engine.backgammon.state.GameSetup;
import ivs.games.engine.exception.EngineCommandException;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static ivs.games.engine.backgammon.shortgame.BoardGeometry.ADDRESSING;
import static ivs.games.engine.backgammon.shortgame.BoardGeometry.BEAR_OFF_POINT;
import static ivs.games.engine.backgammon.shortgame.BoardGeometry.HOME_START_POINT;

/**
 * Initializes short backgammon games and processes checker actions.
 *
 * <p>Validates bar entry, ordinary movements and bearing off.
 * A move resolver determines available actions, including mandatory
 * use of movement values.</p>
 *
 * <p>A single opposing checker is hit when an active checker moves
 * or enters onto its point. Points occupied by two or more opposing
 * checkers are blocked.</p>
 *
 * <p>Play passes to the opposite side when the movement pool is empty
 * or no legal actions remain. Bearing off the fifteenth checker
 * immediately finishes the game.</p>
 *
 * <p>Processing creates new snapshots without modifying supplied states.
 * The engine retains no state belonging to an individual game.</p>
 */
public class ShortBackgammonEngine extends AbstractBackgammonEngine<ShortGameState> {

    private static final int CHECKERS_PER_SIDE = 15;

    private final ShortMoveResolver moveResolver;

    /**
     * Creates an engine with the standard move resolver.
     */
    public ShortBackgammonEngine() {
        this(new ShortMoveResolver());
    }

    /*
     * Creates an engine with an explicitly supplied resolver.
     */
    ShortBackgammonEngine(@NonNull ShortMoveResolver moveResolver) {
        this.moveResolver = moveResolver;
    }

    /**
     * Creates the standard initial short backgammon arrangement.
     *
     * <p>Each side has two checkers at route point 1, five at point 12,
     * three at point 17 and five at point 19.
     * The starting side awaits dice.</p>
     *
     * @param initialSetup the validated initial setup
     * @return the initial immutable game state
     */
    @Override
    protected ShortGameState createInitialState(GameSetup initialSetup) {
        StandardBoard<StandardChecker> board = new StandardBoard<>();

        for (StandardCheckerColor color : StandardCheckerColor.values()) {
            StandardChecker checker = color == StandardCheckerColor.WHITE
                    ? StandardChecker.WHITE_MAN : StandardChecker.BLACK_MAN;

            board.getCell(ADDRESSING.toBoardIndex(color, 1))
                    .setState(Collections.nCopies(2, checker));
            board.getCell(ADDRESSING.toBoardIndex(color, 12))
                    .setState(Collections.nCopies(5, checker));
            board.getCell(ADDRESSING.toBoardIndex(color, 17))
                    .setState(Collections.nCopies(3, checker));
            board.getCell(ADDRESSING.toBoardIndex(color, 19))
                    .setState(Collections.nCopies(5, checker));
        }

        return ShortGameState.builder()
                .board(board.getState())
                .activeColor(initialSetup.getStartingColor())
                .gamePhase(GamePhase.AWAITING_DICE)
                .diceValues(List.of())
                .remainingDiceValues(List.of())
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .allowedEntries(Map.of())
                .winner(null)
                .events(List.of(new GameStartedEvent(initialSetup.getStartingColor())))
                .build();
    }

    /**
     * Stores the dice roll, creates the movement pool and resolves available actions.
     *
     * <p>Doubles provide four equal movement values.
     * If no legal action is available, play passes to the opposite side.
     * Otherwise, the active side enters the movement phase.</p>
     *
     * @param gameState the state awaiting dice
     * @param action    the validated dice result
     * @return the resulting immutable game state
     */
    @Override
    protected ShortGameState processDiceRolled(ShortGameState gameState, DiceRolled action) {
        List<Integer> diceValues = List.of(action.getFirst(), action.getSecond());
        List<Integer> movementPool = createMovementPool(action);
        List<GameEvent> events = new ArrayList<>();
        events.add(new DiceRolledEvent(gameState.getActiveColor(), action.getFirst(), action.getSecond()));

        MoveContext context = MoveContext.builder()
                .boardState(gameState.getBoard())
                .activeColor(gameState.getActiveColor())
                .diceValues(diceValues)
                .movementPool(movementPool)
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .allowedEntries(Map.of())
                .build();

        moveResolver.resolve(context);

        ShortGameState result = gameState.toBuilder()
                .diceValues(diceValues)
                .remainingDiceValues(movementPool)
                .gamePhase(GamePhase.AWAITING_MOVE)
                .allowedMoves(context.getAllowedMoves())
                .allowedBearOffs(context.getAllowedBearOffs())
                .allowedEntries(context.getAllowedEntries())
                .build();

        boolean noAvailableActions = context.getAllowedMoves().isEmpty()
                && context.getAllowedBearOffs().isEmpty()
                && context.getAllowedEntries().isEmpty();

        return noAvailableActions
                ? finishTurn(result, events)
                : result.toBuilder().events(List.copyOf(events)).build();
    }

    /**
     * Applies a validated movement between side-relative route points.
     *
     * <p>A single opposing checker at the destination is moved to its bar
     * before the active checker is placed. The selected movement value
     * is then consumed and available actions are recalculated.</p>
     *
     * <p>The supplied game state remains unchanged.</p>
     *
     * @param gameState the state awaiting movement
     * @param action    the validated movement action
     * @return the resulting immutable game state
     */
    @Override
    protected ShortGameState processMove(ShortGameState gameState, Move action) {
        int from = action.getFrom();
        int diceValue = action.getDiceValue();
        int to = from + diceValue;

        StandardCheckerColor color = gameState.getActiveColor();
        var board = new StandardBoard<>(gameState.getBoard());

        int sourceIndex = ADDRESSING.toBoardIndex(color, from);
        int destinationIndex = ADDRESSING.toBoardIndex(color, to);
        var destination = board.getCell(destinationIndex);
        List<GameEvent> events = new ArrayList<>();

        if (!destination.isEmpty() && destination.getTopCheckerColor() != color) {
            // validateMove() has already ensured that the opposing checker is alone.
            events.add(new CheckerHitEvent(
                    color, StandardCheckerColor.getOpposite(color), to));
            board.moveToBar(destinationIndex);
        }

        board.move(sourceIndex, destinationIndex);
        events.add(new CheckerMovedEvent(color, from, to, diceValue));

        return completeAction(gameState, board.getState(), diceValue, events);
    }

    /**
     * Applies a validated entry from the active side's bar.
     *
     * <p>A single opposing checker at the destination is moved to its bar
     * before the active checker enters. The selected movement value
     * is then consumed and available actions are recalculated.</p>
     *
     * <p>While active checkers remain on the bar, only further entries
     * can be available. The supplied game state remains unchanged.</p>
     *
     * @param gameState the state awaiting movement
     * @param action    the validated entry action
     * @return the resulting immutable game state
     */
    @Override
    protected ShortGameState processEnter(ShortGameState gameState, Enter action) {
        int to = action.getTo();
        int diceValue = action.getDiceValue();

        StandardCheckerColor color = gameState.getActiveColor();
        var board = new StandardBoard<>(gameState.getBoard());

        int destinationIndex = ADDRESSING.toBoardIndex(color, to);
        var destination = board.getCell(destinationIndex);
        List<GameEvent> events = new ArrayList<>();

        if (!destination.isEmpty() && destination.getTopCheckerColor() != color) {
            // validateEnter() has already ensured that the opposing checker is alone.
            events.add(new CheckerHitEvent(
                    color, StandardCheckerColor.getOpposite(color), to));
            board.moveToBar(destinationIndex);
        }

        board.moveFromBar(color, destinationIndex);
        events.add(new CheckerEnteredEvent(color, to));

        return completeAction(gameState, board.getState(), diceValue, events);
    }

    /**
     * Applies a validated bearing-off action.
     *
     * <p>Finishes the game immediately when all fifteen active checkers
     * have been borne off, even if movement values remain.
     * Otherwise, consumes the selected value and recalculates available actions.</p>
     *
     * <p>The supplied game state remains unchanged.</p>
     *
     * @param gameState the state awaiting movement
     * @param action    the validated bearing-off action
     * @return the resulting immutable game state
     */
    @Override
    protected ShortGameState processBearOff(ShortGameState gameState, BearOff action) {
        int from = action.getFrom();
        int diceValue = action.getDiceValue();

        StandardCheckerColor color = gameState.getActiveColor();
        var board = new StandardBoard<>(gameState.getBoard());

        board.moveToBorneOff(ADDRESSING.toBoardIndex(color, from));
        List<GameEvent> events = new ArrayList<>();
        events.add(new CheckerBorneOffEvent(color, from, diceValue));

        BoardState<?> boardState = board.getState();

        if (boardState.getBorneOff().get(color).size() == CHECKERS_PER_SIDE) {
            List<Integer> remaining = new ArrayList<>(gameState.getRemainingDiceValues());
            remaining.remove(Integer.valueOf(diceValue));
            events.add(new GameFinishedEvent(color));

            return gameState.toBuilder()
                    .board(boardState)
                    .remainingDiceValues(List.copyOf(remaining))
                    .gamePhase(GamePhase.FINISHED)
                    .winner(color)
                    .allowedMoves(Map.of())
                    .allowedBearOffs(Map.of())
                    .allowedEntries(Map.of())
                    .events(List.copyOf(events))
                    .build();
        }

        return completeAction(gameState, boardState, diceValue, events);
    }

    /*
     * Consumes one occurrence of the validated movement value.
     * Resolves actions on the updated board and passes play when the pool
     * is empty or no legal continuation remains.
     */
    private ShortGameState completeAction(
            ShortGameState gameState,
            BoardState<? extends Checker<?, ?>> boardState,
            int diceValue,
            List<GameEvent> events) {

        List<Integer> movementPool = new ArrayList<>(gameState.getRemainingDiceValues());
        movementPool.remove(Integer.valueOf(diceValue));

        List<Integer> remaining = List.copyOf(movementPool);

        MoveContext context = MoveContext.builder()
                .boardState(boardState)
                .activeColor(gameState.getActiveColor())
                .diceValues(gameState.getDiceValues())
                .movementPool(remaining)
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .allowedEntries(Map.of())
                .build();

        moveResolver.resolve(context);

        ShortGameState result = gameState.toBuilder()
                .board(boardState)
                .remainingDiceValues(remaining)
                .allowedMoves(context.getAllowedMoves())
                .allowedBearOffs(context.getAllowedBearOffs())
                .allowedEntries(context.getAllowedEntries())
                .build();

        boolean noAvailableActions = context.getAllowedMoves().isEmpty()
                && context.getAllowedBearOffs().isEmpty()
                && context.getAllowedEntries().isEmpty();

        return remaining.isEmpty() || noAvailableActions
                ? finishTurn(result, events)
                : result.toBuilder().events(List.copyOf(events)).build();
    }

    /*
     * Switches the active side and clears data belonging to the completed turn.
     * The board snapshot and other game properties are preserved.
     */
    private ShortGameState finishTurn(ShortGameState gameState, List<GameEvent> events) {
        StandardCheckerColor fromColor = gameState.getActiveColor();
        StandardCheckerColor toColor = StandardCheckerColor.getOpposite(fromColor);
        List<GameEvent> transitionEvents = new ArrayList<>(events);
        transitionEvents.add(new TurnChangedEvent(fromColor, toColor));

        return gameState.toBuilder()
                .activeColor(toColor)
                .gamePhase(GamePhase.AWAITING_DICE)
                .diceValues(List.of())
                .remainingDiceValues(List.of())
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .allowedEntries(Map.of())
                .events(List.copyOf(transitionEvents))
                .build();
    }

    /*
     * Checks an action against resolved options, including mandatory dice usage.
     * The point is a source for movement and bearing off,
     * or a destination for entry.
     */
    private static void validateAction(Map<Integer, Set<Integer>> options, int point, int diceValue) {
        Set<Integer> values = options.get(point);

        EngineCommandException.validate(
                values != null && values.contains(diceValue),
                "Action is not permitted at route point %d using die value %d",
                point, diceValue);
    }

    /**
     * Checks whether the requested checker movement can be applied.
     *
     * <p>Performs common phase, route and die validation first.
     * The active side's bar must be empty, the source must contain
     * an active checker, and the selected movement value must be available.</p>
     *
     * <p>The destination may be empty, occupied by active checkers,
     * or contain a single opposing checker.
     * The action must also satisfy resolved mandatory dice usage.</p>
     *
     * @param gameState the current game state
     * @param action    the movement action to validate
     * @throws EngineCommandException if the movement cannot be applied
     */
    @Override
    protected void validateMove(ShortGameState gameState, Move action) {
        super.validateMove(gameState, action);

        StandardCheckerColor color = gameState.getActiveColor();
        var boardState = gameState.getBoard();

        EngineCommandException.validate(
                boardState.getBars().get(color).isEmpty(),
                "All checkers must enter from the bar before moving");

        int from = action.getFrom();
        int diceValue = action.getDiceValue();

        EngineCommandException.validate(
                gameState.getRemainingDiceValues().contains(diceValue),
                "Movement value is unavailable: %d", diceValue);

        int sourceIndex = ADDRESSING.toBoardIndex(color, from);
        var source = boardState.getCells().get(sourceIndex);

        EngineCommandException.validate(
                !source.isEmpty() && source.getLast().getColor() == color,
                "Source route point %d must contain an active checker", from);

        int to = from + diceValue;
        int destinationIndex = ADDRESSING.toBoardIndex(color, to);
        var destination = boardState.getCells().get(destinationIndex);

        EngineCommandException.validate(
                destination.isEmpty()
                        || destination.getLast().getColor() == color
                        || destination.size() == 1,
                "Destination route point %d is blocked", to);

        validateAction(gameState.getAllowedMoves(), from, diceValue);
    }

    /**
     * Checks whether the requested bar entry can be applied.
     *
     * <p>The active side must have a checker on its bar.
     * The destination route point must be between one and six
     * and identifies the movement value to consume.</p>
     *
     * <p>The selected movement value must be available.
     * The destination may be empty, occupied by active checkers,
     * or contain a single opposing checker.
     * The action must also satisfy resolved mandatory dice usage.</p>
     *
     * @param gameState the current game state
     * @param action    the entry action to validate
     * @throws EngineCommandException if the entry cannot be applied
     */
    @Override
    protected void validateEnter(ShortGameState gameState, Enter action) {
        super.validateEnter(gameState, action);

        StandardCheckerColor color = gameState.getActiveColor();
        var boardState = gameState.getBoard();

        EngineCommandException.validate(
                !boardState.getBars().get(color).isEmpty(),
                "The active side's bar must contain a checker");

        int to = action.getTo();
        int diceValue = action.getDiceValue();

        EngineCommandException.validate(
                to >= MIN_DICE_VALUE && to <= MAX_DICE_VALUE,
                "Entry route point must be between %d and %d: %d",
                MIN_DICE_VALUE, MAX_DICE_VALUE, to);

        EngineCommandException.validate(
                gameState.getRemainingDiceValues().contains(diceValue),
                "Movement value is unavailable: %d", diceValue);

        int destinationIndex = ADDRESSING.toBoardIndex(color, to);
        var destination = boardState.getCells().get(destinationIndex);

        EngineCommandException.validate(
                destination.isEmpty()
                        || destination.getLast().getColor() == color
                        || destination.size() == 1,
                "Destination route point %d is blocked", to);

        validateAction(gameState.getAllowedEntries(), to, diceValue);
    }

    /**
     * Checks whether the requested bearing-off action can be applied.
     *
     * <p>The active side's bar must be empty, all its remaining checkers
     * must be in the home, and the source must contain an active checker.
     * The selected movement value must be available.</p>
     *
     * <p>An exact value permits bearing off from the source point.
     * An oversize value is permitted only when no active checker
     * occupies an earlier route point.
     * The action must also satisfy resolved mandatory dice usage.</p>
     *
     * @param gameState the current game state
     * @param action    the bearing-off action to validate
     * @throws EngineCommandException if bearing off cannot be applied
     */
    @Override
    protected void validateBearOff(ShortGameState gameState, BearOff action) {
        super.validateBearOff(gameState, action);

        StandardCheckerColor color = gameState.getActiveColor();
        var boardState = gameState.getBoard();

        EngineCommandException.validate(
                boardState.getBars().get(color).isEmpty(),
                "All checkers must enter from the bar before bearing off");

        int from = action.getFrom();
        int diceValue = action.getDiceValue();

        EngineCommandException.validate(
                gameState.getRemainingDiceValues().contains(diceValue),
                "Movement value is unavailable: %d", diceValue);

        var cells = boardState.getCells();
        int sourceIndex = ADDRESSING.toBoardIndex(color, from);
        var source = cells.get(sourceIndex);

        EngineCommandException.validate(
                !source.isEmpty() && source.getLast().getColor() == color,
                "Source route point %d must contain an active checker", from);

        boolean allHome = IntStream.range(1, HOME_START_POINT)
                .mapToObj(point -> cells.get(ADDRESSING.toBoardIndex(color, point)))
                .noneMatch(cell -> !cell.isEmpty() && cell.getLast().getColor() == color);

        EngineCommandException.validate(
                allHome,
                "All active checkers must be in the home before bearing off");

        int to = from + diceValue;

        EngineCommandException.validate(
                to >= BEAR_OFF_POINT,
                "Die value %d is insufficient to bear off from route point %d",
                diceValue, from);

        if (to > BEAR_OFF_POINT) {
            boolean hasEarlierChecker = IntStream.range(HOME_START_POINT, from)
                    .mapToObj(point -> cells.get(ADDRESSING.toBoardIndex(color, point)))
                    .anyMatch(cell -> !cell.isEmpty() && cell.getLast().getColor() == color);

            EngineCommandException.validate(
                    !hasEarlierChecker,
                    "Cannot bear off from route point %d using die value %d while an earlier point is occupied",
                    from, diceValue);
        }

        validateAction(gameState.getAllowedBearOffs(), from, diceValue);
    }
}
