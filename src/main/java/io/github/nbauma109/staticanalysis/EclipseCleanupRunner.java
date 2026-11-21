package io.github.nbauma109.staticanalysis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.commons.api.TextRange;

/**
 * Runs the standalone Eclipse cleanup application in non-mutating report mode.
 */
final class EclipseCleanupRunner {

    private static final String APPLICATION = "io.github.nbauma109.refactoring.cli.app";

    private EclipseCleanupRunner() {
    }

    record Diagnostic(String file, int line, int column, String cleanup, String message,
            int startOffset, int endOffset) {
    }

    record Result(List<Diagnostic> findings, int suppressed) {
    }

    static Result run(Path executable, Path profile, Path baseDirectory, Path workDirectory,
            String source, List<String> sourceRoots, List<String> classpath, long timeoutSeconds,
            String duplicatePolicy, List<Issue> sonarIssues, Consumer<String> log)
            throws IOException, InterruptedException {
        validateConfiguration(executable, profile, duplicatePolicy);
        Files.createDirectories(workDirectory);
        Path report = workDirectory.resolve("diagnostics.json");
        Path output = workDirectory.resolve("eclipse.log");
        Files.deleteIfExists(report);
        List<String> command = createCommand(executable, profile, baseDirectory, workDirectory,
                report, source, sourceRoots, classpath);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(baseDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile());
        log.accept("Running Eclipse cleanup analysis without modifying sources.");
        Process process = builder.start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            throw new IOException("Eclipse cleanup analysis timed out after " + timeoutSeconds
                    + " seconds. " + readLogTail(output));
        }
        if (process.exitValue() != 0) {
            throw new IOException("Eclipse cleanup process exited with code " + process.exitValue()
                    + ". " + readLogTail(output));
        }
        if (!Files.isRegularFile(report)) {
            throw new IOException("The Eclipse cleanup executable did not produce a diagnostic report.");
        }
        List<Diagnostic> findings = readReport(report, baseDirectory, sourceRoots);
        List<Diagnostic> retained = new ArrayList<>();
        int suppressed = 0;
        for (Diagnostic finding : findings) {
            if ("prefer-sonar".equals(duplicatePolicy)
                    && overlapsUnusedImport(finding, sonarIssues, baseDirectory)) {
                suppressed++;
            } else {
                retained.add(finding);
            }
        }
        retained.sort(Comparator.comparing(Diagnostic::file)
                .thenComparingInt(Diagnostic::line)
                .thenComparingInt(Diagnostic::column)
                .thenComparing(Diagnostic::cleanup)
                .thenComparing(Diagnostic::message));
        return new Result(List.copyOf(retained), suppressed);
    }

    private static void validateConfiguration(Path executable, Path profile, String duplicatePolicy)
            throws IOException {
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IOException("The Eclipse executable is missing or not executable: " + executable);
        }
        if (!Files.isRegularFile(profile)) {
            throw new IOException("The Eclipse cleanup profile does not exist: " + profile);
        }
        if (!"prefer-sonar".equals(duplicatePolicy) && !"none".equals(duplicatePolicy)) {
            throw new IOException("Unsupported Eclipse duplicate policy: " + duplicatePolicy);
        }
    }

    private static List<String> createCommand(Path executable, Path profile, Path project,
            Path work, Path report, String source, List<String> sourceRoots, List<String> classpath)
            throws IOException {
        List<String> arguments = new ArrayList<>(List.of(
                executable.toAbsolutePath().toString(),
                "-nosplash", "-consoleLog",
                "-data", work.resolve("workspace").toString(),
                "-application", APPLICATION,
                "--source", source,
                "--profile", profile.toAbsolutePath().toString(),
                project.toString(),
                "--report", report.toString()));
        int enabledRoots = 0;
        for (String sourceRoot : sourceRoots) {
            Path directory = project.resolve(sourceRoot).toAbsolutePath().normalize();
            if (Files.isDirectory(directory)) {
                arguments.add("--source-root");
                arguments.add(directory.toString());
                enabledRoots++;
            }
        }
        if (enabledRoots == 0) {
            throw new IOException("No existing production source roots for Eclipse analysis.");
        }
        if (!classpath.isEmpty()) {
            arguments.add("--classpath");
            arguments.add(String.join(File.pathSeparator, classpath));
        }
        return arguments;
    }

    private static String readLogTail(Path logFile) throws IOException {
        Deque<String> tail = new ArrayDeque<>();
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (tail.size() == 15) {
                    tail.removeFirst();
                }
                tail.addLast(line);
            }
        }
        return String.join(System.lineSeparator(), tail);
    }

    private static List<Diagnostic> readReport(Path report, Path project,
            List<String> sourceRoots) throws IOException {
        try (Reader reader = Files.newBufferedReader(report, StandardCharsets.UTF_8)) {
            JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
            if (document.get("formatVersion").getAsInt() != 1) {
                throw new IOException("Unsupported Eclipse cleanup report format.");
            }
            JsonArray issues = document.getAsJsonArray("issues");
            List<Diagnostic> result = new ArrayList<>();
            for (JsonElement item : issues) {
                result.add(readDiagnostic(item.getAsJsonObject(), project, sourceRoots));
            }
            return result;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid Eclipse cleanup diagnostic report.", exception);
        }
    }

    private static Diagnostic readDiagnostic(JsonObject json, Path project,
            List<String> sourceRoots) throws IOException {
        String name = json.get("file").getAsString();
        Path file = project.resolve(name).toAbsolutePath().normalize();
        if (!file.startsWith(project) || !Files.isRegularFile(file)
                || !isProductionSource(file, project, sourceRoots)) {
            throw new IOException("Eclipse cleanup reported a file outside production sources: " + name);
        }
        int line = json.get("line").getAsInt();
        int column = json.get("column").getAsInt();
        int start = json.get("startOffset").getAsInt();
        int end = json.get("endOffset").getAsInt();
        String cleanup = json.get("cleanup").getAsString();
        String message = json.get("message").getAsString();
        if (line < 1 || column < 1 || start < 0 || end < start
                || cleanup.isBlank() || message.isBlank()) {
            throw new IOException("Invalid Eclipse cleanup diagnostic in " + name);
        }
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (end > source.length()) {
            throw new IOException("Eclipse cleanup diagnostic exceeds source length: " + name);
        }
        return new Diagnostic(project.relativize(file).toString().replace('\\', '/'),
                line, column, cleanup, message, start, end);
    }

    private static boolean isProductionSource(Path file, Path project, List<String> sourceRoots) {
        for (String root : sourceRoots) {
            Path directory = project.resolve(root).toAbsolutePath().normalize();
            if (file.startsWith(directory)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlapsUnusedImport(Diagnostic finding, List<Issue> sonarIssues,
            Path project) throws IOException {
        Path file = project.resolve(finding.file());
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (finding.line() > lines.size()
                || !lines.get(finding.line() - 1).strip().startsWith("import ")
                || lines.get(finding.line() - 1).strip().startsWith("import module ")) {
            return false;
        }
        for (Issue issue : sonarIssues) {
            String reportedFile = Optional.ofNullable(issue.getInputFile())
                    .map(ClientInputFile::relativePath).orElse("");
            int reportedLine = Optional.ofNullable(issue.getTextRange())
                    .map(TextRange::getStartLine).orElse(-1);
            if ("java:S1128".equals(issue.getRuleKey())
                    && finding.file().equals(reportedFile) && finding.line() == reportedLine) {
                return true;
            }
        }
        return false;
    }
}
