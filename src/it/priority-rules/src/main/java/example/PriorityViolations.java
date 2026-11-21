package example;

import java.util.List;

public final class PriorityViolations {

    public void BadName() {
        int unused = 1;
        consume("repeated literal");
        consume("repeated literal");
        consume("repeated literal");
    }

    public int tooManyParameters(int first, int second, int third, int fourth,
            int fifth, int sixth, int seventh, int eighth) {
        return first + second + third + fourth + fifth + sixth + seventh + eighth;
    }

    public void genericException() {
        throw new RuntimeException("generic exception");
    }

    public boolean nestedConditions(boolean first, boolean second, boolean third,
            boolean fourth, boolean fifth, boolean sixth) {
        if (first) {
            if (second) {
                if (third) {
                    if (fourth) {
                        if (fifth) {
                            if (sixth) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void consume(String value) {
        System.out.println(value);
    }
}
