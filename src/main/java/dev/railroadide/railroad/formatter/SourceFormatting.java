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

            if (!source.startsWith("//", offset) && !source.startsWith("/*", offset)) {
                offset++;
                continue;
            }

            offset = endOfComment(source, start);
            enabled = formatterEnabled(source.substring(start, offset), enabled);
        }

        return enabled;
    }

    static int endOfComment(String source, int start) {
        if (source.startsWith("//", start)) {
            int end = source.indexOf('\n', start + 2);
            return end < 0 ? source.length() : end;
        }
        int end = source.indexOf("*/", start + 2);
        return end < 0 ? source.length() : end + 2;
    }

    static boolean formatterEnabled(String comment, boolean previouslyEnabled) {
        return comment.contains("@formatter:on") || (previouslyEnabled && !comment.contains("@formatter:off"));
    }
}
