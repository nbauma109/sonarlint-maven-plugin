package example;

final class BlankStringIssues {

    boolean blank(String value) {
        return value != null && value.trim().length() > 0;
    }

    boolean nonblank(String value) {
        return value.length() > 0;
    }

    boolean longer(String value) {
        return value.trim().length() > 1;
    }

    boolean unrelated(CustomString value) {
        return value.trim().length() > 0;
    }

    static final class CustomString {
        CustomString trim() {
            return this;
        }

        int length() {
            return 0;
        }
    }
}
