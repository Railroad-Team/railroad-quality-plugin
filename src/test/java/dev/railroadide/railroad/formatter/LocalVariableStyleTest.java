package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalVariableStyleTest {
    @Test
    public void replacesLiteralsExpressionsInstanceMembersAndUnqualifiedCalls() {
        assertRewrite("""
            class Example {
                String field = "text";
                String value() { return field; }
                void run(Example other) {
                    var text = "hello";
                    final var count = 1 + 2;
                    var copied = text;
                    var result = value();
                    var member = other.field;
                    var call = other.value();
                    var array = new String[] { "hello" };
                    var cast = (CharSequence) text;
                }
            }
            """, """
            class Example {
                String field = "text";
                String value() { return field; }
                void run(Example other) {
                    String text = "hello";
                    final int count = 1 + 2;
                    String copied = text;
                    String result = value();
                    String member = other.field;
                    String call = other.value();
                    String[] array = new String[] { "hello" };
                    CharSequence cast = (CharSequence) text;
                }
            }
            """);
    }

    @Test
    public void allowsConstructorsAndTypeQualifiedMethodsAndFields() {
        String source = """
            class Example {
                static final String TEXT = "text";
                static Example create() { return new Example(); }
                void run() {
                    var constructed = new Example();
                    var anonymous = new Example() {};
                    var factory = Example.create();
                    var field = Example.TEXT;
                    var primitive = Integer.parseInt("123");
                    var parenthesized = (Example.create());
                }
            }
            """;
        assertRewrite(source, source);
    }

    @Test
    public void resolvesQualifierSymbolsInsteadOfGuessingFromCapitalization() {
        assertRewrite("""
            class Example {
                String value() { return "text"; }
                void run(Example Foo) {
                    var result = Foo.value();
                }
            }
            class lower {
                static String value() { return "text"; }
                void run() {
                    var result = lower.value();
                }
            }
            """, """
            class Example {
                String value() { return "text"; }
                void run(Example Foo) {
                    String result = Foo.value();
                }
            }
            class lower {
                static String value() { return "text"; }
                void run() {
                    var result = lower.value();
                }
            }
            """);
    }

    @Test
    public void alwaysExpandsCollectionsIncludingFactoriesSubclassesAndIterators() {
        assertRewrite("""
            import java.util.ArrayDeque;
            import java.util.ArrayList;
            import java.util.Iterator;
            import java.util.List;
            import java.util.Map;
            import java.util.Set;

            class Example {
                static class Names extends ArrayList<String> {}
                void run() {
                    var list = List.of("one");
                    var map = Map.of("one", 1);
                    var set = Set.of("one");
                    var queue = new ArrayDeque<String>();
                    var empty = new ArrayList<>();
                    var names = new Names();
                    var iterator = list.iterator();
                    ArrayList<String> explicit = new ArrayList<String>();
                }
            }
            """, """
            import java.util.ArrayDeque;
            import java.util.ArrayList;
            import java.util.Iterator;
            import java.util.List;
            import java.util.Map;
            import java.util.Set;
            import java.util.Deque;

            class Example {
                static class Names extends ArrayList<String> {}
                void run() {
                    List<String> list = List.of("one");
                    Map<String, Integer> map = Map.of("one", 1);
                    Set<String> set = Set.of("one");
                    Deque<String> queue = new ArrayDeque<>();
                    List<Object> empty = new ArrayList<>();
                    List<String> names = new Names();
                    Iterator<String> iterator = list.iterator();
                    List<String> explicit = new ArrayList<>();
                }
            }
            """);
    }

    @Test
    public void importsInferredTypesAndPreservesTypeArgumentsAndComments() {
        assertRewrite("""
            class Example {
                java.time.Instant now() { return null; }
                java.util.List<String> names() { return null; }
                void run() {
                    final /* before */ var /* after */ instant = now();
                    var names = names();
                }
            }
            """, """
            import java.time.Instant;
            import java.util.List;

            class Example {
                Instant now() { return null; }
                List<String> names() { return null; }
                void run() {
                    final /* before */ Instant /* after */ instant = now();
                    List<String> names = names();
                }
            }
            """);
    }

    @Test
    public void handlesForInitializersEnhancedForResourcesAndLambdaParameters() {
        assertRewrite("""
            import java.io.StringReader;
            import java.util.List;
            import java.util.function.Consumer;

            class Example {
                StringReader open() { return null; }
                void run() throws Exception {
                    for (var index = 0; index < 2; index++) {
                        System.out.println(index);
                    }
                    for (var item : List.of("one")) {
                        System.out.println(item);
                    }
                    try (var reader = open()) {
                        reader.read();
                    }
                    Consumer<String> print = (var text) -> System.out.println(text);
                }
            }
            """, """
            import java.io.StringReader;
            import java.util.List;
            import java.util.function.Consumer;

            class Example {
                StringReader open() { return null; }
                void run() throws Exception {
                    for (int index = 0; index < 2; index++) {
                        System.out.println(index);
                    }
                    for (String item : List.of("one")) {
                        System.out.println(item);
                    }
                    try (StringReader reader = open()) {
                        reader.read();
                    }
                    Consumer<String> print = (String text) -> System.out.println(text);
                }
            }
            """);
    }

    @Test
    public void stillUsesVarForExactNonCollectionConstructionsWithMixedQualification() {
        assertRewrite("""
            class Example {
                void run() {
                    java.lang.StringBuilder first = new StringBuilder();
                    StringBuilder second = new java.lang.StringBuilder();
                }
            }
            """, """
            class Example {
                void run() {
                    var first = new StringBuilder();
                    var second = new StringBuilder();
                }
            }
            """);
    }

    @Test
    public void preservesCommentsAndTypeAnnotationsInExplicitConstructorTypes() {
        String source = """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            class Example {
                @Target(ElementType.TYPE_USE)
                @interface Marker {}
                static class Box<T> {}
                void run() {
                    Box</* keep */ String> commented = new Box<String>();
                    Box<@Marker String> annotated = new Box<String>();
                }
            }
            """;
        assertRewrite(source, source.replace("new Box<String>()", "new Box<>()"));
    }

    @Test
    public void preservesWildcardTypeParametersAndConflictingTypeNames(@TempDir Path directory) throws Exception {
        String source = """
            public class Example<T extends Number> {
                public static class List {}
                private T value() { return null; }
                private java.util.List<? extends T> values() { return null; }
                public void run() {
                    var value = value();
                    var values = values();
                    var fromFactory = java.util.List.of("one");
                }
            }
            """;
        String after = """
            public class Example<T extends Number> {
                public static class List {}
                private T value() { return null; }
                private java.util.List<? extends T> values() { return null; }
                public void run() {
                    T value = value();
                    java.util.List<? extends T> values = values();
                    java.util.List<String> fromFactory = java.util.List.of("one");
                }
            }
            """;
        assertRewrite(source, after);
        Path input = directory.resolve("Example.java");
        Files.writeString(input, after);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-proc:none", "-d", directory.toString(), input.toString()));
    }

    @Test
    public void checkRejectsVarWithoutWritingAndApplyResolvesProjectTypes(@TempDir Path directory) throws Exception {
        Path library = Files.createDirectories(directory.resolve("library"));
        Files.writeString(library.resolve("Widget.java"), """
            package library;
            public class Widget {
                public Widget copy() { return this; }
            }
            """);
        Path input = directory.resolve("Example.java");
        String before = """
            public class Example {
                public void run(library.Widget widget) {
                    var copy = widget.copy();
                    var items = java.util.List.of("one");
                }
            }
            """;
        Files.writeString(input, before);
        Process check = formatter(directory, input, "--check").start();
        var output = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(1, check.waitFor(), output);
        assertTrue(output.contains("Example.java:3:"), output);
        assertTrue(output.contains("Example.java:4:"), output);
        assertEquals(before, Files.readString(input));

        Process apply = formatter(directory, input, "--apply").start();
        var applyOutput = new String(apply.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, apply.waitFor(), applyOutput);
        String after = Files.readString(input);
        assertTrue(after.contains("Widget copy = widget.copy();"), after);
        assertTrue(after.contains("List<String> items = List.of(\"one\");"), after);
        Process recheck = formatter(directory, input, "--check").start();
        var recheckOutput = new String(recheck.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, recheck.waitFor(), recheckOutput);
    }

    @Test
    public void checkReportsUnresolvedAndNonDenotableTypesWithoutInventingAType(@TempDir Path directory)
        throws Exception {
        Path input = directory.resolve("Example.java");
        String source = """
            public class Example {
                public void run() {
                    var missing = unavailable();
                    var mixed = java.util.List.of(1, "one");
                }
            }
            """;
        Files.writeString(input, source);
        Process check = formatter(directory, input, "--check").start();
        var output = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(1, check.waitFor(), output);
        assertTrue(output.contains("Cannot replace disallowed var for 'missing'"), output);
        assertTrue(output.contains("Cannot replace disallowed var for 'mixed'"), output);
        assertEquals(source, Files.readString(input));
    }

    private static ProcessBuilder formatter(Path directory, Path input, String mode) throws Exception {
        String classpath = Path.of(RailroadJavaStyle.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString();
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", classpath, RailroadJavaStyle.class.getName(), mode, "--all", "--source-path", directory.toString(),
            input.toString()).redirectErrorStream(true);
    }

    private static void assertRewrite(String before, String after) {
        assertEquals(after, RailroadJavaStyle.rewrite(before));
        assertEquals(after, RailroadJavaStyle.rewrite(after), "rewrite must be idempotent");
    }
}
