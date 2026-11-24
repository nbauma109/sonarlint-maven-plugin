package example;

public final class LegacyCatch {

    private LegacyCatch() {
    }

    public static int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            return 0;
        }
    }
}
