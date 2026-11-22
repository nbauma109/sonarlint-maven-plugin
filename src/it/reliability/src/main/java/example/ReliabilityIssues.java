package example;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class ReliabilityIssues {

    public int leakedStream() throws IOException {
        InputStream stream = new FileInputStream("input.txt");
        return stream.read();
    }

    public int nullDereference() {
        String value = null;
        return value.length();
    }

    public String uncheckedOptional() {
        Optional<String> value = Optional.empty();
        return value.get();
    }

    public int guardedValue(String value) {
        if (value == null) {
            return 0;
        }
        return value.length();
    }

    public String safeOptional(Optional<String> value) {
        return value.orElse("fallback");
    }

    public int closedStream() throws IOException {
        try (InputStream stream = new FileInputStream("input.txt")) {
            return stream.read();
        }
    }
}
