package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClosingBraceSpacingTest {
    @Test
    public void separatesFollowingCodeAndRemovesBlankLinesBetweenClosingBraces() {
        assertFormat("if (ready) {\n    work();\n}\nnext();\n",
            "if (ready) {\n    work();\n}\n\nnext();\n");
        assertFormat("{\n    {\n    }\n\n\n}\n", "{\n    {\n    }\n}\n");
        assertFormat("class First {\n}\nclass Second {\n}\n",
            "class First {\n}\n\nclass Second {\n}\n");
    }

    @Test
    public void preservesIndentationAndCrLf() {
        assertFormat("{\r\n\t{\r\n\t}\r\n\r\n}\r\nnext();\r\n",
            "{\r\n\t{\r\n\t}\r\n}\r\n\r\nnext();\r\n");
    }

    @Test
    public void keepsClosingPunctuationAttachedAndSeparatesTheNextStatement() {
        assertFormat("run(() -> {\n    work();\n});\nnext();\n",
            "run(() -> {\n    work();\n});\n\nnext();\n");
        assertFormat("Runnable action = () -> {\n    work();\n};\nnext();\n",
            "Runnable action = () -> {\n    work();\n};\n\nnext();\n");
        assertFormat("{\n    run(() -> {\n        work();\n    });\n\n}\n",
            "{\n    run(() -> {\n        work();\n    });\n}\n");
        String arguments = "run(() -> {\n    work();\n}, other);\n";
        assertFormat(arguments, arguments);
        assertFormat("run(() -> {\n    work();\n}); // callback\nnext();\n",
            "run(() -> {\n    work();\n}); // callback\n\nnext();\n");
        assertFormat("run(() -> {\n    work();\n});\n++index;\n",
            "run(() -> {\n    work();\n});\n\n++index;\n");
    }

    @Test
    public void preservesContinuationClauses() {
        String source = """
            if (ready) {
                work();
            } else {
                work();
            }

            try {
                work();
            } catch (Exception exception) {
                work();
            } finally {
                work();
            }

            do {
                work();
            } while (ready);
            """;
        assertFormat(source, source);
    }

    @Test
    public void ignoresBracesInsideCommentsStringsCharactersAndTextBlocks() {
        String source = "String text = \"} more code }\";\n"
            + "char brace = '}';\n"
            + "String escaped = \"\\\"} still inside the string\";\n"
            + "String block = \"\"\"\n}\n\nmore text\n\"\"\";\n"
            + "/* }\n\nmore comment } */\n"
            + "// }\nnext();\n";
        assertFormat(source, source);
        String commentBetweenBraces = "{\n    {}\n\n    // Keep the comment with this brace.\n}\n";
        assertFormat(commentBetweenBraces, commentBetweenBraces);
    }

    @Test
    public void respectsFormatterOffRegions() {
        String disabled = "// @formatter:off\n{\n    {}\n\n}\nnext();\n// @formatter:on\n";
        assertFormat(disabled + "{\n}\nnext();\n", disabled + "{\n}\n\nnext();\n");
    }

    @Test
    public void keepsJavadocsAttachedToTheirDeclarations() {
        assertFormat("void first() {\n}\n/** Documentation. */\nvoid second() {\n}\n",
            "void first() {\n}\n\n/** Documentation. */\nvoid second() {\n}\n");
        assertFormat("void first() {\n}\n/**\n * Documentation.\n */\nvoid second() {\n}\n",
            "void first() {\n}\n\n/**\n * Documentation.\n */\nvoid second() {\n}\n");
    }

    @Test
    public void keepsBodiesAttachedToConditionsContainingSwitchExpressions() {
        String source = """
            @Override
            public Void scan(Tree tree, Void unused) {
                if (tree != null && switch (tree.getKind()) {
                    case BREAK, CONTINUE, RETURN, THROW, YIELD -> true;
                    default -> false;
                }) {
                    inspect(tree, getCurrentPath());
                }

                return super.scan(tree, unused);
            }
            """;
        assertFormat(source, source);
    }

    @Test
    public void keepsBodiesAttachedToConditionsContainingLambdasAndAnonymousClasses() {
        String lambda = "if (check(() -> {\n    work();\n})) {\n    work();\n}\n";
        assertFormat(lambda, lambda);
        String anonymous = "while (check(new Object() {\n    void work() {}\n})) {\n    work();\n}\n";
        assertFormat(anonymous, anonymous);
        String continued = "boolean ready = switch (value) {\n    default -> true;\n} && check();\n";
        assertFormat(continued, continued);
        String typeTest = "boolean ready = switch (value) {\n    default -> item;\n} instanceof String;\n";
        assertFormat(typeTest, typeTest);
    }

    @Test
    public void repairsExistingBlankLinesBetweenExpressionConditionsAndTheirBodies() {
        String before = "if (switch (value) {\n    default -> true;\n})\n\n{\n    work();\n}\n";
        String after = "if (switch (value) {\n    default -> true;\n}) {\n    work();\n}\n";
        assertFormat(before, after);
        assertFormat(before.replace("\n", "\r\n"), after.replace("\n", "\r\n"));
    }

    private static void assertFormat(String before, String after) {
        assertEquals(after, ClosingBraceSpacing.format(before));
        assertEquals(after, ClosingBraceSpacing.format(after), "formatting must be idempotent");
    }
}
