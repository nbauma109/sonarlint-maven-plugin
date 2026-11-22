package example;

public final class ModernProductionIssues {

    private String initialized = "value";
    private int constructed;
    private String changed = "original";

    public ModernProductionIssues() {
        this(1);
    }

    public ModernProductionIssues(int value) {
        this.constructed = value;
    }

    public String getInitialized() {
        return initialized;
    }

    public int getConstructed() {
        return constructed;
    }

    public void change(String value) {
        changed = value;
    }

    public String getChanged() {
        return changed;
    }

    private static String recordMessage() {
        return "hello";
    }

    record Nested() {
        String message() {
            return recordMessage();
        }
    }

    interface Settings {
        final class Constants {
        }

        abstract class AbstractHelper {
        }
    }
}
