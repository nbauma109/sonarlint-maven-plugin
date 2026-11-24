package example;

public final class DelimiterRegexIssues {
    public String replaceOpening(String text) {
        return text.replaceAll("\\s*\\{\\s*", "{");
    }

    public String replaceClosing(String text) {
        return text.replaceAll("\\s*\\}\\s*", "}");
    }

    public String anchored(String text) {
        return text.replaceAll("^\\s*\\{\\s*", "{");
    }

    public String optionalDelimiter(String text) {
        return text.replaceAll("\\s*\\{?\\s*", "{");
    }

    public String withoutRequiredDelimiter(String text) {
        return text.replaceAll("\\s*", "");
    }
}
