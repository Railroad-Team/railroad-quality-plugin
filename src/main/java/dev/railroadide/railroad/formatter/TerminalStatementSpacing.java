package dev.railroadide.railroad.formatter;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds a blank line after abrupt completion statements when another statement or case follows.
 */
public final class TerminalStatementSpacing extends TreePathScanner<Void, Void> {
    private final CompilationUnitTree unit;
    private final SourcePositions positions;
    private final String source;
    private final String newline;
    private final List<RailroadJavaStyle.TextEdit> edits;
    private final List<Integer> changedLines;
    private final List<Range> disabledRanges = new ArrayList<>();

    public TerminalStatementSpacing(
        CompilationUnitTree unit,
        SourcePositions positions,
        String source,
        List<RailroadJavaStyle.TextEdit> edits,
        List<Integer> changedLines
    ) {
        this.unit = unit;
        this.positions = positions;
        this.source = source;
        this.newline = source.contains("\r\n") ? "\r\n" : "\n";
        this.edits = edits;
        this.changedLines = changedLines;
        findDisabledRanges();
    }

    @Override
    public Void scan(Tree tree, Void unused) {
        if (tree != null && switch (tree.getKind()) {
            case BREAK, CONTINUE, RETURN, THROW, YIELD -> true;
            default -> false;
        }) {
            inspect(tree, getCurrentPath());
        }

        return super.scan(tree, unused);
    }

    private void inspect(Tree tree, TreePath parent) {
        int start = Math.toIntExact(positions.getStartPosition(unit, tree));
        int end = Math.toIntExact(positions.getEndPosition(unit, tree));
        if (start < 0 || end <= start || source.charAt(end - 1) != ';'
            || disabledRanges.stream().anyMatch(range -> start >= range.start() && start < range.end()))
            return;

        int gapStart = afterTrailingComments(end);
        int next = gapStart;
        while (next < source.length() && Character.isWhitespace(source.charAt(next))) {
            next++;
        }

        if (next == source.length() || source.charAt(next) == '}' || keywordAt(next, "else"))
            return;

        if (keywordAt(next, "while") && isDoWhileContinuation(parent, end))
            return;

        String gap = source.substring(gapStart, next);
        long lineBreaks = gap.chars().filter(character -> character == '\n' || character == '\r').count();
        if (lineBreaks >= newline.length() * 2L)
            return;

        int lastNewline = Math.max(gap.lastIndexOf('\n'), gap.lastIndexOf('\r'));
        String indentation = lastNewline < 0 ? "" : gap.substring(lastNewline + 1);
        edits.add(new RailroadJavaStyle.TextEdit(gapStart, next, newline + newline + indentation));
        changedLines.add(Math.toIntExact(unit.getLineMap().getLineNumber(start)));
    }

    private int afterTrailingComments(int offset) {
        while (offset < source.length()) {
            int next = offset;
            while (next < source.length() && (source.charAt(next) == ' ' || source.charAt(next) == '\t')) {
                next++;
            }

            if (source.startsWith("//", next)) {
                int end = source.indexOf('\n', next + 2);
                return end < 0 ? source.length() : end > 0 && source.charAt(end - 1) == '\r' ? end - 1 : end;
            }

            if (!source.startsWith("/*", next))
                return offset;

            int end = source.indexOf("*/", next + 2);
            if (end < 0)
                return source.length();

            offset = end + 2;
        }

        return offset;
    }

    private boolean keywordAt(int offset, String word) {
        int end = offset + word.length();
        return source.startsWith(word, offset)
            && (end == source.length() || !Character.isJavaIdentifierPart(source.charAt(end)));
    }

    private boolean isDoWhileContinuation(TreePath path, int statementEnd) {
        for (; path != null; path = path.getParentPath()) {
            if (path.getLeaf() instanceof DoWhileLoopTree loop
                && positions.getEndPosition(unit, loop.getStatement()) == statementEnd)
                return true;
        }

        return false;
    }

    private void findDisabledRanges() {
        int disabledStart = -1;
        for (int offset = 0; offset < source.length();) {
            int start = offset;
            char current = source.charAt(offset);
            if (current == '"' || current == '\'') {
                offset = ClosingBraceSpacing.endOfLiteral(source, offset);
                continue;
            }

            if (source.startsWith("//", offset)) {
                int end = source.indexOf('\n', offset + 2);
                offset = end < 0 ? source.length() : end;
            } else if (source.startsWith("/*", offset)) {
                int end = source.indexOf("*/", offset + 2);
                offset = end < 0 ? source.length() : end + 2;
            } else {
                offset++;
                continue;
            }

            String comment = source.substring(start, offset);
            if (comment.contains("@formatter:off") && disabledStart < 0) {
                disabledStart = start;
            }

            if (comment.contains("@formatter:on") && disabledStart >= 0) {
                disabledRanges.add(new Range(disabledStart, offset));
                disabledStart = -1;
            }
        }

        if (disabledStart >= 0) {
            disabledRanges.add(new Range(disabledStart, source.length()));
        }
    }

    private record Range(int start, int end) {
    }
}
