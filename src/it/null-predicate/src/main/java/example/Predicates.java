package example;

public final class Predicates {

    private Predicates() {
    }

    public static boolean missing(String value) {
        return value == null || value.isEmpty();
    }

    public static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    public static boolean unrelated(String value) {
        return value != null || System.nanoTime() > 0;
    }

    public static boolean presentArguments(String... values) {
        return values != null;
    }
}
