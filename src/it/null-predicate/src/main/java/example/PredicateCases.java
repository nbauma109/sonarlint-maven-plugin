package example;

public final class PredicateCases {

    private PredicateCases() {
    }

    private static String nullable(boolean available) {
        if (available) {
            return "text";
        }
        return null;
    }

    public static int guarded(boolean available) {
        String value = nullable(available);
        if (!Predicates.missing(value)) {
            return value.length();
        }
        return 0;
    }

    public static int positiveGuard(boolean available) {
        String value = nullable(available);
        if (Predicates.present(value)) {
            return value.length();
        }
        return 0;
    }

    public static int elseGuard(boolean available) {
        String value = nullable(available);
        if (Predicates.missing(value)) {
            return 0;
        } else {
            return value.length();
        }
    }

    public static int unsafe(boolean available) {
        String value = nullable(available);
        return value.length();
    }

    public static int wrongBranch(boolean available) {
        String value = nullable(available);
        if (Predicates.missing(value)) {
            return value.length();
        }
        return 0;
    }

    public static int reassigned(boolean available) {
        String value = nullable(available);
        if (!Predicates.missing(value)) {
            value = null;
            return value.length();
        }
        return 0;
    }

    public static int unknownContract(boolean available) {
        String value = nullable(available);
        if (Predicates.unrelated(value)) {
            return value.length();
        }
        return 0;
    }

    public static int varargsGuard(boolean available) {
        String value = nullable(available);
        if (Predicates.presentArguments(value)) {
            return value.length();
        }
        return 0;
    }
}
