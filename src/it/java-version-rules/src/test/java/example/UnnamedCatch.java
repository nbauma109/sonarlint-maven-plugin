package example;

public class UnnamedCatch {
    public int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException _) {
            return 0;
        }
    }
}
