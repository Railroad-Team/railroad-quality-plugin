package dev.railroadide.railroad.formatter;

/**
 * Shared handling for formatter directives in Java comments.
 */
public final class SourceFormatting {
    private SourceFormatting() {
    }

    public static boolean isEnabled(String source, int position) {
        boolean enabled = true;
        for (int offset = 0; offset < position;) {
            int start = offset;
            char current = source.charAt(offset);
            if (current == '"' || current == '\'') {
                offset = ClosingBraceSpacing.endOfLiteral(source, offset);
                continue;
            }

            if (source.startsWith("//", offset)) {
                int end = source.indexOf('\n', offset + 2);
                offset = end < 0 ? source.length() : end;
            } else if (source.startsWith("/*", offset)) {
                int end = source.indexOf("*/", offset + 2);
                offset = end < 0 ? source.length() : end + 2;
            } else {
                offset++;
                continue;
            }

            String comment = source.substring(start, offset);
            if (comment.contains("@formatter:off")) {
                enabled = false;
            }

            if (comment.contains("@formatter:on")) {
                enabled = true;
            }
        }

        return enabled;
    }
}
