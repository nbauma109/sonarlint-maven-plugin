package example;

public final class ComplementarySystemProperties {

    public String fileSeparator() {
        return System.getProperty("file.separator");
    }

    public String pathSeparator() {
        return System.getProperty("path.separator");
    }

    public String lineSeparator() {
        return System.getProperty("line.separator");
    }

    public String fileEncoding() {
        return System.getProperty("file.encoding");
    }

    public boolean featureEnabled() {
        return Boolean.parseBoolean(System.getProperty("com.example.enabled"));
    }
}
