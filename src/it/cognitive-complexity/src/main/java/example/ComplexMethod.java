package example;

public final class ComplexMethod {

    public boolean allEnabled(boolean first, boolean second, boolean third,
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
}
