package dev.railroadide.railroad.formatter;

/**
 * Expands single-line Javadocs before Eclipse formats comment wrapping and indentation.
 */
public final class SingleLineJavadocStyle {
    private SingleLineJavadocStyle() {
    }

    public static String format(String source) {
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        var result = new StringBuilder(source.length());
        boolean disabled = false;
        for (int offset = 0; offset < source.length();) {
            int start = offset;
            char current = source.charAt(offset);
            if (current == '"' || current == '\'') {
                offset = ClosingBraceSpacing.endOfLiteral(source, offset);
                result.append(source, start, offset);
            } else if (source.startsWith("//", offset)) {
                int end = source.indexOf('\n', offset + 2);
                offset = end < 0 ? source.length() : end;
                String comment = source.substring(start, offset);
                disabled = formatterDisabled(comment, disabled);
                result.append(comment);
            } else if (source.startsWith("/*", offset)) {
                int end = source.indexOf("*/", offset + 2);
                offset = end < 0 ? source.length() : end + 2;
                String comment = source.substring(start, offset);
                disabled = formatterDisabled(comment, disabled);
                if (!disabled && comment.startsWith("/**") && end >= start + 3
                    && comment.indexOf('\n') < 0 && comment.indexOf('\r') < 0) {
                    String body = source.substring(start + 3, end).strip();
                    int lineStart = Math.max(source.lastIndexOf('\n', start - 1),
                        source.lastIndexOf('\r', start - 1)) + 1;
                    String indentation = source.substring(lineStart, start);
                    if (!indentation.isBlank()) {
                        indentation = "";
                    }

                    result.append("/**").append(newline).append(indentation).append(" *")
                        .append(body.isEmpty() ? "" : " " + body).append(newline)
                        .append(indentation).append(" */");
                } else {
                    result.append(comment);
                }
            } else {
                result.append(current);
                offset++;
            }
        }

        return result.toString();
    }

    private static boolean formatterDisabled(String comment, boolean disabled) {
        if (comment.contains("@formatter:off")) {
            disabled = true;
        }

        if (comment.contains("@formatter:on")) {
            disabled = false;
        }

        return disabled;
    }
}
