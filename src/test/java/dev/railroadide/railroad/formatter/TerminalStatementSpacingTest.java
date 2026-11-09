package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TerminalStatementSpacingTest {
    @Test
    public void separatesAllTerminalStatementsIncludingLabeledJumpsAndSwitchCases() {
        String before = """
            class Example {
                void run(boolean done) {
                    outer: while (true) {
                        if (done)
                            break outer;
                        work();
                        if (done)
                            continue outer;
                        work();
                    }
                    if (done)
                        return;
                    work();
                    if (done)
                        throw new IllegalStateException();
                    work();
                }
                int choose(int value) {
                    return switch (value) {
                        case 0:
                            yield 1;
                        default:
                            yield 2;
                    };
                }
                void work() {}
            }
            """;
        String after = before.replace("break outer;\n", "break outer;\n\n")
            .replace("continue outer;\n", "continue outer;\n\n")
            .replace("return;\n", "return;\n\n")
            .replace("throw new IllegalStateException();\n", "throw new IllegalStateException();\n\n")
            .replace("yield 1;\n", "yield 1;\n\n");
        assertRewrite(before, after);
    }

    @Test
    public void findsTheEndOfMultilineExpressionsAndNestedLambdaStatements() {
        String before = """
            class Example {
                Runnable make(boolean done) {
                    if (done)
                        return () -> {
                            work();
                            return;
                        };
                    work();
                    throw new IllegalStateException(
                        "return; break; continue; yield; throw;");
                }
                void work() {}
            }
            """;
        assertRewrite(before, before.replace("            };\n", "            };\n\n"));
    }

    @Test
    public void separatesThrowingSwitchRules() {
        String before = """
            class Example {
                int choose(int value) {
                    return switch (value) {
                        case 0 -> throw new IllegalArgumentException("zero;");
                        default -> 1;
                    };
                }
            }
            """;
        assertRewrite(before, before.replace("Exception(\"zero;\");\n", "Exception(\"zero;\");\n\n"));
    }

    @Test
    public void keepsTrailingCommentsAndCrLf() {
        String before = """
            class Example {
                void run(boolean done) {
                    if (done)
                        return; // explain the early exit
                    work();
                    if (done)
                        throw new IllegalStateException(); /* explain the failure */
                    work();
                }
                void work() {}
            }
            """;
        String after = before.replace("early exit\n", "early exit\n\n")
            .replace("failure */\n", "failure */\n\n");
        assertRewrite(before.replace("\n", "\r\n"), after.replace("\n", "\r\n"));
    }

    @Test
    public void preservesClosingBracesAndElseAndDoWhileContinuations() {
        String source = """
            class Example {
                void run(boolean done) {
                    if (done)
                        return;
                    else
                        throw new IllegalStateException();
                }
                void loop(boolean done) {
                    do
                        break;
                    while (done);
                }
            }
            """;
        assertRewrite(source, source);
    }

    @Test
    public void separatesGuardClausesAfterRemovingTheirOptionalBraces() {
        assertRewrite("""
            class Example {
                void run(boolean done) {
                    if (done) {
                        return;
                    }
                    work();
                }
                void work() {}
            }
            """, """
            class Example {
                void run(boolean done) {
                    if (done)
                        return;

                    work();
                }
                void work() {}
            }
            """);
    }

    @Test
    public void respectsFormatterTagsAndIgnoresKeywordsInLiterals() {
        String before = """
            class Example {
                void run(boolean done) {
                    String text = "@formatter:off return;";
                    // @formatter:off
                    if (done)
                        return;
                    work();
                    // @formatter:on
                    if (done)
                        return;
                    work();
                }
                void work() {}
            }
            """;
        String after = before.replace("// @formatter:on\n        if (done)\n            return;\n",
            "// @formatter:on\n        if (done)\n            return;\n\n");
        assertRewrite(before, after);
    }

    private static void assertRewrite(String before, String after) {
        assertEquals(after, RailroadJavaStyle.rewrite(before));
        assertEquals(after, RailroadJavaStyle.rewrite(after), "rewrite must be idempotent");
    }
}
