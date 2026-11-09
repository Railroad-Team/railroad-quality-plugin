package dev.railroadide.railroad.formatter;

import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.YieldTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.sun.source.tree.ExpressionTree;

/**
 * Uses collection interfaces for local variables and private fields when their uses permit widening.
 */
public final class CollectionTypeStyle extends TreePathScanner<Void, Void> {
    private static final List<String> INTERFACES = List.of(
        "java.util.concurrent.ConcurrentNavigableMap", "java.util.concurrent.ConcurrentMap",
        "java.util.concurrent.BlockingDeque", "java.util.concurrent.TransferQueue",
        "java.util.concurrent.BlockingQueue",
        "java.util.NavigableMap", "java.util.SortedMap", "java.util.NavigableSet", "java.util.SortedSet",
        "java.util.List", "java.util.Deque", "java.util.Queue", "java.util.Set", "java.util.Map",
        "java.util.SequencedMap", "java.util.SequencedSet", "java.util.Collection",
        "java.util.ListIterator", "java.util.Iterator", "java.util.Enumeration", "java.lang.Iterable");

    private final CompilationUnitTree unit;
    private final Trees trees;
    private final SourcePositions positions;
    private final String source;
    private final List<Candidate> candidates = new ArrayList<>();
    private final Map<VariableTree, DeclaredType> replacements = new HashMap<>();
    private boolean changedTypes;

    public CollectionTypeStyle(CompilationUnitTree unit, Trees trees, String source) {
        this.unit = unit;
        this.trees = trees;
        this.positions = trees.getSourcePositions();
        this.source = source;
        scan(unit, null);
    }

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    public boolean changedTypes() {
        return changedTypes;
    }

    public DeclaredType replacementType(VariableTree variable) {
        return replacements.get(variable);
    }

    @Override
    public Void visitVariable(VariableTree variable, Void unused) {
        Tree parent = getCurrentPath().getParentPath().getLeaf();
        List<? extends Tree> siblings;
        if (parent instanceof BlockTree block) {
            siblings = block.getStatements();
        } else if (parent instanceof ForLoopTree loop) {
            siblings = loop.getInitializer();
        } else if (parent instanceof CaseTree caseTree && caseTree.getStatements() != null) {
            siblings = caseTree.getStatements();
        } else if (parent instanceof ClassTree owner && variable.getModifiers().getFlags().contains(Modifier.PRIVATE)
            && (owner.getKind() != Tree.Kind.RECORD || variable.getModifiers().getFlags().contains(Modifier.STATIC))) {
            siblings = owner.getMembers();
        } else if (parent instanceof EnhancedForLoopTree) {
            siblings = List.of(variable);
        } else
            return super.visitVariable(variable, unused);

        int start = variable.getType() == null
            ? -1
            : Math.toIntExact(positions.getStartPosition(unit, variable.getType()));
        int end = variable.getType() == null ? -1 : Math.toIntExact(positions.getEndPosition(unit, variable.getType()));
        int release = Integer
            .parseInt(System.getProperty("railroad.quality.release", Integer.toString(Runtime.version().feature())));
        boolean inferred = variable.getType() == null || (release >= 10 && variable.getType().toString().equals("var"));
        int directivePosition = inferred ? Math.toIntExact(positions.getStartPosition(unit, variable)) : start;
        if (!SourceFormatting.isEnabled(source, directivePosition))
            return super.visitVariable(variable, unused);

        if (!inferred) {
            if (start < 0 || end <= start || variable.getType().getKind() == Tree.Kind.PRIMITIVE_TYPE
                || variable.getType().getKind() == Tree.Kind.ARRAY_TYPE)
                return super.visitVariable(variable, unused);

            String original = source.substring(start, end);
            if (original.contains("/*") || original.contains("//") || original.contains("@") || original.contains("\\"))
                return super.visitVariable(variable, unused);

            if (siblings.stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast)
                .filter(other -> positions.getStartPosition(unit, other.getType()) == start).count() != 1)
                return super.visitVariable(variable, unused);
        }

        candidates.add(new Candidate(getCurrentPath(), start, end, inferred));
        return super.visitVariable(variable, unused);
    }

    public static Map<Element, List<TreePath>> references(List<CompilationUnitTree> units, Trees trees) {
        Map<Element, List<TreePath>> result = new HashMap<>();
        TreePathScanner<Void, Void> scanner = new TreePathScanner<>() {
            private void record() {
                Element element = trees.getElement(getCurrentPath());
                if (element instanceof VariableElement) {
                    result.computeIfAbsent(element, unused -> new ArrayList<>()).add(getCurrentPath());
                }
            }

            @Override
            public Void visitIdentifier(IdentifierTree tree, Void unused) {
                record();
                return super.visitIdentifier(tree, unused);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
                record();
                return super.visitMemberSelect(tree, unused);
            }
        };

        units.forEach(unit -> scanner.scan(unit, null));
        return result;
    }

    public static void plan(
        List<CollectionTypeStyle> styles,
        Elements elements,
        Types types,
        Map<Element, List<TreePath>> references
    ) {
        Map<Element, Plan> plans = new LinkedHashMap<>();
        Map<Element, DeclaredType> proposed = new HashMap<>();
        Map<Element, Integer> indices = new HashMap<>();
        for (CollectionTypeStyle style : styles) {
            for (Candidate candidate : style.candidates) {
                VariableTree variable = (VariableTree) candidate.path().getLeaf();
                Element symbol = style.trees.getElement(candidate.path());
                TypeMirror type = style.trees.getTypeMirror(candidate.path());
                if (!(type instanceof DeclaredType concrete) || type.getKind() != TypeKind.DECLARED
                    || !(concrete.asElement() instanceof TypeElement owner) || owner.getKind().isInterface()
                    || style.hasContextualFactory(variable.getInitializer(), candidate.path()))
                    continue;

                Map<String, DeclaredType> supertypes = supertypes(concrete, types);
                List<DeclaredType> choices = new ArrayList<>();
                for (String name : INTERFACES) {
                    DeclaredType base = supertypes.get(name);
                    if (base != null && preservesTypeParameters(owner, name, types)) {
                        choices.add(base);
                    }
                }

                if (!choices.isEmpty()) {
                    plans.put(symbol, new Plan(style, candidate, concrete, choices));
                    proposed.put(symbol, choices.getFirst());
                    indices.put(symbol, 0);
                }
            }
        }

        // Plan related declarations together, so aliases widen in the same formatting run.

        boolean changed;
        do {
            changed = false;
            for (Map.Entry<Element, Plan> entry : plans.entrySet()) {
                Element symbol = entry.getKey();
                Plan plan = entry.getValue();
                if (!proposed.containsKey(symbol))
                    continue;

                int index = indices.get(symbol);
                while (proposed.containsKey(symbol)) {
                    DeclaredType base = proposed.get(symbol);
                    boolean safe = references.getOrDefault(symbol, List.of()).stream()
                        .allMatch(
                            path -> plan.style().safeReference(path, plan.concrete(), base, elements, types, proposed));
                    if (safe)
                        break;

                    index++;
                    if (index < plan.choices().size()) {
                        proposed.put(symbol, plan.choices().get(index));
                    } else {
                        proposed.remove(symbol);
                    }

                    changed = true;
                }

                indices.put(symbol, index);
            }
        } while (changed);

        proposed.forEach((symbol, base) -> {
            Plan plan = plans.get(symbol);
            plan.style().replacements.put((VariableTree) plan.candidate().path().getLeaf(), base);
        });
    }

    public void addEdits(LocalVariableStyle renderer, List<RailroadJavaStyle.TextEdit> edits, List<Integer> lines) {
        for (Candidate candidate : candidates) {
            if (candidate.inferred())
                continue;

            DeclaredType base = replacements.get((VariableTree) candidate.path().getLeaf());
            String replacement = base == null ? null : renderer.renderType(base, candidate.path());
            if (replacement == null)
                continue;

            edits.add(new RailroadJavaStyle.TextEdit(candidate.start(), candidate.end(), replacement));
            lines.add(Math.toIntExact(unit.getLineMap().getLineNumber(candidate.start())));
            changedTypes = true;
        }
    }

    private static Map<String, DeclaredType> supertypes(DeclaredType type, Types types) {
        Map<String, DeclaredType> result = new LinkedHashMap<>();
        List<DeclaredType> pending = new ArrayList<>();
        pending.add(type);
        Set<String> visited = new HashSet<>();
        for (int index = 0; index < pending.size(); index++) {
            DeclaredType current = pending.get(index);
            if (!visited.add(current.toString()))
                continue;

            TypeElement element = (TypeElement) current.asElement();
            result.putIfAbsent(element.getQualifiedName().toString(), current);
            for (TypeMirror parent : types.directSupertypes(current)) {
                if (parent.getKind() == TypeKind.DECLARED && parent instanceof DeclaredType declared) {
                    pending.add(declared);
                }
            }
        }

        return result;
    }

    private static boolean preservesTypeParameters(TypeElement concrete, String name, Types types) {
        if (concrete.getTypeParameters().isEmpty())
            return true;

        DeclaredType base = supertypes((DeclaredType) concrete.asType(), types).get(name);
        return base != null && concrete.getTypeParameters().stream().allMatch(parameter -> contains(base, parameter));
    }

    private static boolean contains(TypeMirror type, TypeParameterElement parameter) {
        if (type instanceof TypeVariable variable)
            return variable.asElement().equals(parameter);

        if (type instanceof DeclaredType declared)
            return declared.getTypeArguments().stream().anyMatch(argument -> contains(argument, parameter));

        if (type instanceof ArrayType array)
            return contains(array.getComponentType(), parameter);

        if (type instanceof WildcardType wildcard)
            return (wildcard.getExtendsBound() != null && contains(wildcard.getExtendsBound(), parameter))
                || (wildcard.getSuperBound() != null && contains(wildcard.getSuperBound(), parameter));

        return false;
    }

    private boolean hasContextualFactory(Tree expression, TreePath parent) {
        if (expression == null)
            return false;

        TreePath path = new TreePath(parent, expression);
        if (expression instanceof ParenthesizedTree parentheses)
            return hasContextualFactory(parentheses.getExpression(), path);

        if (expression instanceof ConditionalExpressionTree conditional)
            return hasContextualFactory(conditional.getTrueExpression(), path)
                || hasContextualFactory(conditional.getFalseExpression(), path);

        if (expression instanceof SwitchExpressionTree switchExpression) {
            boolean[] unsafe = {false};

            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitYield(YieldTree tree, Void unused) {
                    unsafe[0] |= hasContextualFactory(tree.getValue(), getCurrentPath());
                    return super.visitYield(tree, unused);
                }
            }.scan(path, null);
            for (CaseTree caseTree : switchExpression.getCases()) {
                if (caseTree.getBody() instanceof ExpressionTree body) {
                    unsafe[0] |= hasContextualFactory(body, new TreePath(path, caseTree));
                }
            }

            return unsafe[0];
        }

        if (!(expression instanceof MethodInvocationTree))
            return false;

        Element element = trees.getElement(path);
        return !(element instanceof ExecutableElement method)
            || (!method.getTypeParameters().isEmpty() && method.getReturnType().getKind() == TypeKind.TYPEVAR);
    }

    private boolean safeReference(
        TreePath path,
        DeclaredType concrete,
        DeclaredType base,
        Elements elements,
        Types types,
        Map<Element, DeclaredType> proposed
    ) {
        Tree leaf = path.getLeaf();
        TreePath parentPath = path.getParentPath();
        if (parentPath == null)
            return false;

        Tree parent = parentPath.getLeaf();
        if (parent instanceof ParenthesizedTree)
            return safeReference(parentPath, concrete, base, elements, types, proposed);

        if (parent instanceof MemberSelectTree member && member.getExpression() == leaf)
            return supportedMethod(trees.getElement(parentPath), concrete, base, elements, types);

        if (parent instanceof MemberReferenceTree reference && reference.getQualifierExpression() == leaf)
            return supportedMethod(trees.getElement(parentPath), concrete, base, elements, types);

        if (parent instanceof TypeCastTree || parent instanceof InstanceOfTree || parent instanceof SynchronizedTree)
            return true;

        if (parent instanceof BinaryTree binary)
            return binary.getKind() == Tree.Kind.EQUAL_TO || binary.getKind() == Tree.Kind.NOT_EQUAL_TO
                || binary.getKind() == Tree.Kind.PLUS;

        if (parent instanceof AssertTree assertion && assertion.getDetail() == leaf)
            return true;

        if (parent instanceof EnhancedForLoopTree loop && loop.getExpression() == leaf)
            return true;

        if (parent instanceof AssignmentTree assignment) {
            if (assignment.getVariable() == leaf)
                return !hasContextualFactory(assignment.getExpression(), parentPath);

            return assignable(base, expectedType(new TreePath(parentPath, assignment.getVariable()), proposed), types);
        }

        if (parent instanceof VariableTree variable) {
            TypeMirror target = expectedType(parentPath, proposed);
            if (!proposed.containsKey(trees.getElement(parentPath))
                && (variable.getType() == null || variable.getType().toString().equals("var")))
                return false;

            return assignable(base, target, types);
        }

        if (parent instanceof ConditionalExpressionTree)
            return assignable(base, trees.getTypeMirror(parentPath), types);

        if (parent instanceof YieldTree) {
            for (TreePath scope = parentPath; scope != null; scope = scope.getParentPath()) {
                if (scope.getLeaf() instanceof SwitchExpressionTree)
                    return assignable(base, trees.getTypeMirror(scope), types);
            }

            return false;
        }

        if (parent instanceof ReturnTree) {
            for (TreePath scope = parentPath; scope != null; scope = scope.getParentPath()) {
                if (scope.getLeaf() instanceof LambdaExpressionTree)
                    return false;

                if (scope.getLeaf() instanceof MethodTree
                    && trees.getElement(scope) instanceof ExecutableElement method)
                    return assignable(base, method.getReturnType(), types);
            }

            return false;
        }

        if (parent instanceof MethodInvocationTree invocation) {
            TypeMirror signature = trees.getTypeMirror(new TreePath(parentPath, invocation.getMethodSelect()));
            return safeArgument(leaf, invocation.getArguments(), trees.getElement(parentPath), signature, base, types);
        }

        if (parent instanceof NewClassTree construction) {
            Element method = trees.getElement(parentPath);
            TypeMirror constructed = trees.getTypeMirror(parentPath);
            if (!(method instanceof ExecutableElement constructor) || !(constructed instanceof DeclaredType declared)
                || constructed.getKind() != TypeKind.DECLARED)
                return false;

            DeclaredType owner = supertypes(declared, types).values().stream()
                .filter(type -> type.asElement().equals(constructor.getEnclosingElement())).findFirst().orElse(null);
            if (owner == null)
                return false;

            return safeArgument(leaf, construction.getArguments(), constructor,
                types.asMemberOf(owner, constructor), base, types);
        }

        if (parent instanceof NewArrayTree && trees.getTypeMirror(parentPath) instanceof ArrayType array)
            return assignable(base, array.getComponentType(), types);

        return false;
    }

    private TypeMirror expectedType(TreePath path, Map<Element, DeclaredType> proposed) {
        TypeMirror replacement = proposed.get(trees.getElement(path));
        return replacement == null ? trees.getTypeMirror(path) : replacement;
    }

    private static boolean assignable(DeclaredType base, TypeMirror target, Types types) {
        return target != null && target.getKind() != TypeKind.ERROR && types.isAssignable(base, target);
    }

    private static boolean safeArgument(
        Tree argument,
        List<? extends Tree> arguments,
        Element selected,
        TypeMirror signature,
        DeclaredType base,
        Types types
    ) {
        if (!(selected instanceof ExecutableElement method) || !(signature instanceof ExecutableType executable))
            return false;

        int index = arguments.indexOf(argument);
        List<? extends TypeMirror> parameters = executable.getParameterTypes();
        if (index < 0 || parameters.isEmpty() || (!method.isVarArgs() && index >= parameters.size()))
            return false;

        TypeMirror target = parameters.get(Math.min(index, parameters.size() - 1));
        if (method.isVarArgs() && index >= parameters.size() - 1 && target instanceof ArrayType array) {
            target = array.getComponentType();
        }

        return assignable(base, target, types);
    }

    private static boolean supportedMethod(
        Element selected,
        DeclaredType concrete,
        DeclaredType base,
        Elements elements,
        Types types
    ) {
        if (!(selected instanceof ExecutableElement method) || method.getModifiers().contains(Modifier.STATIC))
            return false;

        if (method.getEnclosingElement() instanceof TypeElement owner
            && owner.getQualifiedName().contentEquals("java.lang.Object"))
            return method.getModifiers().contains(Modifier.PUBLIC) && !method.getSimpleName().contentEquals("getClass");

        ExecutableType original = (ExecutableType) types.asMemberOf(concrete, method);
        for (Element member : elements.getAllMembers((TypeElement) base.asElement())) {
            if (!(member instanceof ExecutableElement alternative)
                || alternative.getModifiers().contains(Modifier.STATIC)
                || !alternative.getSimpleName().contentEquals(method.getSimpleName())
                || alternative.isVarArgs() != method.isVarArgs())
                continue;

            if (!alternative.equals(method)
                && !elements.overrides(method, alternative, (TypeElement) concrete.asElement()))
                continue;

            ExecutableType projected = (ExecutableType) types.asMemberOf(base, alternative);
            if (types.isSubsignature(original, projected)
                && (types.isSameType(original.getReturnType(), projected.getReturnType())
                    || original.getReturnType().toString().equals(projected.getReturnType().toString()))
                && preservesCheckedExceptions(original, projected, elements, types))
                return true;
        }

        return false;
    }

    private static boolean preservesCheckedExceptions(
        ExecutableType original,
        ExecutableType projected,
        Elements elements,
        Types types
    ) {
        TypeMirror runtimeException = elements.getTypeElement("java.lang.RuntimeException").asType();
        TypeMirror error = elements.getTypeElement("java.lang.Error").asType();
        return projected.getThrownTypes().stream().allMatch(thrown -> types.isSubtype(thrown, runtimeException)
            || types.isSubtype(thrown, error)
            || original.getThrownTypes().stream().anyMatch(existing -> types.isSubtype(thrown, existing)));
    }

    private record Candidate(TreePath path, int start, int end, boolean inferred) {
    }

    private record Plan(
        CollectionTypeStyle style,
        Candidate candidate,
        DeclaredType concrete,
        List<DeclaredType> choices
    ) {
    }
}
