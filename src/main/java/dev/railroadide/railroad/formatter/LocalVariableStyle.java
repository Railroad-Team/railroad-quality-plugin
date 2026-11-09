package dev.railroadide.railroad.formatter;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows var only for constructions and type-qualified members, excluding collection types.
 */
public final class LocalVariableStyle extends TreePathScanner<Void, Void> {
    private static final List<String> COLLECTION_TYPES = List.of(
        "java.lang.Iterable", "java.util.Map", "java.util.Iterator", "java.util.Enumeration",
        "java.util.Dictionary");

    private final CompilationUnitTree unit;
    private final Trees trees;
    private final String source;
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<Violation> violations = new ArrayList<>();
    private boolean expandedTypes;

    public LocalVariableStyle(CompilationUnitTree unit, Trees trees, String source) {
        this.unit = unit;
        this.trees = trees;
        this.source = source;
        int release = Integer.parseInt(System.getProperty("railroad.quality.release",
            Integer.toString(Runtime.version().feature())));
        if (release >= 10) {
            scan(unit, null);
        }
    }

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    public List<Violation> violations() {
        return List.copyOf(violations);
    }

    public boolean expandedTypes() {
        return expandedTypes;
    }

    @Override
    public Void visitVariable(VariableTree variable, Void unused) {
        // Capture source ranges before attribution replaces inferred types in javac's trees.
        int start = startOf(variable.getType());
        int end = endOf(variable.getType());
        boolean inferred = variable.getType() == null || variable.getType().toString().equals("var");
        if (inferred) {
            start = skipTrivia(Math.max(startOf(variable), endOf(variable.getModifiers())));
            end = start + 3;
            int nameStart = skipTrivia(end);
            String name = variable.getName().toString();
            if (!source.startsWith("var", start) || !source.startsWith(name, nameStart))
                return super.visitVariable(variable, unused);
        } else if (!isExactConstructionCandidate(variable))
            return super.visitVariable(variable, unused);

        if (start >= 0 && end > start) {
            String originalType = source.substring(start, end);
            if (inferred || (!originalType.contains("/*") && !originalType.contains("//")
                && !originalType.contains("\\") && !variable.getType().toString().contains("@"))) {
                candidates.add(new Candidate(getCurrentPath(), start, end, inferred));
            }
        }

        return super.visitVariable(variable, unused);
    }

    public void addEdits(
        Elements elements,
        Types types,
        CollectionTypeStyle collections,
        List<RailroadJavaStyle.TextEdit> edits,
        List<Integer> changedLines
    ) {
        for (Candidate candidate : candidates) {
            VariableTree variable = (VariableTree) candidate.path().getLeaf();
            TypeMirror type = trees.getTypeMirror(candidate.path());
            boolean resolved = type != null && type.getKind() != TypeKind.ERROR;
            boolean collection = resolved && isCollection(type, elements, types);
            if (!candidate.inferred()) {
                NewClassTree construction = (NewClassTree) variable.getInitializer();
                TypeMirror constructedType = trees.getTypeMirror(new TreePath(candidate.path(), construction));
                if (resolved && !collection && constructedType != null
                    && constructedType.getKind() != TypeKind.ERROR && types.isSameType(type, constructedType)) {
                    addEdit(candidate, "var", edits, changedLines);
                }

                continue;
            }

            if (resolved && !collection && allowsVar(candidate.path(), variable.getInitializer()))
                continue;

            TypeMirror base = collections.replacementType(variable);
            String explicitType = resolved ? renderType(base == null ? type : base, candidate.path()) : null;
            if (explicitType == null) {
                violations.add(new Violation(lineOf(candidate.start()), "Cannot replace disallowed var for '"
                    + variable.getName() + "' with a safe explicit type; resolve its dependencies or declare "
                    + "a suitable type manually"));
                continue;
            }

            addEdit(candidate, explicitType, edits, changedLines);
            expandedTypes = true;
        }
    }

    private boolean isExactConstructionCandidate(VariableTree variable) {
        Tree parent = getCurrentPath().getParentPath().getLeaf();
        if (!(parent instanceof BlockTree || parent instanceof ForLoopTree)
            || !variable.getModifiers().getAnnotations().isEmpty()
            || !(variable.getInitializer() instanceof NewClassTree construction)
            || construction.getClassBody() != null || construction.getEnclosingExpression() != null
            || !construction.getTypeArguments().isEmpty()
            || construction.getIdentifier().toString().contains("<>")
            || variable.getType().getKind() == Tree.Kind.ANNOTATED_TYPE)
            return false;

        if (parent instanceof ForLoopTree loop)
            return loop.getInitializer().size() == 1;

        long typeStart = startOf(variable.getType());
        return ((BlockTree) parent).getStatements().stream()
            .filter(VariableTree.class::isInstance).map(VariableTree.class::cast)
            .filter(other -> startOf(other.getType()) == typeStart).count() == 1;
    }

    private boolean allowsVar(TreePath variablePath, ExpressionTree initializer) {
        if (initializer == null)
            return false;

        TreePath path = new TreePath(variablePath, initializer);
        while (initializer instanceof ParenthesizedTree parentheses) {
            initializer = parentheses.getExpression();
            path = new TreePath(path, initializer);
        }

        if (initializer instanceof NewClassTree)
            return true;

        if (initializer instanceof MethodInvocationTree invocation) {
            initializer = invocation.getMethodSelect();
            path = new TreePath(path, initializer);
        }

        return initializer instanceof MemberSelectTree member
            && trees.getElement(new TreePath(path, member.getExpression())) instanceof TypeElement;
    }

    private static boolean isCollection(TypeMirror type, Elements elements, Types types) {
        for (String name : COLLECTION_TYPES) {
            TypeElement collection = elements.getTypeElement(name);
            if (collection != null && types.isSubtype(types.erasure(type), types.erasure(collection.asType())))
                return true;
        }

        return false;
    }

    // Use qualified names first; the import rule shortens these in a subsequent pass.
    // Some inferred types (anonymous classes, captures, intersections) cannot be written in a declaration.

    public String renderType(TypeMirror type, TreePath path) {
        if (type.getKind().isPrimitive())
            return type.toString();

        if (type instanceof ArrayType array) {
            String component = renderType(array.getComponentType(), path);
            return component == null ? null : component + "[]";
        }

        if (type instanceof DeclaredType declared && declared.asElement() instanceof TypeElement element) {
            String name = element.getQualifiedName().toString();
            if (type.getKind() == TypeKind.ERROR || name.isEmpty()
                || !trees.isAccessible(trees.getScope(path), element))
                return null;

            if (declared.getEnclosingType() instanceof DeclaredType enclosing) {
                String owner = renderType(enclosing, path);
                if (owner == null)
                    return null;

                name = owner + "." + element.getSimpleName();
            }

            List<String> arguments = new ArrayList<>();
            for (TypeMirror argument : declared.getTypeArguments()) {
                String rendered = renderType(argument, path);
                if (rendered == null)
                    return null;

                arguments.add(rendered);
            }

            return name + (arguments.isEmpty() ? "" : "<" + String.join(", ", arguments) + ">");
        }

        if (type instanceof TypeVariable variable) {
            Element element = variable.asElement();
            String name = element.getSimpleName().toString();
            return type.toString().equals(name) ? name : null;
        }

        if (type instanceof WildcardType wildcard) {
            TypeMirror bound = wildcard.getExtendsBound();
            String prefix = "? extends ";
            if (bound == null) {
                bound = wildcard.getSuperBound();
                prefix = "? super ";
            }

            if (bound == null)
                return "?";

            String rendered = renderType(bound, path);
            return rendered == null ? null : prefix + rendered;
        }

        return null;
    }

    private void addEdit(
        Candidate candidate,
        String replacement,
        List<RailroadJavaStyle.TextEdit> edits,
        List<Integer> changedLines
    ) {
        edits.add(new RailroadJavaStyle.TextEdit(candidate.start(), candidate.end(), replacement));
        changedLines.add(lineOf(candidate.start()));
    }

    private int lineOf(int offset) {
        return Math.toIntExact(unit.getLineMap().getLineNumber(offset));
    }

    private int startOf(Tree tree) {
        return tree == null ? -1 : Math.toIntExact(trees.getSourcePositions().getStartPosition(unit, tree));
    }

    private int endOf(Tree tree) {
        return tree == null ? -1 : Math.toIntExact(trees.getSourcePositions().getEndPosition(unit, tree));
    }

    private int skipTrivia(int offset) {
        while (offset >= 0 && offset < source.length()) {
            if (Character.isWhitespace(source.charAt(offset))) {
                offset++;
            } else if (source.startsWith("/*", offset)) {
                int end = source.indexOf("*/", offset + 2);
                if (end < 0)
                    break;

                offset = end + 2;
            } else if (source.startsWith("//", offset)) {
                int end = source.indexOf('\n', offset + 2);
                if (end < 0)
                    break;

                offset = end + 1;
            } else
                break;

        }

        return offset;
    }

    private record Candidate(TreePath path, int start, int end, boolean inferred) {
    }

    public record Violation(int line, String message) {
    }
}
