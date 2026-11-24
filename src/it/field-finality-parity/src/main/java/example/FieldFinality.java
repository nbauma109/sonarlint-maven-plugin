package example;

public final class FieldFinality {

    private String initialized = "value";
    private int constructed;
    private String changed = "original";
    private final int alreadyFinal = 3;

    public FieldFinality(int value) {
        constructed = value;
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

    public int getAlreadyFinal() {
        return alreadyFinal;
    }
}
