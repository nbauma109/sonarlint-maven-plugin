package example;

public final class ModernCatch {

    private ModernCatch() {
    }

    public static int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            return 0;
        }
    }
}
