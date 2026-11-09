package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DiamondStyleTest {
    @Test
    public void simplifiesFieldsLocalsAssignmentsReturnsAndArguments(@TempDir Path directory) throws Exception {
        String before = """
            import java.util.ArrayList;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;

            public class Example {
                private final List<String> field = new ArrayList<String>();
                public List<String> make() {
                    return new ArrayList<String>();
                }
                public void run() {
                    List<String> list = new ArrayList<String>(10);
                    Map<String, List<Integer>> map = new HashMap<String, List<Integer>>();
                    list = new ArrayList<String>();
                    use(new ArrayList<String>());
                }
                private void use(List<String> values) {}
            }
            """;
        String after = before.replace("new ArrayList<String>", "new ArrayList<>")
            .replace("new HashMap<String, List<Integer>>", "new HashMap<>");
        assertRewrite(before, after);
        compile(after, directory);
    }

    @Test
    public void preservesVarInferenceAndTheExistingConstructorVarRule() {
        String before = """
            class Example {
                static class Box<T> {
                    Box() {}
                    Box(T value) {}
                }
                void run() {
                    var empty = new Box<String>();
                    var withArgument = new Box<String>("text");
                    Box<String> exact = new Box<String>();
                }
            }
            """;
        assertRewrite(before, before.replace("Box<String> exact", "var exact"));
    }

    @Test
    public void preservesTypeArgumentsWhenDiamondWouldInferDifferentArgumentsOrAmbiguousOverloads() {
        String source = """
            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;

            class Example {
                void run() {
                    List<?> unknown = new ArrayList<String>();
                    Object erased = new ArrayList<String>();
                    Map<String, ?> partial = new HashMap<String, Integer>();
                    use(new ArrayList<String>());
                }
                void use(List<String> values) {}
                void use(Collection<Integer> values) {}
            }
            """;
        assertRewrite(source, source);
    }

    @Test
    public void keepsCommentsAndTypeUseAnnotationsInConstructorArguments() {
        String source = """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;
            import java.util.ArrayList;
            import java.util.List;

            class Example {
                @Target(ElementType.TYPE_USE)
                @interface Marker {}
                List<@Marker String> annotated = new ArrayList<@Marker String>();
                List<String> commented = new ArrayList</* keep */ String>();
            }
            """;
        assertRewrite(source, source);
    }

    @Test
    public void checksAnonymousClassRestrictionsBeforeUsingDiamond(@TempDir Path directory) throws Exception {
        String before = """
            import java.util.ArrayList;
            import java.util.List;

            public class Example {
                private final List<String> overriding = new ArrayList<String>() {
                    @Override
                    public int size() { return 0; }
                };
                private final List<String> extraMethod = new ArrayList<String>() {
                    public void custom() {}
                };
            }
            """;
        String after = before.replace("overriding = new ArrayList<String>()", "overriding = new ArrayList<>()");
        assertRewrite(before, after);
        compile(after, directory);
    }

    @Test
    public void respectsFormatterTagsWithoutConfusingStringsForTags() {
        String before = """
            import java.util.ArrayList;
            import java.util.List;

            class Example {
                String text = "@formatter:off";
                // @formatter:off
                List<String> disabled = new ArrayList<String>();
                // @formatter:on
                List<String> enabled = new ArrayList<String>();
            }
            """;
        assertRewrite(before, before.replace("enabled = new ArrayList<String>()", "enabled = new ArrayList<>()"));
    }

    @Test
    public void checkRejectsRedundantArgumentsWithoutWritingAndApplyUsesProjectSources(@TempDir Path directory)
        throws Exception {
        Path library = Files.createDirectories(directory.resolve("library"));
        Files.writeString(library.resolve("Bag.java"),
            "package library; public class Bag<T> extends java.util.ArrayList<T> {}\n");
        Path input = directory.resolve("Example.java");
        String before = """
            import java.util.List;
            import library.Bag;

            public class Example {
                private final List<String> values = new Bag<String>();
            }
            """;
        Files.writeString(input, before);
        Process check = formatter(directory, input, "--check").start();
        var checkOutput = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(1, check.waitFor(), checkOutput);
        assertTrue(checkOutput.contains("Example.java:5:"), checkOutput);
        assertEquals(before, Files.readString(input));

        Process apply = formatter(directory, input, "--apply").start();
        var applyOutput = new String(apply.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, apply.waitFor(), applyOutput);
        assertEquals(before.replace("new Bag<String>()", "new Bag<>()"), Files.readString(input));
        Process recheck = formatter(directory, input, "--check").start();
        var recheckOutput = new String(recheck.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, recheck.waitFor(), recheckOutput);
        compile(Files.readString(input), directory);
    }

    private static ProcessBuilder formatter(Path directory, Path input, String mode) throws Exception {
        String classpath = Path.of(RailroadJavaStyle.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString();
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", classpath, RailroadJavaStyle.class.getName(), mode, "--all", "--source-path", directory.toString(),
            input.toString()).redirectErrorStream(true);
    }

    private static void compile(String source, Path directory) throws Exception {
        Path input = directory.resolve("Example.java");
        Files.writeString(input, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
            "-proc:none", "--source-path", directory.toString(), "-d", directory.toString(), input.toString()));
    }

    private static void assertRewrite(String before, String after) {
        assertEquals(after, RailroadJavaStyle.rewrite(before));
        assertEquals(after, RailroadJavaStyle.rewrite(after), "rewrite must be idempotent");
    }
}
