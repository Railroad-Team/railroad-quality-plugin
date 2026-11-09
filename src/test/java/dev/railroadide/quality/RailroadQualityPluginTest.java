package dev.railroadide.quality;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RailroadQualityPluginTest {
    @TempDir
    private Path project;

    @Test
    public void formatsAnIndependentJava17ProjectAndChecksCoverageWithoutCompiling() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'consumer'\n");
        Files.writeString(project.resolve("build.gradle"), """
            plugins { id 'dev.railroadide.quality' }
            repositories { mavenCentral() }
            java { sourceCompatibility = JavaVersion.VERSION_17 }
            sourceSets { extra }
            """);
        Path source = project.resolve("src/main/java/example/Api.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package example;
            /** API. */
            public class Api {
            public String value(){if(true){return "value";}return "";}
            /** Discover entrypoints before loading Minecraft, leaving a boundary for future transformations. */
            private void locals(){var text="value";var values=java.util.List.of(text);var builder=new StringBuilder();}
            private void collections(){java.util.ArrayList<String> values=new java.util.ArrayList<String>();}
            private void guarded(boolean stop){if(stop)return;locals();}
            }
            """);
        Path extra = project.resolve("src/extra/java/example/Extra.java");
        Files.createDirectories(extra.getParent());
        Files.writeString(extra, "package example; public class Extra {public void run(){}}\n");
        BuildResult rejected = runner("semanticFormatCheck").buildAndFail();
        assertTrue(rejected.getOutput().contains("Java source violates semantic formatting rules"));
        assertTrue(Files.readString(source).contains("var text="));
        BuildResult formatted = runner("format").build();
        assertTrue(formatted.getOutput().contains("BUILD SUCCESSFUL"));
        assertTrue(Files.readString(source).replace("\r\n", "\n").contains("if (true)\n"), Files.readString(source));
        assertTrue(Files.readString(extra).contains("public void run()"));
        assertTrue(Files.readString(source).contains("String text = \"value\";"), Files.readString(source));
        assertTrue(Files.readString(source).contains("List<String> values = List.of(text);"), Files.readString(source));
        assertTrue(Files.readString(source).contains("var builder = new StringBuilder();"), Files.readString(source));
        assertTrue(Files.readString(source).contains("List<String> values = new ArrayList<>();"),
            Files.readString(source));
        String javaSource = Files.readString(source).replace("\r\n", "\n");
        assertTrue(javaSource.contains("/**\n * API.\n */\npublic class Api"), javaSource);
        assertTrue(javaSource.contains("    /**\n"
            + "     * Discover entrypoints before loading Minecraft, leaving a boundary for future transformations.\n"
            + "     */\n    private void locals()"), javaSource);
        assertTrue(javaSource.contains("return;\n\n        locals();"), javaSource);
        String before = Files.readString(source);
        runner("format").build();
        assertEquals(before, Files.readString(source));
        runner("formatCheck").build();
        runner("javadocCoverage").build();
        Path report = project.resolve("build/reports/javadoc-coverage");
        assertEquals("1", Files.readString(report.resolve("violations.txt")));
        assertTrue(Files.readString(report.resolve("index.html")).contains("Missing Javadoc"));
        assertTrue(runner("javadocCoverageCheck").buildAndFail().getOutput().contains("incomplete Javadocs"));
        Files.writeString(source, """
            package example;
            /** API. */
            public class Api extends missing.Dependency {}
            """);
        runner("javadocCoverageCheck").build();
        assertEquals("0", Files.readString(report.resolve("violations.txt")));
    }

    private GradleRunner runner(String task) {
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath()
            .withArguments(task, "--stacktrace", "--console=plain");
    }

    @Test
    public void separatesCompletedBlocksWithoutBlankLinesBetweenClosingBraces() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'brace-spacing'\n");
        Files.writeString(project.resolve("build.gradle"), """
            plugins { id 'dev.railroadide.quality' }
            repositories { mavenCentral() }
            java { sourceCompatibility = JavaVersion.VERSION_17 }
            """);
        Path source = project.resolve("src/main/java/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            public class Example {
                private void run(boolean ready) {
                    if (ready) {
                        work();
                    } work();
                    while (ready) {
                        work();
                    }
                    work();
                    if (ready) {
                        while (ready) {
                            work();


                        }


                    }


                }
                private void work() {}


            }
            """);
        String original = Files.readString(source);
        String checkOutput = runner("spotlessJavaCheck").buildAndFail().getOutput();
        assertTrue(checkOutput.contains("spotlessJavaCheck"), checkOutput);
        assertEquals(original, Files.readString(source));

        runner("format").build();
        String formatted = Files.readString(source).replace("\r\n", "\n");
        assertTrue(formatted.contains("        }\n\n        work();"), formatted);
        assertTrue(formatted.contains("            }\n        }\n    }"), formatted);
        assertFalse(formatted.matches("(?s).*}\\n[\\t ]*\\n\\s*}.*"), formatted);
        runner("format").build();
        assertEquals(formatted, Files.readString(source).replace("\r\n", "\n"));
        runner("formatCheck").build();
    }

    @Test
    public void repairsSwitchExpressionConditionSpacingAndKeepsItStable() throws Exception {
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'switch-spacing'\n");
        Files.writeString(project.resolve("build.gradle"), """
            plugins { id 'dev.railroadide.quality' }
            repositories { mavenCentral() }
            java { sourceCompatibility = JavaVersion.VERSION_17 }
            """);
        Path source = project.resolve("src/main/java/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            import com.sun.source.tree.Tree;
            import com.sun.source.util.TreePath;
            import com.sun.source.util.TreePathScanner;

            public class Example extends TreePathScanner<Void, Void> {
                @Override
                public Void scan(Tree tree, Void unused) {
                    if (tree != null && switch (tree.getKind()) {
                        case BREAK, CONTINUE, RETURN, THROW, YIELD -> true;
                        default -> false;
                    })

                    {
                        inspect(tree, getCurrentPath());
                    }

                    return super.scan(tree, unused);
                }

                private void inspect(Tree tree, TreePath path) {}
            }
            """);
        runner("format").build();
        String formatted = Files.readString(source).replace("\r\n", "\n");
        assertTrue(formatted.contains("        }) {\n            inspect(tree, getCurrentPath());"), formatted);
        assertFalse(formatted.matches("(?s).*}\\)\\s*\\n\\s*\\n\\s*\\{.*"), formatted);
        runner("format").build();
        assertEquals(formatted, Files.readString(source).replace("\r\n", "\n"));
        runner("formatCheck").build();
    }
}
