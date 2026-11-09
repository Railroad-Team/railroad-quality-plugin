package dev.railroadide.railroad.formatter;

/**
 * Finishes block spacing after Eclipse formats indentation and Java continuation clauses.
 */
public final class ClosingBraceSpacing {
    private ClosingBraceSpacing() {
    }

    public static String format(String source) {
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        var result = new StringBuilder(source.length());
        boolean afterBrace = false;
        boolean afterSemicolon = false;
        boolean afterClosingDelimiter = false;
        boolean disabled = false;
        for (int offset = 0; offset < source.length();) {
            int start = offset;
            char current = source.charAt(offset);
            if (Character.isWhitespace(current)) {
                while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) {
                    offset++;
                }

                String gap = source.substring(start, offset);
                if (afterBrace && afterClosingDelimiter && !afterSemicolon && !disabled
                    && offset < source.length() && source.charAt(offset) == '{') {
                    // Keep a condition's body attached, including repairing gaps from older formatter runs.
                    gap = " ";
                } else if (afterBrace && (!afterClosingDelimiter || afterSemicolon)
                    && !disabled && offset < source.length()) {
                    char next = source.charAt(offset);
                    int lastNewline = Math.max(gap.lastIndexOf('\n'), gap.lastIndexOf('\r'));
                    String indentation = lastNewline < 0 ? "" : gap.substring(lastNewline + 1);
                    if (next == '}') {
                        gap = newline + indentation;
                    } else if ((next != '/' || source.startsWith("/**", offset))
                        && (afterSemicolon || "()[],.;:+-*%&|^!?<>=~".indexOf(next) < 0)
                        && !isContinuation(source, offset, lastNewline < 0 && !afterSemicolon)
                        && gap.chars().filter(character -> character == '\n' || character == '\r')
                            .count() < (newline.length() * 2L)) {
                        gap = newline + newline + indentation;
                    }
                }

                result.append(gap);
                continue;
            }

            if (source.startsWith("//", offset) || source.startsWith("/*", offset)) {
                if (source.startsWith("//", offset)) {
                    int end = source.indexOf('\n', offset + 2);
                    offset = end < 0 ? source.length() : end;
                } else {
                    int end = source.indexOf("*/", offset + 2);
                    offset = end < 0 ? source.length() : end + 2;
                }

                String comment = source.substring(start, offset);
                if (comment.startsWith("/**")) {
                    afterBrace = false;
                    afterSemicolon = false;
                    afterClosingDelimiter = false;
                }

                if (comment.contains("@formatter:off")) {
                    disabled = true;
                    afterBrace = false;
                    afterSemicolon = false;
                    afterClosingDelimiter = false;
                }

                if (comment.contains("@formatter:on")) {
                    disabled = false;
                    afterBrace = false;
                    afterSemicolon = false;
                    afterClosingDelimiter = false;
                }

                result.append(comment);
                continue;
            }

            if (current == '"' || current == '\'') {
                offset = endOfLiteral(source, offset);
                result.append(source, start, offset);
                afterBrace = false;
                afterSemicolon = false;
                afterClosingDelimiter = false;
                continue;
            }

            if (current == '}') {
                afterBrace = true;
                afterSemicolon = false;
                afterClosingDelimiter = false;
            } else if (afterBrace && current == ';') {
                afterSemicolon = true;
            } else if (afterBrace && (current == ')' || current == ']')) {
                afterClosingDelimiter = true;
            } else {
                afterBrace = false;
                afterSemicolon = false;
                afterClosingDelimiter = false;
            }

            result.append(current);
            offset++;
        }

        return result.toString();
    }

    private static boolean isContinuation(String source, int offset, boolean sameLine) {
        int end = offset;
        while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) {
            end++;
        }

        return switch (source.substring(offset, end)) {
            case "else", "catch", "finally", "while" -> sameLine;
            case "instanceof" -> true;
            default -> false;
        };
    }

    public static int endOfLiteral(String source, int offset) {
        char quote = source.charAt(offset);
        boolean textBlock = source.startsWith("\"\"\"", offset);
        offset += textBlock ? 3 : 1;
        while (offset < source.length()) {
            if (source.charAt(offset) == '\\') {
                offset = Math.min(source.length(), offset + 2);
            } else if (textBlock && source.startsWith("\"\"\"", offset))
                return offset + 3;
            else if (!textBlock && source.charAt(offset) == quote)
                return offset + 1;
            else {
                offset++;
            }
        }

        return offset;
    }
}
