package example;

public class HandwrittenTest {
    public String normalize(String value) {
        return value.replaceAll("[\\t ]*\\r?\\n", "\n");
    }
}
