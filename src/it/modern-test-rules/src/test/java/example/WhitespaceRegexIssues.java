package example;

public final class WhitespaceRegexIssues {

    private WhitespaceRegexIssues() {
    }

    public static boolean sameNormalizedText(String first, String second) {
        return first.replaceAll("[\\t ]*\\r?\\n", "\n").equals(second.replaceAll("[\\t ]*\\r?\\n", "\n"));
    }

    public static String repeatedClass(String value) {
        return value.replaceFirst("[ \t]+\\n", "\n");
    }

    public static String repeatedEscape(String value) {
        return value.replaceAll("[\\h]*\\r\\n", "\n");
    }

    public static String anchored(String value) {
        return value.replaceAll("^[\\t ]*\\r?\\n", "\n");
    }

    public static String guarded(String value) {
        return value.replaceAll("(?<![\\t ])[\\t ]*\\r?\\n", "\n");
    }

    public static String optional(String value) {
        return value.replaceAll("[\\t ]?\\r?\\n", "\n");
    }

    public static String noSuffix(String value) {
        return value.replaceAll("[\\t ]*", "");
    }
}
