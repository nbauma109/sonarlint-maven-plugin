package example;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

public final class SymbolicFailures {

    public int nullDereference() {
        Object value = null;
        return value.hashCode();
    }

    public int resourceLeak(String filename) throws IOException {
        FileInputStream input = new FileInputStream(filename);
        return input.read();
    }

    public String unsafeOptional() {
        Optional<String> value = Optional.empty();
        return value.get();
    }

    public int safeValue(String filename) throws IOException {
        try (FileInputStream input = new FileInputStream(filename)) {
            return input.read();
        }
    }
}
