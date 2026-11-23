package example;

public final class ModernTestIssues {

    private ModernTestIssues() {
    }

    public static String normalize(String value) {
        return value.replaceAll(" *\\r?\\n", "\\n")
                .replaceAll(" *\\r?\\n", "\\n");
    }

    public static String safelyNormalize(String value) {
        return value.replaceAll("^ *\\r?\\n", "\\n");
    }

    public static boolean ignoreFailure() {
        try {
            throw new IllegalStateException("expected");
        } catch (IllegalStateException failure) {
            return false;
        }
    }
}
