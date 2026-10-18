package ivs.games.engine.backgammon.shortgame;

import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardChecker;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.command.Abort;
import ivs.games.engine.backgammon.command.BearOff;
import ivs.games.engine.backgammon.command.DiceRolled;
import ivs.games.engine.backgammon.command.Enter;
import ivs.games.engine.backgammon.command.Move;
import ivs.games.engine.backgammon.event.CheckerBorneOffEvent;
import ivs.games.engine.backgammon.event.CheckerMovedEvent;
import ivs.games.engine.backgammon.event.DiceRolledEvent;
import ivs.games.engine.backgammon.event.GameAbortedEvent;
import ivs.games.engine.backgammon.event.GameFinishedEvent;
import ivs.games.engine.backgammon.event.GameStartedEvent;
import ivs.games.engine.backgammon.event.TurnChangedEvent;
import ivs.games.engine.backgammon.shortgame.event.CheckerEnteredEvent;
import ivs.games.engine.backgammon.shortgame.event.CheckerHitEvent;
import ivs.games.engine.backgammon.state.GameSetup;
import ivs.games.engine.exception.EngineCommandException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ivs.games.accessories.backgammonboard.standard.StandardCheckerColor.WHITE;
import static ivs.games.engine.backgammon.shortgame.BoardGeometry.ADDRESSING;
import static ivs.games.engine.backgammon.state.GamePhase.ABORTED;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_DICE;
import static ivs.games.engine.backgammon.state.GamePhase.AWAITING_MOVE;
import static ivs.games.engine.backgammon.state.GamePhase.FINISHED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests short backgammon command sequences using the real engine and resolver.
 *
 * <p>Scenarios cover initial play, hitting, entry, blocked turns,
 * mandatory dice usage, bearing off, victory and system abort.
 * Earlier game snapshots must remain unchanged.</p>
 */
class ShortBackgammonEngineIntegrationTest {

    private final ShortBackgammonEngine engine = new ShortBackgammonEngine();

    /**
     * Verifies a complete ordinary turn from the initial arrangement,
     * followed by the opponent's roll.
     *
     * @param color the starting color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void initialPositionSupportsCompleteTurnAndOpponentRoll(StandardCheckerColor color) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        ShortGameState initial = engine.startGame(new GameSetup(color));
        assertEquals(List.of(new GameStartedEvent(color)), initial.getEvents());

        ShortGameState rolled = engine.processCommand(initial, new DiceRolled(color, 1, 2));

        assertEquals(AWAITING_MOVE, rolled.getGamePhase());
        assertEquals(color, rolled.getActiveColor());
        assertEquals(List.of(1, 2), rolled.getDiceValues());
        assertEquals(List.of(1, 2), rolled.getRemainingDiceValues());
        assertTrue(rolled.getAllowedMoves().get(1).contains(1));
        assertTrue(rolled.getAllowedEntries().isEmpty());
        assertTrue(rolled.getAllowedBearOffs().isEmpty());

        ShortGameState moved = engine.processCommand(rolled, new Move(color, 1, 1));

        assertEquals(AWAITING_MOVE, moved.getGamePhase());
        assertEquals(List.of(2), moved.getRemainingDiceValues());
        assertEquals(1, stackSize(moved, color, 1));
        assertEquals(1, stackSize(moved, color, 2));
        assertTrue(moved.getAllowedMoves().get(2).contains(2));

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(moved, new Move(opponent, 2, 2)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(moved, new DiceRolled(color, 3, 5)));

        ShortGameState completed = engine.processCommand(moved, new Move(color, 2, 2));

        assertTurnPassed(completed, opponent);
        assertEquals(1, stackSize(completed, color, 1));
        assertEquals(0, stackSize(completed, color, 2));
        assertEquals(1, stackSize(completed, color, 4));

        ShortGameState opponentRoll = engine.processCommand(
                completed, new DiceRolled(opponent, 1, 2));

        assertEquals(AWAITING_MOVE, opponentRoll.getGamePhase());
        assertEquals(opponent, opponentRoll.getActiveColor());

        assertEquals(AWAITING_DICE, initial.getGamePhase());
        assertEquals(2, stackSize(initial, color, 1));
        assertEquals(0, stackSize(initial, color, 4));
        assertEquals(List.of(1, 2), rolled.getRemainingDiceValues());
        assertEquals(1, stackSize(moved, color, 2));
    }

    /**
     * Verifies that a double supports four consecutive movements
     * and passes the turn only after the fourth value is consumed.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void doubleConsumesFourValuesBeforePassingTurn(StandardCheckerColor color) {
        ShortGameState initial = engine.startGame(new GameSetup(color));
        ShortGameState rolled = engine.processCommand(initial, new DiceRolled(color, 1, 1));

        assertEquals(List.of(1, 1, 1, 1), rolled.getRemainingDiceValues());

        ShortGameState first = engine.processCommand(rolled, new Move(color, 19, 1));
        ShortGameState second = engine.processCommand(first, new Move(color, 20, 1));
        ShortGameState third = engine.processCommand(second, new Move(color, 21, 1));

        assertEquals(List.of(1, 1, 1), first.getRemainingDiceValues());
        assertEquals(List.of(1, 1), second.getRemainingDiceValues());
        assertEquals(List.of(1), third.getRemainingDiceValues());
        assertEquals(AWAITING_MOVE, third.getGamePhase());

        ShortGameState completed = engine.processCommand(third, new Move(color, 22, 1));

        assertTurnPassed(completed, StandardCheckerColor.getOpposite(color));
        assertEquals(4, stackSize(completed, color, 19));
        assertEquals(1, stackSize(completed, color, 23));

        assertEquals(5, stackSize(initial, color, 19));
        assertEquals(0, stackSize(initial, color, 23));
    }

    /**
     * Verifies hitting during movement, passing the turn, and the opponent's
     * subsequent mandatory entry from the bar.
     *
     * @param color the side performing the hit
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void movementHitsOpponentWhoMustThenEnterFromBar(StandardCheckerColor color) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        ShortGameState original = position(
                color, Map.of(8, 15), Map.of(11, 1, 20, 14), 0);
        assertTrue(original.getEvents().isEmpty());

        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 3, 2));
        assertEquals(List.of(new DiceRolledEvent(color, 3, 2)), rolled.getEvents());

        ShortGameState hit = engine.processCommand(rolled, new Move(color, 8, 3));

        assertEquals(List.of(
                new CheckerHitEvent(color, opponent, 11),
                new CheckerMovedEvent(color, 8, 11, 3)), hit.getEvents());
        assertEquals(1, hit.getBoard().getBars().get(opponent).size());
        assertEquals(List.of(man(color)), hit.getBoard().getCells()
                .get(ADDRESSING.toBoardIndex(color, 11)));
        assertEquals(List.of(2), hit.getRemainingDiceValues());

        ShortGameState completed = engine.processCommand(hit, new Move(color, 11, 2));

        assertEquals(List.of(
                new CheckerMovedEvent(color, 11, 13, 2),
                new TurnChangedEvent(color, opponent)), completed.getEvents());
        assertTurnPassed(completed, opponent);
        assertEquals(1, stackSize(completed, color, 13));

        ShortGameState opponentRoll = engine.processCommand(
                completed, new DiceRolled(opponent, 1, 2));

        assertEquals(List.of(new DiceRolledEvent(opponent, 1, 2)), opponentRoll.getEvents());
        assertTrue(opponentRoll.getAllowedMoves().isEmpty());
        assertTrue(opponentRoll.getAllowedBearOffs().isEmpty());
        assertEquals(Map.of(1, Set.of(1), 2, Set.of(2)), opponentRoll.getAllowedEntries());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(opponentRoll, new Move(opponent, 5, 1)));

        ShortGameState entered = engine.processCommand(opponentRoll, new Enter(opponent, 1));

        assertEquals(List.of(new CheckerEnteredEvent(opponent, 1)), entered.getEvents());
        assertTrue(entered.getBoard().getBars().get(opponent).isEmpty());
        assertEquals(1, stackSize(entered, opponent, 1));
        assertTrue(entered.getAllowedEntries().isEmpty());
        assertFalse(entered.getAllowedMoves().isEmpty());

        assertTrue(original.getBoard().getBars().get(opponent).isEmpty());
        assertEquals(List.of(man(opponent)), original.getBoard().getCells()
                .get(ADDRESSING.toBoardIndex(color, 11)));
    }

    /**
     * Verifies hitting during bar entry and ordinary movement
     * after the last active checker has entered.
     *
     * @param color the entering side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void entryHitsOpponentAndAllowsRemainingValueForMovement(StandardCheckerColor color) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        ShortGameState original = position(
                color, Map.of(8, 14), Map.of(3, 1, 20, 14), 1);

        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 3, 5));

        assertTrue(rolled.getAllowedMoves().isEmpty());
        assertTrue(rolled.getAllowedBearOffs().isEmpty());
        assertTrue(rolled.getAllowedEntries().get(3).contains(3));

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(rolled, new Move(color, 8, 3)));

        ShortGameState entered = engine.processCommand(rolled, new Enter(color, 3));

        assertEquals(List.of(
                new CheckerHitEvent(color, opponent, 3),
                new CheckerEnteredEvent(color, 3)), entered.getEvents());
        assertTrue(entered.getBoard().getBars().get(color).isEmpty());
        assertEquals(1, entered.getBoard().getBars().get(opponent).size());
        assertEquals(List.of(man(color)), entered.getBoard().getCells()
                .get(ADDRESSING.toBoardIndex(color, 3)));
        assertTrue(entered.getAllowedEntries().isEmpty());
        assertTrue(entered.getAllowedMoves().get(3).contains(5));

        ShortGameState completed = engine.processCommand(entered, new Move(color, 3, 5));

        assertTurnPassed(completed, opponent);
        assertEquals(15, stackSize(completed, color, 8));
        assertEquals(0, stackSize(completed, color, 3));
        assertEquals(1, completed.getBoard().getBars().get(opponent).size());

        assertEquals(1, original.getBoard().getBars().get(color).size());
        assertTrue(original.getBoard().getBars().get(opponent).isEmpty());
    }

    /**
     * Verifies automatic passing when both entry points are closed.
     * No board contents are changed by the blocked roll.
     *
     * @param color the blocked side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void blockedBarRollPassesTurnWithoutChangingPosition(StandardCheckerColor color) {
        ShortGameState original = position(
                color, Map.of(8, 14), Map.of(1, 2, 2, 2, 20, 11), 1);

        ShortGameState completed = engine.processCommand(original, new DiceRolled(color, 1, 2));

        assertEquals(List.of(
                new DiceRolledEvent(color, 1, 2),
                new TurnChangedEvent(color, StandardCheckerColor.getOpposite(color))),
                completed.getEvents());
        assertTurnPassed(completed, StandardCheckerColor.getOpposite(color));
        assertEquals(1, completed.getBoard().getBars().get(color).size());
        assertEquals(original.getBoard().getCells(), completed.getBoard().getCells());
        assertEquals(original.getBoard().getBars(), completed.getBoard().getBars());
        assertEquals(original.getBoard().getBorneOff(), completed.getBoard().getBorneOff());
    }

    /**
     * Verifies that the turn ends when only some bar checkers can enter.
     * Ordinary movements remain forbidden while another checker is on the bar.
     *
     * @param color the entering side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void partialEntryPassesTurnWhenRemainingEntryIsBlocked(StandardCheckerColor color) {
        ShortGameState original = position(
                color, Map.of(8, 13), Map.of(2, 2, 20, 13), 2);

        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 1, 2));

        assertEquals(Map.of(1, Set.of(1)), rolled.getAllowedEntries());
        assertTrue(rolled.getAllowedMoves().isEmpty());

        ShortGameState completed = engine.processCommand(rolled, new Enter(color, 1));

        assertTurnPassed(completed, StandardCheckerColor.getOpposite(color));
        assertEquals(1, completed.getBoard().getBars().get(color).size());
        assertEquals(1, stackSize(completed, color, 1));
        assertEquals(13, stackSize(completed, color, 8));

        assertEquals(2, original.getBoard().getBars().get(color).size());
    }

    /**
     * Verifies that the higher entry value is required when either entry
     * is possible but neither permits a second action.
     *
     * @param color the entering side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void higherEntryValueIsMandatoryWhenOnlyOneValueCanBeUsed(StandardCheckerColor color) {
        ShortGameState original = position(
                color, Map.of(), Map.of(3, 2, 20, 13), 1);

        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 1, 2));

        assertEquals(Map.of(2, Set.of(2)), rolled.getAllowedEntries());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(rolled, new Enter(color, 1)));

        ShortGameState completed = engine.processCommand(rolled, new Enter(color, 2));

        assertTurnPassed(completed, StandardCheckerColor.getOpposite(color));
        assertTrue(completed.getBoard().getBars().get(color).isEmpty());
        assertEquals(1, stackSize(completed, color, 2));
        assertEquals(14, completed.getBoard().getBorneOff().get(color).size());
    }

    /**
     * Verifies that closed intermediate points prevent movement by the sum
     * of two dice, while a legal ordering uses both values separately.
     *
     * @param color the moving side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void closedIntermediatePointForcesLegalDiceOrder(StandardCheckerColor color) {
        ShortGameState original = position(
                color, Map.of(8, 1), Map.of(9, 2, 20, 13), 0);

        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 1, 2));

        assertEquals(Map.of(8, Set.of(2)), rolled.getAllowedMoves());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(rolled, new Move(color, 8, 1)));

        ShortGameState first = engine.processCommand(rolled, new Move(color, 8, 2));

        assertEquals(Map.of(10, Set.of(1)), first.getAllowedMoves());
        assertEquals(List.of(1), first.getRemainingDiceValues());

        ShortGameState completed = engine.processCommand(first, new Move(color, 10, 1));

        assertTurnPassed(completed, StandardCheckerColor.getOpposite(color));
        assertEquals(1, stackSize(completed, color, 11));
        assertEquals(0, stackSize(completed, color, 8));
        assertEquals(0, stackSize(completed, color, 10));
    }

    /**
     * Verifies that the higher movement value is mandatory when either first
     * movement is possible but the shared continuation is closed.
     *
     * @param color the moving side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void higherMovementValueIsMandatoryWhenContinuationIsBlocked(StandardCheckerColor color) {
        ShortGameState original = position(
                color, Map.of(8, 1), Map.of(11, 2, 20, 13), 0);

        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 1, 2));

        assertEquals(Map.of(8, Set.of(2)), rolled.getAllowedMoves());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(rolled, new Move(color, 8, 1)));

        ShortGameState completed = engine.processCommand(rolled, new Move(color, 8, 2));

        assertTurnPassed(completed, StandardCheckerColor.getOpposite(color));
        assertEquals(1, stackSize(completed, color, 10));
    }

    /**
     * Verifies that a checker outside home prevents bearing off.
     * After it enters home, exact bearing off becomes available.
     *
     * @param color the active color
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void enteringHomeEnablesBearingOffForRemainingValue(StandardCheckerColor color) {
        ShortGameState original = position(
                color, Map.of(18, 1, 24, 1), Map.of(1, 15), 0);

        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 1, 1));

        assertTrue(rolled.getAllowedBearOffs().isEmpty());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(rolled, new BearOff(color, 24, 1)));

        ShortGameState home = engine.processCommand(rolled, new Move(color, 18, 1));

        assertEquals(AWAITING_MOVE, home.getGamePhase());
        assertEquals(List.of(1, 1, 1), home.getRemainingDiceValues());
        assertTrue(home.getAllowedBearOffs().get(24).contains(1));

        ShortGameState borneOff = engine.processCommand(home, new BearOff(color, 24, 1));

        assertEquals(List.of(new CheckerBorneOffEvent(color, 24, 1)), borneOff.getEvents());
        assertEquals(14, borneOff.getBoard().getBorneOff().get(color).size());
        assertEquals(List.of(1, 1), borneOff.getRemainingDiceValues());
        assertEquals(1, stackSize(borneOff, color, 19));
        assertEquals(0, stackSize(borneOff, color, 24));
        assertEquals(AWAITING_MOVE, borneOff.getGamePhase());

        assertEquals(13, original.getBoard().getBorneOff().get(color).size());
    }

    /**
     * Verifies that movement before bearing off is mandatory
     * when it permits both values to be consumed.
     *
     * @param color the winning side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void movementBeforeFinalBearOffIsRequiredToUseBothValues(StandardCheckerColor color) {
        ShortGameState original = position(
                color, Map.of(23, 1), Map.of(1, 15), 0);

        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 1, 2));

        assertEquals(Map.of(23, Set.of(1)), rolled.getAllowedMoves());
        assertTrue(rolled.getAllowedBearOffs().isEmpty());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(rolled, new BearOff(color, 23, 2)));

        ShortGameState moved = engine.processCommand(rolled, new Move(color, 23, 1));

        assertEquals(Map.of(24, Set.of(2)), moved.getAllowedBearOffs());
        assertEquals(List.of(2), moved.getRemainingDiceValues());

        ShortGameState finished = engine.processCommand(moved, new BearOff(color, 24, 2));

        assertEquals(List.of(
                new CheckerBorneOffEvent(color, 24, 2),
                new GameFinishedEvent(color)), finished.getEvents());
        assertEquals(FINISHED, finished.getGamePhase());
        assertEquals(color, finished.getWinner());
        assertEquals(color, finished.getActiveColor());
        assertEquals(15, finished.getBoard().getBorneOff().get(color).size());
        assertTrue(finished.getRemainingDiceValues().isEmpty());
        assertTrue(finished.getAllowedMoves().isEmpty());
        assertTrue(finished.getAllowedBearOffs().isEmpty());
        assertTrue(finished.getAllowedEntries().isEmpty());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(finished, new DiceRolled(color, 1, 2)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(finished, new Abort()));

        assertEquals(14, original.getBoard().getBorneOff().get(color).size());
        assertEquals(1, stackSize(original, color, 23));
    }

    /**
     * Verifies victory before a double is exhausted.
     * The final checker may be borne off using an oversize value.
     *
     * @param color the winning side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void victoryEndsTurnWithUnusedDoubleValues(StandardCheckerColor color) {
        ShortGameState original = position(
                color, Map.of(24, 2), Map.of(1, 15), 0);

        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 2, 2));

        assertEquals(Map.of(24, Set.of(2)), rolled.getAllowedBearOffs());

        ShortGameState first = engine.processCommand(rolled, new BearOff(color, 24, 2));

        assertEquals(AWAITING_MOVE, first.getGamePhase());
        assertEquals(List.of(2, 2, 2), first.getRemainingDiceValues());
        assertEquals(14, first.getBoard().getBorneOff().get(color).size());

        ShortGameState finished = engine.processCommand(first, new BearOff(color, 24, 2));

        assertEquals(FINISHED, finished.getGamePhase());
        assertEquals(color, finished.getWinner());
        assertEquals(color, finished.getActiveColor());
        assertEquals(List.of(2, 2), finished.getRemainingDiceValues());
        assertEquals(15, finished.getBoard().getBorneOff().get(color).size());
        assertTrue(finished.getAllowedMoves().isEmpty());
        assertTrue(finished.getAllowedBearOffs().isEmpty());
        assertTrue(finished.getAllowedEntries().isEmpty());

        assertEquals(14, first.getBoard().getBorneOff().get(color).size());
        assertNull(first.getWinner());
    }

    /**
     * Verifies that abort preserves short-game entry options and board data
     * after a real roll, and prevents every subsequent action.
     */
    @Test
    void abortPreservesEntryStateAndRejectsFurtherActions() {
        ShortGameState original = position(
                WHITE, Map.of(8, 14), Map.of(20, 15), 1);

        ShortGameState rolled = engine.processCommand(original, new DiceRolled(WHITE, 1, 2));
        ShortGameState aborted = engine.processCommand(rolled, new Abort());

        assertEquals(List.of(new GameAbortedEvent()), aborted.getEvents());
        assertEquals(ABORTED, aborted.getGamePhase());
        assertEquals(AWAITING_MOVE, rolled.getGamePhase());
        assertNotSame(rolled, aborted);
        assertSame(rolled.getBoard(), aborted.getBoard());
        assertSame(rolled.getDiceValues(), aborted.getDiceValues());
        assertSame(rolled.getRemainingDiceValues(), aborted.getRemainingDiceValues());
        assertSame(rolled.getAllowedMoves(), aborted.getAllowedMoves());
        assertSame(rolled.getAllowedBearOffs(), aborted.getAllowedBearOffs());
        assertSame(rolled.getAllowedEntries(), aborted.getAllowedEntries());
        assertEquals(rolled.getActiveColor(), aborted.getActiveColor());
        assertFalse(aborted.getAllowedEntries().isEmpty());
        assertNull(aborted.getWinner());

        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(aborted, new Enter(WHITE, 1)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(aborted, new Move(WHITE, 8, 1)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(aborted, new BearOff(WHITE, 24, 1)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(aborted, new DiceRolled(WHITE, 3, 5)));
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(aborted, new Abort()));
    }

    /**
     * Verifies that a checker hit during bearing off must enter and return home
     * before bearing off can resume.
     *
     * @param color the side whose checker is hit
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void hitDuringBearingOffRequiresEntryAndReturnHome(StandardCheckerColor color) {
        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        ShortGameState original = position(
                opponent, Map.of(6, 1, 14, 14), Map.of(1, 1, 7, 1), 0);

        ShortGameState opponentRoll = engine.processCommand(original, new DiceRolled(opponent, 1, 2));
        ShortGameState hit = engine.processCommand(opponentRoll, new Move(opponent, 6, 1));
        ShortGameState opponentCompleted = engine.processCommand(hit, new Move(opponent, 7, 2));

        assertTurnPassed(opponentCompleted, color);
        assertEquals(1, hit.getBoard().getBars().get(color).size());
        assertEquals(13, hit.getBoard().getBorneOff().get(color).size());

        ShortGameState entryRoll = engine.processCommand(opponentCompleted, new DiceRolled(color, 1, 6));
        assertTrue(entryRoll.getAllowedMoves().isEmpty());
        assertTrue(entryRoll.getAllowedBearOffs().isEmpty());
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(entryRoll, new BearOff(color, 24, 1)));

        ShortGameState entered = engine.processCommand(entryRoll, new Enter(color, 1));
        assertTrue(entered.getBoard().getBars().get(color).isEmpty());
        assertTrue(entered.getAllowedBearOffs().isEmpty());
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(entered, new BearOff(color, 24, 6)));

        ShortGameState outsideHome = engine.processCommand(entered, new Move(color, 1, 6));
        assertTurnPassed(outsideHome, opponent);

        ShortGameState replyRoll = engine.processCommand(outsideHome, new DiceRolled(opponent, 1, 1));
        ShortGameState replyFirst = engine.processCommand(replyRoll, new Move(opponent, 14, 1));
        ShortGameState replySecond = engine.processCommand(replyFirst, new Move(opponent, 14, 1));
        ShortGameState replyThird = engine.processCommand(replySecond, new Move(opponent, 14, 1));
        ShortGameState replyCompleted = engine.processCommand(replyThird, new Move(opponent, 14, 1));
        assertTurnPassed(replyCompleted, color);

        ShortGameState returnRoll = engine.processCommand(replyCompleted, new DiceRolled(color, 6, 6));
        ShortGameState returning = engine.processCommand(returnRoll, new Move(color, 7, 6));
        assertTrue(returning.getAllowedBearOffs().isEmpty());

        ShortGameState home = engine.processCommand(returning, new Move(color, 13, 6));
        assertTrue(home.getAllowedBearOffs().get(19).contains(6));
        assertEquals(List.of(6, 6), home.getRemainingDiceValues());

        ShortGameState firstOff = engine.processCommand(home, new BearOff(color, 19, 6));
        ShortGameState finished = engine.processCommand(firstOff, new BearOff(color, 24, 6));
        assertEquals(FINISHED, finished.getGamePhase());
        assertEquals(color, finished.getWinner());
        assertEquals(15, finished.getBoard().getBorneOff().get(color).size());
        assertTrue(original.getBoard().getBars().get(color).isEmpty());
        assertEquals(13, original.getBoard().getBorneOff().get(color).size());
        assertEquals(1, stackSize(outsideHome, color, 7));
    }

    /**
     * Verifies that multiple bar checkers enter using separate double values.
     * Ordinary movement becomes available only after the last entry.
     *
     * @param color the entering side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void doubleEntersAllBarCheckersBeforeOrdinaryMovement(StandardCheckerColor color) {
        ShortGameState original = position(color, Map.of(8, 12), Map.of(20, 15), 3);
        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 2, 2));
        assertEquals(List.of(2, 2, 2, 2), rolled.getRemainingDiceValues());
        assertEquals(Map.of(2, Set.of(2)), rolled.getAllowedEntries());
        assertTrue(rolled.getAllowedMoves().isEmpty());

        ShortGameState first = engine.processCommand(rolled, new Enter(color, 2));
        assertEquals(2, first.getBoard().getBars().get(color).size());
        assertEquals(List.of(2, 2, 2), first.getRemainingDiceValues());
        assertTrue(first.getAllowedMoves().isEmpty());
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(first, new Move(color, 8, 2)));

        ShortGameState second = engine.processCommand(first, new Enter(color, 2));
        assertEquals(1, second.getBoard().getBars().get(color).size());
        assertEquals(List.of(2, 2), second.getRemainingDiceValues());
        assertTrue(second.getAllowedMoves().isEmpty());

        ShortGameState third = engine.processCommand(second, new Enter(color, 2));
        assertTrue(third.getBoard().getBars().get(color).isEmpty());
        assertEquals(3, stackSize(third, color, 2));
        assertEquals(List.of(2), third.getRemainingDiceValues());
        assertTrue(third.getAllowedEntries().isEmpty());
        assertTrue(third.getAllowedMoves().get(2).contains(2));

        ShortGameState completed = engine.processCommand(third, new Move(color, 2, 2));
        assertTurnPassed(completed, StandardCheckerColor.getOpposite(color));
        assertEquals(2, stackSize(completed, color, 2));
        assertEquals(1, stackSize(completed, color, 4));
        assertEquals(3, original.getBoard().getBars().get(color).size());
    }

    /**
     * Verifies passing after only part of a double can be consumed
     * because every remaining continuation is blocked.
     *
     * @param color the moving side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void blockedContinuationEndsPartiallyUsedDouble(StandardCheckerColor color) {
        ShortGameState original = position(color, Map.of(8, 1), Map.of(14, 2, 20, 13), 0);
        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 2, 2));
        assertEquals(Map.of(8, Set.of(2)), rolled.getAllowedMoves());

        ShortGameState first = engine.processCommand(rolled, new Move(color, 8, 2));
        assertEquals(AWAITING_MOVE, first.getGamePhase());
        assertEquals(List.of(2, 2, 2), first.getRemainingDiceValues());
        assertEquals(Map.of(10, Set.of(2)), first.getAllowedMoves());

        ShortGameState completed = engine.processCommand(first, new Move(color, 10, 2));
        assertTurnPassed(completed, StandardCheckerColor.getOpposite(color));
        assertEquals(1, stackSize(completed, color, 12));
        assertEquals(0, stackSize(completed, color, 8));
        assertEquals(0, stackSize(completed, color, 10));
        assertEquals(2, stackSize(completed, color, 14));
        assertEquals(List.of(2, 2, 2), first.getRemainingDiceValues());
    }

    /**
     * Verifies that an earlier home checker permits exact bearing off
     * but prevents oversize bearing off from the same source.
     *
     * @param color the active side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void exactBearingOffIsAllowedButOversizeIsRejectedWithEarlierChecker(StandardCheckerColor color) {
        ShortGameState original = position(color, Map.of(19, 1, 24, 1), Map.of(1, 15), 0);
        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 1, 2));
        assertEquals(Map.of(24, Set.of(1)), rolled.getAllowedBearOffs());
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(rolled, new BearOff(color, 24, 2)));

        ShortGameState borneOff = engine.processCommand(rolled, new BearOff(color, 24, 1));
        assertEquals(14, borneOff.getBoard().getBorneOff().get(color).size());
        assertEquals(List.of(2), borneOff.getRemainingDiceValues());
        assertEquals(Map.of(19, Set.of(2)), borneOff.getAllowedMoves());
        assertTrue(borneOff.getAllowedBearOffs().isEmpty());

        ShortGameState completed = engine.processCommand(borneOff, new Move(color, 19, 2));
        assertTurnPassed(completed, StandardCheckerColor.getOpposite(color));
        assertEquals(1, stackSize(completed, color, 21));
        assertEquals(0, stackSize(completed, color, 24));
        assertNull(completed.getWinner());
        assertEquals(13, original.getBoard().getBorneOff().get(color).size());
        assertEquals(1, stackSize(original, color, 24));
    }

    /**
     * Verifies mandatory use of both dice on separate checkers
     * when their combined destination is blocked.
     *
     * @param color the moving side
     */
    @ParameterizedTest
    @EnumSource(StandardCheckerColor.class)
    void bothValuesAreUsedOnSeparateCheckersWhenCombinedMovementIsBlocked(StandardCheckerColor color) {
        ShortGameState original = position(color, Map.of(8, 2), Map.of(11, 2, 20, 13), 0);
        ShortGameState rolled = engine.processCommand(original, new DiceRolled(color, 1, 2));
        assertEquals(Map.of(8, Set.of(1, 2)), rolled.getAllowedMoves());

        ShortGameState first = engine.processCommand(rolled, new Move(color, 8, 1));
        assertEquals(AWAITING_MOVE, first.getGamePhase());
        assertEquals(List.of(2), first.getRemainingDiceValues());
        assertEquals(Map.of(8, Set.of(2)), first.getAllowedMoves());
        assertThrows(EngineCommandException.class,
                () -> engine.processCommand(first, new Move(color, 9, 2)));

        ShortGameState completed = engine.processCommand(first, new Move(color, 8, 2));
        assertTurnPassed(completed, StandardCheckerColor.getOpposite(color));
        assertEquals(0, stackSize(completed, color, 8));
        assertEquals(1, stackSize(completed, color, 9));
        assertEquals(1, stackSize(completed, color, 10));
        assertEquals(2, stackSize(completed, color, 11));
        assertEquals(2, stackSize(original, color, 8));
        assertEquals(1, stackSize(first, color, 8));
    }

    /*
     * Creates a scenario awaiting dice.
     * Both placement maps use active-side route coordinates.
     * Unplaced checkers are stored in their respective borne-off cells.
     */
    private static ShortGameState position(
            StandardCheckerColor color,
            Map<Integer, Integer> ownStacks,
            Map<Integer, Integer> opponentStacks,
            int barCount) {

        StandardCheckerColor opponent = StandardCheckerColor.getOpposite(color);
        StandardChecker ownChecker = man(color);
        StandardChecker opposingChecker = man(opponent);
        StandardBoard<StandardChecker> board = new StandardBoard<>();

        ownStacks.forEach((point, count) ->
                board.getCell(ADDRESSING.toBoardIndex(color, point))
                        .setState(Collections.nCopies(count, ownChecker)));

        opponentStacks.forEach((point, count) ->
                board.getCell(ADDRESSING.toBoardIndex(color, point))
                        .setState(Collections.nCopies(count, opposingChecker)));

        board.getBar(color).setState(Collections.nCopies(barCount, ownChecker));

        int ownCount = ownStacks.values().stream().mapToInt(Integer::intValue).sum();
        int opponentCount = opponentStacks.values().stream().mapToInt(Integer::intValue).sum();

        board.getBorneOff(color)
                .setState(Collections.nCopies(15 - ownCount - barCount, ownChecker));
        board.getBorneOff(opponent)
                .setState(Collections.nCopies(15 - opponentCount, opposingChecker));

        return ShortGameState.builder()
                .board(board.getState())
                .activeColor(color)
                .gamePhase(AWAITING_DICE)
                .diceValues(List.of())
                .remainingDiceValues(List.of())
                .allowedMoves(Map.of())
                .allowedBearOffs(Map.of())
                .allowedEntries(Map.of())
                .winner(null)
                .build();
    }

    /*
     * Checks the complete turn reset for either next active color.
     */
    private static void assertTurnPassed(ShortGameState state, StandardCheckerColor nextColor) {
        assertEquals(nextColor, state.getActiveColor());
        assertEquals(AWAITING_DICE, state.getGamePhase());
        assertTrue(state.getDiceValues().isEmpty());
        assertTrue(state.getRemainingDiceValues().isEmpty());
        assertTrue(state.getAllowedMoves().isEmpty());
        assertTrue(state.getAllowedBearOffs().isEmpty());
        assertTrue(state.getAllowedEntries().isEmpty());
        assertNull(state.getWinner());
    }

    /*
     * Reads stack size using the specified side's route coordinates.
     */
    private static int stackSize(ShortGameState state, StandardCheckerColor color, int point) {
        int index = ADDRESSING.toBoardIndex(color, point);
        return state.getBoard().getCells().get(index).size();
    }

    /*
     * Selects the ordinary checker constant for the specified color.
     */
    private static StandardChecker man(StandardCheckerColor color) {
        return color == WHITE ? StandardChecker.WHITE_MAN : StandardChecker.BLACK_MAN;
    }
}
