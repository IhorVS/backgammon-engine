package ivs.games.engine.backgammon.render;

import ivs.games.accessories.backgammonboard.BoardAddressing;
import ivs.games.accessories.backgammonboard.BoardState;
import ivs.games.accessories.backgammonboard.Checker;
import ivs.games.accessories.backgammonboard.standard.StandardBoard;
import ivs.games.accessories.backgammonboard.standard.StandardCheckerColor;
import ivs.games.engine.backgammon.longgame.LongGameState;
import ivs.games.engine.backgammon.shortgame.ShortGameState;
import lombok.NonNull;
import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ivs.games.engine.backgammon.longgame.BoardGeometry.ADDRESSING;

/**
 * Renders a backgammon position with point numbers relative to the selected side.
 */
@SuppressWarnings("StringRepeatCanBeUsed")
public final class BackgammonBoardRenderer {

    private static final int HALF_CELL_COUNT = StandardBoard.PLAYING_CELL_COUNT / 2;
    private static final int GROUP_CELL_COUNT = HALF_CELL_COUNT / 2;
    private static final int MIN_HALF_HEIGHT = 5;
    private static final String EMPTY_SLOT = "..";
    private static final String INDENT = "     ";
    private static final String CELL_SEPARATOR = "  ";

    /**
     * Renders a long backgammon position using the selected side's route points.
     *
     * @param state      the game snapshot
     * @param perspective the side whose route points are displayed
     * @return the rendered board
     */
    public String render(@NonNull LongGameState state, @NonNull StandardCheckerColor perspective) {
        return render(state.getBoard(), ADDRESSING, perspective);
    }

    /**
     * Renders a short backgammon position using the selected side's route points.
     *
     * @param state      the game snapshot
     * @param perspective the side whose route points are displayed
     * @return the rendered board
     */
    public String render(@NonNull ShortGameState state, @NonNull StandardCheckerColor perspective) {
        return render(state.getBoard(), ivs.games.engine.backgammon.shortgame.BoardGeometry.ADDRESSING, perspective);
    }

    private String render(
            BoardState<? extends Checker<?, ?>> state,
            BoardAddressing<StandardCheckerColor> addressing,
            StandardCheckerColor perspective) {
        List<List<String>> cells = state.getCells().stream()
                .map(BackgammonBoardRenderer::getCodes)
                .toList();
        Validate.isTrue(cells.size() == StandardBoard.PLAYING_CELL_COUNT,
                "Board has %d playing cells; expected %d", cells.size(), StandardBoard.PLAYING_CELL_COUNT);

        int cellWidth = Math.max(EMPTY_SLOT.length(), cells.stream()
                .flatMap(List::stream)
                .mapToInt(String::length)
                .max()
                .orElse(0));
        int groupWidth = GROUP_CELL_COUNT * cellWidth
                + (GROUP_CELL_COUNT - 1) * CELL_SEPARATOR.length();
        String border = INDENT + "+" + "-".repeat(groupWidth + 2)
                + "+" + "-".repeat(groupWidth + 2) + "+";
        int topHeight = Math.max(MIN_HALF_HEIGHT, cells.subList(HALF_CELL_COUNT, cells.size()).stream()
                .mapToInt(List::size).max().orElse(0));
        int bottomHeight = Math.max(MIN_HALF_HEIGHT, cells.subList(0, HALF_CELL_COUNT).stream()
                .mapToInt(List::size).max().orElse(0));
        StringBuilder result = new StringBuilder();

        appendIndices(result, true, cellWidth, addressing, perspective);
        result.append(border).append('\n');
        appendHalf(result, cells, true, topHeight, cellWidth);
        result.append(INDENT).append("| ")
                .append(" ".repeat(groupWidth)).append(" | ")
                .append(" ".repeat(groupWidth)).append(" |").append('\n');
        appendHalf(result, cells, false, bottomHeight, cellWidth);
        result.append(border).append('\n');
        appendIndices(result, false, cellWidth, addressing, perspective);
        appendSpecialCells(result, state);
        return result.toString();
    }

    private static List<String> getCodes(List<? extends Checker<?, ?>> checkers) {
        return checkers.stream()
                .map(checker -> {
                    String code = checker.getCode();
                    Validate.notEmpty(code, "Checker code must not be empty");
                    Validate.isTrue(code.chars().noneMatch(Character::isWhitespace),
                            "Checker code must not contain whitespace: %s", code);
                    return code;
                })
                .toList();
    }

    private static int getCellIndex(boolean upper, int column) {
        return upper ? StandardBoard.PLAYING_CELL_COUNT - 1 - column : column;
    }

    private static void appendIndices(
            StringBuilder result,
            boolean upper,
            int cellWidth,
            BoardAddressing<StandardCheckerColor> addressing,
            StandardCheckerColor perspective) {
        String format = "%" + cellWidth + "d";
        result.append(INDENT).append("  ");

        for (int column = 0; column < HALF_CELL_COUNT; column++) {
            if (column > 0) {
                result.append(column == GROUP_CELL_COUNT ? "   " : CELL_SEPARATOR);
            }
            int index = getCellIndex(upper, column);
            result.append(String.format(Locale.ROOT, format, addressing.toRoutePoint(perspective, index)));
        }
        result.append('\n');
    }

    private static void appendHalf(
            StringBuilder result, List<List<String>> cells, boolean upper, int height, int cellWidth) {
        for (int row = 0; row < height; row++) {
            int checkerIndex = upper ? row : height - 1 - row;
            result.append(INDENT).append("| ");

            for (int column = 0; column < HALF_CELL_COUNT; column++) {
                if (column > 0) {
                    result.append(column == GROUP_CELL_COUNT ? " | " : CELL_SEPARATOR);
                }
                List<String> checkers = cells.get(getCellIndex(upper, column));
                String code = checkerIndex < checkers.size() ? checkers.get(checkerIndex) : EMPTY_SLOT;
                result.append(" ".repeat(cellWidth - code.length())).append(code);
            }
            result.append(" |").append('\n');
        }
    }

    private static void appendSpecialCells(StringBuilder result, BoardState<?> state) {
        List<String> whiteBar = getCodes(state.getBars().get(StandardCheckerColor.WHITE));
        List<String> blackBar = getCodes(state.getBars().get(StandardCheckerColor.BLACK));
        List<String> whiteBorneOff = getCodes(state.getBorneOff().get(StandardCheckerColor.WHITE));
        List<String> blackBorneOff = getCodes(state.getBorneOff().get(StandardCheckerColor.BLACK));
        int countWidth = Math.max(2, IntStream.of(
                        whiteBar.size(), blackBar.size(), whiteBorneOff.size(), blackBorneOff.size())
                .max().orElse(0));
        String format = "%s (%" + countWidth + "d): %s";
        String whiteBarText = formatCell("W", whiteBar, format);
        String blackBarText = formatCell("B", blackBar, format);
        String whiteOffText = formatCell("W", whiteBorneOff, format);
        String blackOffText = formatCell("B", blackBorneOff, format);
        int whiteWidth = Math.max(whiteBarText.length(), whiteOffText.length());

        result.append(INDENT).append("BAR       ").append(whiteBarText)
                .append(" ".repeat(whiteWidth - whiteBarText.length() + 5))
                .append(blackBarText).append('\n');
        result.append(INDENT).append("BORNE OFF ").append(whiteOffText)
                .append(" ".repeat(whiteWidth - whiteOffText.length() + 5))
                .append(blackOffText);
    }

    private static String formatCell(String color, List<String> checkers, String format) {
        return String.format(Locale.ROOT, format, color, checkers.size(),
                checkers.stream().collect(Collectors.joining(", ", "[", "]")));
    }
}
