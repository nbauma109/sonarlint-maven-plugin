package io.github.nbauma109.staticanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.commons.api.TextRange;

/**
 * Performs local Java static analysis after compilation.
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public final class CheckMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${plugin.artifacts}", readonly = true, required = true)
    private List<Artifact> pluginArtifacts;

    /** Skip local analysis. */
    @Parameter(property = "staticAnalysis.skip", defaultValue = "false")
    private boolean skip;

    /** Fail the build when violations are found. */
    @Parameter(property = "staticAnalysis.failOnViolation", defaultValue = "false")
    private boolean failOnViolation;

    /** Maximum cognitive complexity allowed for a method. */
    @Parameter(property = "staticAnalysis.cognitiveComplexityThreshold", defaultValue = "15")
    private int cognitiveComplexityThreshold;

    /** Default rule profile: priority, complexity, sonar-way, reliability or none. */
    @Parameter(property = "staticAnalysis.profile", defaultValue = "priority")
    private String profile;

    /** Comma-separated additional Java rule keys. */
    @Parameter(property = "staticAnalysis.enabledRules", defaultValue = "")
    private String enabledRules;

    /** Comma-separated Java rule keys to disable, taking precedence over activation. */
    @Parameter(property = "staticAnalysis.disabledRules", defaultValue = "")
    private String disabledRules;

    /** Explicit per-rule parameter overrides using exact upstream parameter names. */
    @Parameter
    private List<RuleParameters> ruleParameters;

    /** Enable the separately installed Eclipse cleanup analyzer. */
    @Parameter(property = "staticAnalysis.eclipseEnabled", defaultValue = "false")
    private boolean eclipseEnabled;

    /** Executable or wrapper that launches the Eclipse application. */
    @Parameter(property = "staticAnalysis.eclipseExecutable")
    private File eclipseExecutable;

    /** Existing exported Eclipse cleanup profile. */
    @Parameter(property = "staticAnalysis.eclipseCleanupProfile")
    private File eclipseCleanupProfile;

    /** Maximum runtime for the external Eclipse cleanup process. */
    @Parameter(property = "staticAnalysis.eclipseTimeoutSeconds", defaultValue = "600")
    private long eclipseTimeoutSeconds;

    /** Duplicate handling: prefer-sonar or none. */
    @Parameter(property = "staticAnalysis.eclipseDuplicatePolicy", defaultValue = "prefer-sonar")
    private String eclipseDuplicatePolicy;

    /** Source encoding; defaults to UTF-8 when not specified by the project. */
    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    /** Java source release used for analysis. */
    @Parameter(defaultValue = "${maven.compiler.release}")
    private String release;

    /** Java source level if the compiler release is not specified. */
    @Parameter(defaultValue = "${maven.compiler.source}")
    private String source;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || "pom".equals(project.getPackaging())) {
            getLog().info("Skipping local Sonar analysis.");
            return;
        }
        if (cognitiveComplexityThreshold < 0) {
            throw new MojoExecutionException("Cognitive complexity threshold must not be negative.");
        }
        List<Issue> issues = analyzeProject();
        EclipseCleanupRunner.Result eclipse = analyzeEclipse(issues);
        reportIssues(issues);
        reportEclipseIssues(eclipse);
        int total = issues.size() + eclipse.findings().size();
        if (failOnViolation && total > 0) {
            if (eclipseEnabled) {
                throw new MojoFailureException("Static analysis found " + total + " violation(s).");
            }
            throw new MojoFailureException("Sonar found " + total + " violation(s).");
        }
    }


    private EclipseCleanupRunner.Result analyzeEclipse(List<Issue> sonarIssues)
            throws MojoExecutionException {
        if (!eclipseEnabled) {
            return new EclipseCleanupRunner.Result(List.of(), 0);
        }
        if (eclipseExecutable == null || eclipseCleanupProfile == null) {
            throw new MojoExecutionException("Eclipse analysis requires eclipseExecutable and eclipseCleanupProfile.");
        }
        if (eclipseTimeoutSeconds <= 0) {
            throw new MojoExecutionException("The Eclipse analysis timeout must be positive.");
        }
        try {
            Path baseDirectory = project.getBasedir().toPath().toAbsolutePath().normalize();
            Path workDirectory = Path.of(project.getBuild().getDirectory(), "static-analysis", "eclipse")
                    .toAbsolutePath().normalize();
            String javaSource = Optional.ofNullable(release).filter(value -> !value.isBlank())
                    .or(() -> Optional.ofNullable(source).filter(value -> !value.isBlank()))
                    .orElse("17");
            return EclipseCleanupRunner.run(eclipseExecutable.toPath(), eclipseCleanupProfile.toPath(),
                    baseDirectory, workDirectory, javaSource, project.getCompileSourceRoots(),
                    project.getCompileClasspathElements(), eclipseTimeoutSeconds,
                    eclipseDuplicatePolicy, sonarIssues, getLog()::info);
        } catch (IOException | DependencyResolutionRequiredException | RuntimeException exception) {
            throw new MojoExecutionException("Eclipse cleanup analysis failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Eclipse cleanup analysis was interrupted.", exception);
        }
    }

    private void reportEclipseIssues(EclipseCleanupRunner.Result result) {
        for (EclipseCleanupRunner.Diagnostic finding : result.findings()) {
            getLog().warn(finding.file() + ":" + finding.line() + ":" + finding.column()
                    + " [eclipse:" + finding.cleanup() + "] " + finding.message());
        }
        if (eclipseEnabled) {
            getLog().info("Eclipse cleanup analysis: " + result.findings().size() + " violation(s).");
            getLog().info("Duplicate Eclipse findings suppressed: " + result.suppressed() + ".");
        }
    }

    private List<Issue> analyzeProject() throws MojoExecutionException {
        try {
            Path baseDirectory = project.getBasedir().toPath().toAbsolutePath().normalize();
            List<JavaSourceFile> files = collectSources(baseDirectory);
            if (files.isEmpty()) {
                getLog().info("No Java sources to analyze.");
                return List.of();
            }
            Path binaries = Path.of(project.getBuild().getOutputDirectory()).toAbsolutePath().normalize();
            if (!Files.isDirectory(binaries)) {
                throw new MojoExecutionException("Compiled classes are missing. Run Maven compile first.");
            }
            Path workDirectory = Path.of(project.getBuild().getDirectory(), "static-analysis").toAbsolutePath();
            Path analyzer = findAnalyzer();
            boolean useSymbolicAnalyzer = "reliability".equals(profile);
            Path symbolicAnalyzer = null;
            RuleCatalog catalog;
            if (useSymbolicAnalyzer) {
                symbolicAnalyzer = findAnalyzer("sonar-java-symbolic-execution-plugin");
                catalog = RuleCatalog.load(analyzer, symbolicAnalyzer);
            } else {
                catalog = RuleCatalog.load(analyzer);
            }
            var activeRules = catalog.select(profile, enabledRules, disabledRules,
                    cognitiveComplexityThreshold, Optional.ofNullable(ruleParameters).orElse(List.of()));
            if (activeRules.isEmpty()) {
                getLog().info("No local Sonar rules selected.");
                return List.of();
            }
            getLog().info("Activated " + activeRules.size() + " local Java rule(s).");
            AnalysisConfiguration configuration = AnalysisConfiguration.builder()
                    .setBaseDir(baseDirectory)
                    .addInputFiles(files)
                    .putAllExtraProperties(analysisProperties(binaries))
                    .addActiveRules(activeRules)
                    .build();
            getLog().info("Analyzing " + files.size() + " Java source file(s) locally.");
            if (useSymbolicAnalyzer) {
                return new StandaloneAnalyzer(analyzer, symbolicAnalyzer, workDirectory,
                        new MavenLogOutput(getLog())).analyze(configuration);
            }
            return new StandaloneAnalyzer(analyzer, workDirectory,
                    new MavenLogOutput(getLog())).analyze(configuration);
        } catch (IOException | DependencyResolutionRequiredException | RuntimeException exception) {
            throw new MojoExecutionException("Local Sonar analysis could not complete: " + exception.getMessage(), exception);
        }
    }

    private List<JavaSourceFile> collectSources(Path baseDirectory) throws IOException {
        Charset charset = Charset.forName(Optional.ofNullable(encoding)
                .filter(value -> !value.isBlank()).orElse("UTF-8"));
        Set<Path> paths = new LinkedHashSet<>();
        for (String root : project.getCompileSourceRoots()) {
            Path directory = baseDirectory.resolve(root).normalize();
            if (Files.isDirectory(directory)) {
                try (Stream<Path> entries = Files.walk(directory)) {
                    paths.addAll(entries.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java"))
                            .sorted().toList());
                }
            }
        }
        List<JavaSourceFile> files = new ArrayList<>();
        for (Path path : paths) {
            files.add(new JavaSourceFile(baseDirectory, path, charset));
        }
        return files;
    }

    private Map<String, String> analysisProperties(Path binaries)
            throws DependencyResolutionRequiredException {
        Map<String, String> properties = new TreeMap<>();
        properties.put("sonar.java.binaries", binaries.toString());
        List<String> libraries = new ArrayList<>();
        for (String element : project.getCompileClasspathElements()) {
            Path path = Path.of(element).toAbsolutePath().normalize();
            if (!path.equals(binaries) && Files.exists(path)) {
                libraries.add(path.toString());
            }
        }
        if (!libraries.isEmpty()) {
            properties.put("sonar.java.libraries", String.join(",", libraries));
        }
        Optional.ofNullable(release).filter(value -> !value.isBlank())
                .or(() -> Optional.ofNullable(source).filter(value -> !value.isBlank()))
                .ifPresent(value -> properties.put("sonar.java.source", value));
        return properties;
    }

    private Path findAnalyzer() throws MojoExecutionException {
        return findAnalyzer("sonar-java-plugin");
    }

    private Path findAnalyzer(String artifactId) throws MojoExecutionException {
        for (Artifact artifact : pluginArtifacts) {
            if ("org.sonarsource.java".equals(artifact.getGroupId())
                    && artifactId.equals(artifact.getArtifactId())
                    && artifact.getFile() != null && artifact.getFile().isFile()) {
                return artifact.getFile().toPath();
            }
        }
        throw new MojoExecutionException("The Sonar analyzer dependency was not resolved: " + artifactId);
    }

    private void reportIssues(List<Issue> issues) {
        List<Issue> sorted = new ArrayList<>(issues);
        sorted.sort(Comparator.comparing(CheckMojo::location)
                .thenComparing(Issue::getRuleKey)
                .thenComparing(issue -> Optional.ofNullable(issue.getMessage()).orElse("")));
        for (Issue issue : sorted) {
            getLog().warn(location(issue) + " [" + issue.getRuleKey() + "] " + issue.getMessage());
        }
        getLog().info("Sonar analysis: " + issues.size() + " violation(s).");
    }

    private static String location(Issue issue) {
        String file = Optional.ofNullable(issue.getInputFile())
                .map(ClientInputFile::relativePath).orElse("<project>");
        return Optional.ofNullable(issue.getTextRange()).map(TextRange::getStartLine)
                .map(line -> file + ":" + line).orElse(file);
    }
}
