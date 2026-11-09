package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QualifiedNameStyleTest {
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
