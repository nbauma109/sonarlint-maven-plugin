package io.github.nbauma109.staticanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.work.DisableCachingByDefault;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.commons.api.TextRange;

/**
 * Analyses compiled production Java code using the shared standalone analyzer.
 */
@DisableCachingByDefault(because = "Reports build diagnostics and may invoke an external Eclipse process.")
public abstract class StaticAnalysisTask extends DefaultTask {

    private StaticAnalysisExtension extension;
    private SourceSet sourceSet;
    private Configuration analyzerConfiguration;

    @Internal
    public StaticAnalysisExtension getAnalysisExtension() {
        return extension;
    }

    public void setAnalysisExtension(StaticAnalysisExtension extension) {
        this.extension = extension;
    }

    @Internal
    public SourceSet getSourceSet() {
        return sourceSet;
    }

    public void setSourceSet(SourceSet sourceSet) {
        this.sourceSet = sourceSet;
    }

    @Internal
    public Configuration getAnalyzerConfiguration() {
        return analyzerConfiguration;
    }

    public void setAnalyzerConfiguration(Configuration analyzerConfiguration) {
        this.analyzerConfiguration = analyzerConfiguration;
    }

    @TaskAction
    public void analyse() {
        if (extension.isSkip()) {
            getLogger().lifecycle("Skipping static analysis.");
            return;
        }
        if (extension.getCognitiveComplexityThreshold() < 0) {
            throw new GradleException("Cognitive complexity threshold must not be negative.");
        }
        Path project = getProject().getProjectDir().toPath().toAbsolutePath().normalize();
        Path work = getProject().getLayout().getBuildDirectory().get().getAsFile().toPath()
                .resolve("static-analysis").toAbsolutePath().normalize();
        try {
            List<JavaSourceFile> files = collectSources(project);
            if (files.isEmpty()) {
                getLogger().lifecycle("No Java production sources to analyze.");
                return;
            }
            List<Issue> sonar = runSonar(project, work, files);
            EclipseCleanupRunner.Result eclipse = runEclipse(project, work, sonar);
            report(sonar, eclipse);
            int violations = sonar.size() + eclipse.findings().size();
            if (extension.isFailOnViolation() && violations > 0) {
                throw new GradleException("Static analysis found " + violations + " violation(s).");
            }
        } catch (IOException exception) {
            throw new GradleException("Local static analysis failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GradleException("Eclipse cleanup analysis was interrupted.", exception);
        }
    }

    private List<JavaSourceFile> collectSources(Path project) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        List<JavaSourceFile> result = new ArrayList<>();
        List<File> candidates = sourceSet.getAllJava().getFiles().stream()
                .filter(file -> file.getName().endsWith(".java"))
                .sorted(Comparator.comparing(File::getAbsolutePath))
                .toList();
        for (File candidate : candidates) {
            Path sourceFile = candidate.toPath().toAbsolutePath().normalize();
            if (!sourceFile.startsWith(project)) {
                throw new IOException("A Java source file is outside the project directory: " + sourceFile);
            }
            if (Files.isRegularFile(sourceFile)) {
                result.add(new JavaSourceFile(project, sourceFile, charset));
            }
        }
        return result;
    }

    private List<Issue> runSonar(Path project, Path work, List<JavaSourceFile> files)
            throws IOException {
        RuleCatalog catalog = RuleCatalog.load(analyzerConfiguration.getSingleFile().toPath());
        List<RuleParameters> overrides = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : extension.getRuleParameters().entrySet()) {
            RuleParameters parameter = new RuleParameters();
            parameter.setKey(entry.getKey());
            parameter.setProperties(entry.getValue());
            overrides.add(parameter);
        }
        List<ActiveRule> active = catalog.select(extension.getProfile(), extension.getEnabledRules(),
                extension.getDisabledRules(), extension.getCognitiveComplexityThreshold(), overrides);
        if (active.isEmpty()) {
            getLogger().lifecycle("No local Sonar rules selected.");
            return List.of();
        }
        AnalysisConfiguration configuration = AnalysisConfiguration.builder()
                .setBaseDir(project)
                .addInputFiles(files)
                .putAllExtraProperties(analysisProperties())
                .addActiveRules(active)
                .build();
        getLogger().lifecycle("Activated " + active.size() + " local Java rule(s).");
        return new StandaloneAnalyzer(analyzerConfiguration.getSingleFile().toPath(),
                work.resolve("sonar"), new GradleLogOutput(getLogger())).analyze(configuration);
    }

    private Map<String, String> analysisProperties() throws IOException {
        Map<String, String> properties = new TreeMap<>();
        List<String> binaries = paths(sourceSet.getOutput().getClassesDirs().getFiles(), true);
        if (binaries.isEmpty()) {
            throw new IOException("Compiled Java classes are missing. Run the Gradle classes task first.");
        }
        properties.put("sonar.java.binaries", String.join(",", binaries));
        List<String> libraries = paths(sourceSet.getCompileClasspath().getFiles(), false);
        libraries.removeAll(binaries);
        if (!libraries.isEmpty()) {
            properties.put("sonar.java.libraries", String.join(",", libraries));
        }
        JavaCompile compiler = (JavaCompile) getProject().getTasks().getByName("compileJava");
        String release = Optional.ofNullable(compiler.getOptions().getRelease().getOrNull())
                .map(Object::toString).orElse(compiler.getSourceCompatibility());
        properties.put("sonar.java.source", release);
        return properties;
    }

    private static List<String> paths(Iterable<File> files, boolean directoriesOnly) {
        List<String> result = new ArrayList<>();
        for (File file : files) {
            Path path = file.toPath().toAbsolutePath().normalize();
            if (Files.exists(path) && (!directoriesOnly || Files.isDirectory(path))) {
                result.add(path.toString());
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    private EclipseCleanupRunner.Result runEclipse(Path project, Path work, List<Issue> sonar)
            throws IOException, InterruptedException {
        if (!extension.isEclipseEnabled()) {
            return new EclipseCleanupRunner.Result(List.of(), 0);
        }
        if (extension.getEclipseExecutable() == null || extension.getEclipseCleanupProfile() == null) {
            throw new IOException("Eclipse analysis requires an executable and cleanup profile.");
        }
        if (extension.getEclipseTimeoutSeconds() <= 0) {
            throw new IOException("The Eclipse analysis timeout must be positive.");
        }
        List<String> roots = paths(sourceSet.getJava().getSrcDirs(), true);
        List<String> libraries = paths(sourceSet.getCompileClasspath().getFiles(), false);
        String release = analysisProperties().get("sonar.java.source");
        return EclipseCleanupRunner.run(extension.getEclipseExecutable().toPath(),
                extension.getEclipseCleanupProfile().toPath(), project, work.resolve("eclipse"),
                release, roots, libraries, extension.getEclipseTimeoutSeconds(),
                extension.getEclipseDuplicatePolicy(), sonar, getLogger()::lifecycle);
    }

    private void report(List<Issue> sonar, EclipseCleanupRunner.Result eclipse) {
        List<Issue> sorted = new ArrayList<>(sonar);
        sorted.sort(Comparator.comparing(StaticAnalysisTask::location)
                .thenComparing(Issue::getRuleKey)
                .thenComparing(issue -> Optional.ofNullable(issue.getMessage()).orElse("")));
        for (Issue issue : sorted) {
            getLogger().warn(location(issue) + " [" + issue.getRuleKey() + "] " + issue.getMessage());
        }
        getLogger().lifecycle("Sonar analysis: " + sonar.size() + " violation(s).");
        for (EclipseCleanupRunner.Diagnostic finding : eclipse.findings()) {
            getLogger().warn(finding.file() + ":" + finding.line() + ":" + finding.column()
                    + " [eclipse:" + finding.cleanup() + "] " + finding.message());
        }
        if (extension.isEclipseEnabled()) {
            getLogger().lifecycle("Eclipse cleanup analysis: " + eclipse.findings().size() + " violation(s).");
            getLogger().lifecycle("Duplicate Eclipse findings suppressed: " + eclipse.suppressed() + ".");
        }
    }

    private static String location(Issue issue) {
        String file = Optional.ofNullable(issue.getInputFile())
                .map(ClientInputFile::relativePath).orElse("<project>");
        return Optional.ofNullable(issue.getTextRange()).map(TextRange::getStartLine)
                .map(line -> file + ":" + line).orElse(file);
    }
}
