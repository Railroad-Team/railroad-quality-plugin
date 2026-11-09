package dev.railroadide.railroad.formatter;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replaces constructor type arguments only when javac's diamond analysis confirms identical inferred arguments.
 */
public final class DiamondStyle extends TreePathScanner<Void, Void> {
    private final CompilationUnitTree unit;
    private final SourcePositions positions;
    private final String source;
    private final List<Candidate> candidates = new ArrayList<>();

    public DiamondStyle(CompilationUnitTree unit, SourcePositions positions, String source) {
        this.unit = unit;
        this.positions = positions;
        this.source = source;
        scan(unit, null);
    }

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    @Override
    public Void visitNewClass(NewClassTree tree, Void unused) {
        if (tree.getIdentifier() instanceof ParameterizedTypeTree type && !type.getTypeArguments().isEmpty()
            && tree.getTypeArguments().isEmpty() && !hasInferredContext(getCurrentPath())) {
            int rawEnd = Math.toIntExact(positions.getEndPosition(unit, type.getType()));
            int end = Math.toIntExact(positions.getEndPosition(unit, type));
            int start = rawEnd < 0 ? -1 : source.indexOf('<', rawEnd);
            if (start >= rawEnd && start >= 0 && end > start && source.charAt(end - 1) == '>') {
                String arguments = source.substring(start, end);
                if (!arguments.contains("/*") && !arguments.contains("//") && !arguments.contains("@")
                    && !arguments.contains("\\") && SourceFormatting.isEnabled(source, start)) {
                    candidates.add(new Candidate(start, end));
                }
            }
        }

        return super.visitNewClass(tree, unused);
    }

    public void addEdits(
        List<Diagnostic<? extends JavaFileObject>> diagnostics,
        List<RailroadJavaStyle.TextEdit> edits,
        List<Integer> changedLines
    ) {
        Set<Long> redundantArguments = new HashSet<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            if (diagnostic.getCode().equals("compiler.warn.diamond.redundant.args")
                && diagnostic.getSource() != null
                && diagnostic.getSource().toUri().equals(unit.getSourceFile().toUri())) {
                redundantArguments.add(diagnostic.getPosition());
            }
        }

        for (Candidate candidate : candidates) {
            if (redundantArguments.contains((long) candidate.start())) {
                edits.add(new RailroadJavaStyle.TextEdit(candidate.start(), candidate.end(), "<>"));
                changedLines.add(Math.toIntExact(unit.getLineMap().getLineNumber(candidate.start())));
            }
        }
    }

    private static boolean hasInferredContext(TreePath path) {
        for (; path != null; path = path.getParentPath()) {
            if (path.getLeaf() instanceof VariableTree variable
                && (variable.getType() == null || variable.getType().toString().equals("var")))
                return true;
        }

        return false;
    }

    private record Candidate(int start, int end) {
    }
}
