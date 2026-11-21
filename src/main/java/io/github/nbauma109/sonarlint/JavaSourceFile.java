package io.github.nbauma109.sonarlint;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

final class JavaSourceFile implements ClientInputFile {

    private final Path path;
    private final String relativePath;
    private final Charset charset;

    JavaSourceFile(Path baseDirectory, Path path, Charset charset) {
        this.path = path;
        this.relativePath = baseDirectory.relativize(path).toString().replace('\\', '/');
        this.charset = charset;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getPath() {
        return path.toString();
    }

    @Override
    public boolean isTest() {
        return false;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    @Override
    public SonarLanguage language() {
        return SonarLanguage.JAVA;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <G> G getClientObject() {
        return (G) path;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public String contents() throws IOException {
        return Files.readString(path, charset);
    }

    @Override
    public String relativePath() {
        return relativePath;
    }

    @Override
    public URI uri() {
        return path.toUri();
    }
}
