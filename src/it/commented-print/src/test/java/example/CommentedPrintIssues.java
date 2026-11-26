// System.out.println("license header");
package example;

final class CommentedPrintIssues {

    void first() {
        // A fragment of the expected source follows.
        //  c); // End of call(...
        // Source fragment: System.out.println(i)
        int value = 1;
    }

    void second() {
        // System.err.printf("value")
    }

    String literal() {
        return "// System.out.println(i)";
    }

    String textBlock() {
        return """
                // System.err.println(i)
                """;
    }

    /** Example: System.out.println(i). */
    void documented() {
        // Use a logger to report the value.
        // System.out.println(i) NOSONAR
    }
}
