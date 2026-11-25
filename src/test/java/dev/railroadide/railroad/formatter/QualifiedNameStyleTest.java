package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QualifiedNameStyleTest {
    @Test
    public void respectsSamePackageTypesBeforeWildcardImports(@TempDir Path directory) throws Exception {
        Path pkg = Files.createDirectories(directory.resolve("example"));
        Files.writeString(pkg.resolve("Date.java"), "package example; public class Date {}");
        Path input = pkg.resolve("Example.java");
        String source = """
            package example;
            import java.util.*;

            public class Example {
                private Date local;
                private java.util.Date external;
                public long time() { return java.util.Date.from(java.time.Instant.EPOCH).getTime(); }
                public java.util.UUID id() { return java.util.UUID.randomUUID(); }
            }
            """;
        String expected = source.replace("import java.util.*;", "import java.util.*;\nimport java.time.Instant;")
            .replace("java.time.Instant.EPOCH", "Instant.EPOCH").replace("java.util.UUID", "UUID");
        Files.writeString(input, source);
        String[] arguments = {"--apply", "--all", "--source-path", directory.toString(), input.toString()};
        RailroadJavaStyle.main(arguments);
        assertEquals(expected, Files.readString(input));
        RailroadJavaStyle.main(arguments);
        assertEquals(expected, Files.readString(input));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-proc:none", "-sourcepath", directory.toString(), "-d", directory.toString(), input.toString()));
    }

    @Test
    public void onlyUsesAnImportThatShadowsAPackageTypeIfItAlreadyExists(@TempDir Path directory) throws Exception {
        Path pkg = Files.createDirectories(directory.resolve("example"));
        Files.writeString(pkg.resolve("Date.java"), "package example; public class Date {}");
        Path input = pkg.resolve("Example.java");
        String source = "package example;\npublic class Example { private java.util.Date value; }\n";
        String[] arguments = {"--apply", "--all", "--source-path", directory.toString(), input.toString()};
        Files.writeString(input, source);
        RailroadJavaStyle.main(arguments);
        assertEquals(source, Files.readString(input));

        String imported = source.replace("package example;", "package example;\nimport java.util.Date;");
        Files.writeString(input, imported);
        RailroadJavaStyle.main(arguments);
        assertEquals(imported.replace("private java.util.Date", "private Date"), Files.readString(input));
    }

    @Test
    public void keepsMemberQualificationInClassHeader(@TempDir Path directory) throws Exception {
        String source = """
            package example;

            interface Handler<T> {}
            public class Example implements Handler<Example.Item> {
                public class Item {}
            }
            """;
        assertRewrite(source, source, directory);
    }

    @Test
    public void shortensOwnNestedTypesInExplicitAndInferredCollections(@TempDir Path directory) throws Exception {
        assertRewrite("""
            package example;

            import java.util.ArrayList;
            import java.util.Map;

            public class Example {
                public record TypeCoverage(String name) {}
                public void run() {
                    var classes = new ArrayList<TypeCoverage>();
                    ArrayList<example.Example.TypeCoverage> explicit = new ArrayList<TypeCoverage>();
                    var map = Map.of("one", new TypeCoverage("one"));
                }
            }
            """, """
            package example;

            import java.util.ArrayList;
            import java.util.Map;
            import java.util.List;

            public class Example {
                public record TypeCoverage(String name) {}
                public void run() {
                    List<TypeCoverage> classes = new ArrayList<>();
                    List<TypeCoverage> explicit = new ArrayList<>();
                    Map<String, TypeCoverage> map = Map.of("one", new TypeCoverage("one"));
                }
            }
            """, directory);
    }

    @Test
    public void keepsOuterQualificationWhenAnotherNestedTypeShadowsTheSimpleName(@TempDir Path directory)
        throws Exception {
        assertRewrite("""
            package example;

            import java.util.ArrayList;

            public class Example {
                public static class Item {}
                public void run() {
                    var own = new ArrayList<Item>();
                }
                public static class Other {
                    public static class Item {}
                    public void run() {
                        var items = new ArrayList<Example.Item>();
                    }
                }
            }
            """, """
            package example;

            import java.util.ArrayList;
            import java.util.List;

            public class Example {
                public static class Item {}
                public void run() {
                    List<Item> own = new ArrayList<>();
                }
                public static class Other {
                    public static class Item {}
                    public void run() {
                        List<Example.Item> items = new ArrayList<>();
                    }
                }
            }
            """, directory);
    }

    @Test
    public void preservesTypeArgumentsOfEnclosingInstances(@TempDir Path directory) throws Exception {
        assertRewrite("""
            package example;

            import java.util.ArrayList;

            public class Example<T> {
                public class Item {}
                public void run() {
                    var items = new ArrayList<Item>();
                }
            }
            """, """
            package example;

            import java.util.ArrayList;
            import java.util.List;

            public class Example<T> {
                public class Item {}
                public void run() {
                    List<Example<T>.Item> items = new ArrayList<>();
                }
            }
            """, directory);
    }

    @Test
    public void reusesInheritedNestedTypes(@TempDir Path directory) throws Exception {
        assertRewrite("""
            package example;

            import java.util.ArrayList;

            class Parent {
                public static class Item {}
            }
            public class Example extends Parent {
                public void run() {
                    var items = new ArrayList<Item>();
                }
            }
            """, """
            package example;

            import java.util.ArrayList;
            import java.util.List;

            class Parent {
                public static class Item {}
            }
            public class Example extends Parent {
                public void run() {
                    List<Item> items = new ArrayList<>();
                }
            }
            """, directory);
    }

    private static void assertRewrite(String before, String after, Path directory) throws Exception {
        assertEquals(after, RailroadJavaStyle.rewrite(before));
        assertEquals(after, RailroadJavaStyle.rewrite(after), "rewrite must be idempotent");
        Path input = directory.resolve("Example.java");
        Files.writeString(input, after);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-proc:none", "-d", directory.toString(), input.toString()), "rewritten source must compile");
    }
}
