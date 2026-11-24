package example;

public class HandwrittenMain {
    private String message = "hello";

    public void print() {
        System.out.println(message);
    }
    public GeneratedMain generated() {
        return new GeneratedMain();
    }
}
