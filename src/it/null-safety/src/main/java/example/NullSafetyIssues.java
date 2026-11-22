package example;

import java.util.Objects;

public final class NullSafetyIssues {

    private NullSafetyIssues() {
    }

    private static String normalizeNullable(String optional) {
        if (Objects.isNull(optional)) {
            return "";
        }
        return optional;
    }

    public static int unsafe(String optional) {
        String normalized = normalizeNullable(optional);
        return optional.length() + normalized.length();
    }

    public static int safe(String optional) {
        String normalized = normalizeNullable(optional);
        Objects.requireNonNull(optional, "optional");
        return optional.length() + normalized.length();
    }
}
