package dev.railroadide.railroad.formatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SingleLineJavadocStyleTest {
    @Test
    public void expandsDescriptionsWithDeclarationIndentation() {
        assertFormat(
            "    /** Discover entrypoints before loading Minecraft, leaving a boundary for future transformations. */\n",
            "    /**\n"
                + "     * Discover entrypoints before loading Minecraft, leaving a boundary for future transformations.\n"
                + "     */\n");
        assertFormat("/** API. */\npublic class Api {}\n",
            "/**\n * API.\n */\npublic class Api {}\n");
    }

    @Test
    public void preservesCrLfInlineTagsAndEmptyComments() {
        assertFormat("\t/** Uses {@link String} and {@code a < b}. */\r\n",
            "\t/**\r\n\t * Uses {@link String} and {@code a < b}.\r\n\t */\r\n");
        assertFormat("    /** */\n", "    /**\n     *\n     */\n");
        assertFormat("/**/\n", "/**/\n");
    }

    @Test
    public void ignoresOrdinaryCommentsLiteralsAndFormatterOffRegions() {
        String source = "/* Ordinary comment. */\n// /** Not Javadoc. */\n"
            + "String text = \"/** Not Javadoc. */\";\n"
            + "char slash = '/';\n"
            + "String block = \"\"\"\n/** Not Javadoc. */\n\"\"\";\n"
            + "// @formatter:off\n/** Keep this line. */\n// @formatter:on\n";
        assertFormat(source + "/** Expand this line. */\n",
            source + "/**\n * Expand this line.\n */\n");
    }

    private static void assertFormat(String before, String after) {
        assertEquals(after, SingleLineJavadocStyle.format(before));
        assertEquals(after, SingleLineJavadocStyle.format(after), "formatting must be idempotent");
    }
}
