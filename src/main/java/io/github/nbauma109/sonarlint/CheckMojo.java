package io.github.nbauma109.sonarlint;

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
    @Parameter(property = "sonarlint.skip", defaultValue = "false")
    private boolean skip;

    /** Fail the build when violations are found. */
    @Parameter(property = "sonarlint.failOnViolation", defaultValue = "false")
    private boolean failOnViolation;

    /** Maximum cognitive complexity allowed for a method. */
    @Parameter(property = "sonarlint.cognitiveComplexityThreshold", defaultValue = "15")
    private int cognitiveComplexityThreshold;

    /** Default rule profile: priority, complexity, sonar-way or none. */
    @Parameter(property = "sonarlint.profile", defaultValue = "priority")
    private String profile;

    /** Comma-separated additional Java rule keys. */
    @Parameter(property = "sonarlint.enabledRules", defaultValue = "")
    private String enabledRules;

    /** Comma-separated Java rule keys to disable, taking precedence over activation. */
    @Parameter(property = "sonarlint.disabledRules", defaultValue = "")
    private String disabledRules;

    /** Explicit per-rule parameter overrides using exact upstream parameter names. */
    @Parameter
    private List<RuleParameters> ruleParameters;

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
        reportIssues(issues);
        if (failOnViolation && !issues.isEmpty()) {
            throw new MojoFailureException("Sonar found " + issues.size() + " violation(s).");
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
            Path workDirectory = Path.of(project.getBuild().getDirectory(), "sonarlint").toAbsolutePath();
            Path analyzer = findAnalyzer();
            RuleCatalog catalog = RuleCatalog.load(analyzer);
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
            return new StandaloneAnalyzer(analyzer, workDirectory, getLog()).analyze(configuration);
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
        for (Artifact artifact : pluginArtifacts) {
            if ("org.sonarsource.java".equals(artifact.getGroupId())
                    && "sonar-java-plugin".equals(artifact.getArtifactId())
                    && artifact.getFile() != null && artifact.getFile().isFile()) {
                return artifact.getFile().toPath();
            }
        }
        throw new MojoExecutionException("The SonarJava analyzer dependency was not resolved.");
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
