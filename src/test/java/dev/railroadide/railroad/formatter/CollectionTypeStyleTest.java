package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CollectionTypeStyleTest {
    @Test
    public void usesListSetMapAndSpecializedCollectionInterfaces(@TempDir Path directory) throws Exception {
        String after = rewrite("""
            import java.util.*;
            import java.util.concurrent.*;

            public class Example {
                public void run() {
                    ArrayList<String> list = new ArrayList<>();
                    HashSet<String> set = new HashSet<>();
                    set.addAll(Set.of("one"));
                    set.containsAll(Set.of("one"));
                    HashMap<String, Integer> map = new HashMap<>();
                    TreeSet<String> sortedSet = new TreeSet<>();
                    TreeMap<String, Integer> sortedMap = new TreeMap<>();
                    ArrayDeque<String> deque = new ArrayDeque<>();
                    PriorityQueue<String> queue = new PriorityQueue<>();
                    ConcurrentHashMap<String, Integer> concurrent = new ConcurrentHashMap<>();
                    LinkedBlockingQueue<String> blocking = new LinkedBlockingQueue<>();
                }
            }
            """, directory);
        assertContains(after, "List<String> list = new ArrayList<>();", "Set<String> set = new HashSet<>();",
            "Map<String, Integer> map = new HashMap<>();", "NavigableSet<String> sortedSet = new TreeSet<>();",
            "NavigableMap<String, Integer> sortedMap = new TreeMap<>();", "Deque<String> deque = new ArrayDeque<>();",
            "Queue<String> queue = new PriorityQueue<>();",
            "ConcurrentMap<String, Integer> concurrent = new ConcurrentHashMap<>();",
            "BlockingQueue<String> blocking = new LinkedBlockingQueue<>();");
    }

    @Test
    public void keepsConcreteCapabilitiesAndOverloadSelections(@TempDir Path directory) throws Exception {
        String after = rewrite("""
            import java.util.*;

            public class Example {
                public void run() {
                    ArrayList<String> capacity = new ArrayList<>();
                    capacity.ensureCapacity(10);
                    ArrayList<String> normal = new ArrayList<>();
                    normal.add("one");
                    normal.sort(Comparator.naturalOrder());
                    ArrayList<String> overloaded = new ArrayList<>();
                    use(overloaded);
                    LinkedList<String> stack = new LinkedList<>();
                    stack.push("one");
                    PriorityQueue<String> comparator = new PriorityQueue<>();
                    comparator.comparator();
                    ArrayList<String> inspected = new ArrayList<>();
                    Class<?> type = inspected.getClass();
                    ArrayList<String> constructor = new ArrayList<>();
                    new Holder(constructor);
                    ArrayList<String> anonymousArgument = new ArrayList<>();
                    new ListHolder(anonymousArgument) {};
                }
                private void use(ArrayList<String> values) {}
                private void use(List<String> values) {}
                private static class Holder {
                    private Holder(ArrayList<String> values) {}
                }
                private static class ListHolder {
                    private ListHolder(List<String> values) {}
                }
            }
            """, directory);
        assertContains(after, "ArrayList<String> capacity", "List<String> normal", "ArrayList<String> overloaded",
            "Deque<String> stack", "PriorityQueue<String> comparator", "ArrayList<String> inspected",
            "ArrayList<String> constructor", "List<String> anonymousArgument");
    }

    @Test
    public void widensAliasesAndPrivateFieldsTogetherWithoutChangingPublicApi(@TempDir Path directory)
        throws Exception {
        String after = rewrite("""
            import java.util.*;

            public class Example {
                private ArrayList<String> field = new ArrayList<>();
                private ArrayList<String> alias = field;
                public ArrayList<String> apiField = new ArrayList<>();
                public record Data(ArrayList<String> values) {}
                public ArrayList<String> api(ArrayList<String> input) { return input; }
                public void run() {
                    var first = new ArrayList<String>();
                    var second = first;
                    ArrayList<String> third = second;
                    field = third;
                    third = field;
                    ArrayList<String> left = new ArrayList<>(), right = new ArrayList<>();
                }
            }
            """, directory);
        assertContains(after, "private List<String> field", "private List<String> alias",
            "public ArrayList<String> apiField", "record Data(ArrayList<String> values)",
            "public ArrayList<String> api(ArrayList<String> input)", "List<String> first = new ArrayList<>();",
            "List<String> second = first;", "List<String> third = second;",
            "ArrayList<String> left = new ArrayList<>(), right = new ArrayList<>();");
    }

    @Test
    public void retainsConcreteTypesThroughoutAliasesThatNeedConcreteMethods(@TempDir Path directory) throws Exception {
        String after = rewrite("""
            import java.util.*;

            public class Example {
                public void run() {
                    var first = new ArrayList<String>();
                    var second = first;
                    second.ensureCapacity(10);
                }
            }
            """, directory);
        assertContains(after, "ArrayList<String> first = new ArrayList<>();", "ArrayList<String> second = first;");
    }

    @Test
    public void projectsSubclassTypeArgumentsWithoutLosingUnconstrainedParameters(@TempDir Path directory)
        throws Exception {
        String after = rewrite("""
            import java.util.*;

            public class Example {
                private static class Names extends ArrayList<String> {}
                private static class Swap<A, B> extends HashMap<B, A> {}
                private static class Phantom<A, B> extends ArrayList<A> {}
                public void run() {
                    Names names = new Names();
                    names.add("one");
                    Swap<String, Integer> swapped = new Swap<>();
                    Phantom<String, Integer> phantom = new Phantom<>();
                }
            }
            """, directory);
        assertContains(after, "List<String> names = new Names();", "Map<Integer, String> swapped = new Swap<>();",
            "Phantom<String, Integer> phantom = new Phantom<>();");
    }

    @Test
    public void respectsShadowedNamesAndCovariantMethods(@TempDir Path directory) throws Exception {
        String after = rewrite("""
            public class Example {
                private static class List<T> {}
                private static class ArrayList<T> {}
                private static class Narrow extends java.util.ArrayList<CharSequence> {
                    @Override
                    public String get(int index) { return "one"; }
                }
                public void run() {
                    ArrayList<String> pretend = new ArrayList<>();
                    java.util.ArrayList<String> actual = new java.util.ArrayList<>();
                    Narrow narrow = new Narrow();
                    String value = narrow.get(0);
                }
            }
            """, directory);
        assertContains(after, "ArrayList<String> pretend = new ArrayList<>();",
            "java.util.List<String> actual = new java.util.ArrayList<>();", "Narrow narrow = new Narrow();");
    }

    @Test
    public void preservesConcreteReturnsGenericInferenceAndTypeUseComments(@TempDir Path directory) throws Exception {
        String after = rewrite("""
            import java.util.*;

            public class Example {
                public ArrayList<String> concrete() {
                    ArrayList<String> concrete = new ArrayList<>();
                    return concrete;
                }
                public List<String> abstraction() {
                    ArrayList<String> abstraction = new ArrayList<>();
                    return abstraction;
                }
                private static <T> T make() { return null; }
                public void run() {
                    ArrayList<String> factory = make();
                    ArrayList<String> assigned = new ArrayList<>();
                    assigned = make();
                    ArrayList</* keep */ String> commented = new ArrayList<>();
                }
            }
            """, directory);
        assertContains(after, "ArrayList<String> concrete = new ArrayList<>();",
            "List<String> abstraction = new ArrayList<>();", "ArrayList<String> factory = make();",
            "ArrayList<String> assigned = new ArrayList<>();", "ArrayList</* keep */ String> commented");
    }

    @Test
    public void checkRejectsConcreteDeclarationsAndApplyAddsTheInterfaceImport(@TempDir Path directory)
        throws Exception {
        Path input = directory.resolve("Example.java");
        String before = """
            import java.util.ArrayList;

            public class Example {
                private final ArrayList<String> values = new ArrayList<>();
            }
            """;
        Files.writeString(input, before);
        Process check = formatter(directory, input, "--check").start();
        var checkOutput = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(1, check.waitFor(), checkOutput);
        assertTrue(checkOutput.contains("Example.java:4:"), checkOutput);
        assertEquals(before, Files.readString(input));
        Process apply = formatter(directory, input, "--apply").start();
        var applyOutput = new String(apply.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, apply.waitFor(), applyOutput);
        String after = Files.readString(input);
        assertContains(after, "import java.util.List;", "private final List<String> values = new ArrayList<>();");
        Process recheck = formatter(directory, input, "--check").start();
        var recheckOutput = new String(recheck.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, recheck.waitFor(), recheckOutput);
        compile(after, directory);
    }

    @Test
    public void preservesTypeUseAnnotationsAndFormatterOffRegions(@TempDir Path directory) throws Exception {
        String after = rewrite("""
            import java.util.*;
            import java.lang.annotation.*;

            public class Example {
                @Target(ElementType.TYPE_USE)
                private @interface Marker {}
                private ArrayList<@Marker String> annotated = new ArrayList<>();
                // @formatter:off
                private ArrayList<String> disabled = new ArrayList<>();
                // @formatter:on
                private ArrayList<String> enabled = new ArrayList<>();
            }
            """, directory);
        assertContains(after, "ArrayList<@Marker String> annotated", "ArrayList<String> disabled",
            "List<String> enabled");
    }

    @Test
    public void keepsCollectionResourceTypesAutoCloseable(@TempDir Path directory) throws Exception {
        String after = rewrite("""
            import java.util.*;

            public class Example {
                private static class ResourceList extends ArrayList<String> implements AutoCloseable {
                    @Override
                    public void close() {}
                }
                public void run() {
                    try (var resource = new ResourceList()) {
                        resource.add("one");
                    }
                }
            }
            """, directory);
        assertContains(after, "try (ResourceList resource = new ResourceList())");
    }

    @Test
    public void preservesNarrowerExceptionsAndVarargsOverrides(@TempDir Path directory) throws Exception {
        String after = rewrite("""
            import java.util.*;
            import java.util.concurrent.*;

            public class Example {
                private static class NoThrowQueue extends ArrayBlockingQueue<String> {
                    private NoThrowQueue() { super(1); }
                    @Override
                    public String take() { return "one"; }
                }
                private static class VarargsList extends ArrayList<String> {
                    @Override
                    public <T> T[] toArray(T... values) { return values; }
                }
                public void run() {
                    NoThrowQueue queue = new NoThrowQueue();
                    String value = queue.take();
                    VarargsList varargs = new VarargsList();
                    String[] copied = varargs.toArray("one", "two");
                }
            }
            """, directory);
        assertContains(after, "NoThrowQueue queue = new NoThrowQueue();", "VarargsList varargs = new VarargsList();");
    }

    private static String rewrite(String source, Path directory) throws Exception {
        String after = RailroadJavaStyle.rewrite(source);
        assertEquals(after, RailroadJavaStyle.rewrite(after), "rewrite must be idempotent");
        compile(after, directory);
        return after;
    }

    private static void compile(String source, Path directory) throws Exception {
        Path input = directory.resolve("Example.java");
        Files.writeString(input, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-proc:none", "-d", directory.toString(), input.toString()), "rewritten source must compile");
    }

    private static void assertContains(String source, String... snippets) {
        for (String snippet : snippets) {
            assertTrue(source.contains(snippet), snippet + "\n" + source);
        }
    }

    private static ProcessBuilder formatter(Path directory, Path input, String mode) throws Exception {
        String classpath = Path.of(RailroadJavaStyle.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString();
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", classpath, RailroadJavaStyle.class.getName(), mode, "--all", "--source-path", directory.toString(),
            input.toString()).redirectErrorStream(true);
    }
}
