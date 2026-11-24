package example;

public final class NativeVersionIssues {

    private NativeVersionIssues() {
    }

    public static int length(Object value) {
        if (value instanceof String) {
            String text = (String) value;
            return text.length();
        }
        return 0;
    }
}
