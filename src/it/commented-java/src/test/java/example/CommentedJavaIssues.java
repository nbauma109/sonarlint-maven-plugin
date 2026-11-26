// service.reset()
package example;

final class CommentedJavaIssues {

    void first() {
        // The expected source contains the next statement.
        // Source fragment: System.out.println(i)
        // service.reset()
        int value = 1;
    }

    void assignment() {
        // counter = 2
    }

    void update() {
        // counter++
    }

    void terminated() {
        // performWork();
    }

    void prose() {
        // Call reset() before starting.
        // reset()
        // A value equals x = y in this description.
        // See https://example.org/path
        // The expression is a + b.
    }

    String literal() {
        return "// service.reset()";
    }

    String textBlock() {
        return """
                // service.reset()
                """;
    }

    /** Example: service.reset(). */
    void documented() {
        // service.reset() NOSONAR
    }

    @SuppressWarnings("java:S125")
    void suppressed() {
        // service.reset()
    }

    void inline() {
        int value = 1; // service.reset()
    }
}
