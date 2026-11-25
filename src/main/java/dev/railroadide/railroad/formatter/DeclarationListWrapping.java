package dev.railroadide.railroad.formatter;

/**
 * Removes existing wraps at declaration list boundaries so Eclipse can lay out the list at its configured width.
 */
public final class DeclarationListWrapping {
    private boolean inHeader;
    private boolean inList;
    private int parentheses;
    private int typeArguments;

    private DeclarationListWrapping() {
    }

    public static String prepare(String source) {
        var header = new DeclarationListWrapping();
        var result = new StringBuilder(source.length());
        int copyFrom = 0;
        boolean afterSeparator = false;
        boolean afterDot = false;
        boolean afterComment = false;
        boolean enabled = true;
        for (int offset = 0; offset < source.length();) {
            int gap = offset;
            while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) {
                offset++;
            }
            if (offset == source.length())
                break;

            int start = offset;
            char current = source.charAt(start);
            if (source.startsWith("//", start) || source.startsWith("/*", start)) {
                offset = SourceFormatting.endOfComment(source, start);
                enabled = SourceFormatting.formatterEnabled(source.substring(start, offset), enabled);
                afterComment = true;
                afterSeparator = false;
                continue;
            }

            boolean clause = false;
            boolean separator = false;
            if (current == '"' || current == '\'') {
                offset = ClosingBraceSpacing.endOfLiteral(source, start);
            } else if (Character.isJavaIdentifierStart(source.codePointAt(start))) {
                do {
                    offset += Character.charCount(source.codePointAt(offset));
                } while (offset < source.length() && Character.isJavaIdentifierPart(source.codePointAt(offset)));
                clause = header.acceptWord(source.substring(start, offset), afterDot);
            } else {
                offset++;
                separator = header.acceptPunctuation(current);
            }

            if (enabled && !afterComment && (clause || afterSeparator) && source.indexOf('\n', gap, start) >= 0) {
                result.append(source, copyFrom, gap).append(' ');
                copyFrom = start;
            }
            afterSeparator = clause || separator;
            afterComment = false;
            afterDot = current == '.';
        }
        return copyFrom == 0 ? source : result.append(source, copyFrom, source.length()).toString();
    }

    private boolean acceptWord(String word, boolean afterDot) {
        boolean clause = switch (word) {
            case "implements", "throws" -> true;
            case "extends", "permits" -> inHeader && !afterDot && parentheses == 0 && typeArguments == 0;
            default -> false;
        };
        if (clause) {
            inHeader = true;
            inList = true;
        } else if (!afterDot && (word.equals("class") || word.equals("interface"))) {
            inHeader = true;
        }
        return clause;
    }

    private boolean acceptPunctuation(char symbol) {
        if (!inHeader)
            return false;

        if (symbol == '(') {
            parentheses++;
        } else if (symbol == ')') {
            parentheses--;
        } else if (parentheses == 0) {
            switch (symbol) {
                case '<' -> typeArguments++;
                case '>' -> typeArguments--;
                case ',' -> {
                    return inList && typeArguments == 0;
                }
                case '{', '}', ';' -> {
                    inHeader = false;
                    inList = false;
                    typeArguments = 0;
                }
            }
        }
        return false;
    }
}
