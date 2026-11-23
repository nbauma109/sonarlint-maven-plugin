package example;

public final class EmbeddedJdtFinality {

    private String eligible = "value";
    private String mutable = "initial";

    public String value() {
        return eligible;
    }

    public void setMutable(String value) {
        mutable = value;
    }

    public String mutable() {
        return mutable;
    }
}
