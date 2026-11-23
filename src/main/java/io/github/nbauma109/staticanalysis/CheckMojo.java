// Additional GPLv3 section 7 permission for Eclipse JDT linking:
// See ECLIPSE-JDT-LINKING-EXCEPTION.md in the distribution.
package io.github.nbauma109.staticanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
@Mojo(name = "check", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES,
        requiresDependencyResolution = ResolutionScope.TEST)
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

    /** Default rule profile: priority, complexity, sonar-way, sonar-way-standard, reliability or none. */
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

    /** Enable bundled JDT cleanup analysis or an explicitly configured external Eclipse runner. */
    @Parameter(property = "staticAnalysis.eclipseEnabled", defaultValue = "false")
    private boolean eclipseEnabled;

    /** Optional executable or wrapper for the legacy external Eclipse application. */
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
        ProjectAnalysis analysis = analyzeProject();
        EclipseCleanupRunner.Result eclipse = analyzeEclipse(analysis.sonarIssues());
        List<SupplementalAnalyzer.Finding> supplementalIssues = preferEclipseFieldFinality(
                analysis.additionalIssues(), eclipse.findings());
        reportIssues(analysis.sonarIssues());
        reportSupplementalIssues(supplementalIssues);
        reportEclipseIssues(eclipse);
        int total = analysis.sonarIssues().size() + supplementalIssues.size()
                + eclipse.findings().size();
        if (failOnViolation && total > 0) {
            if (eclipseEnabled) {
                throw new MojoFailureException("Static analysis found " + total + " violation(s).");
            }
            throw new MojoFailureException("Sonar found " + total + " violation(s).");
        }
    }


    private List<SupplementalAnalyzer.Finding> preferEclipseFieldFinality(
            List<SupplementalAnalyzer.Finding> supplemental,
            List<EclipseCleanupRunner.Diagnostic> eclipse) {
        if (supplemental.isEmpty() || eclipse.isEmpty()) {
            return supplemental;
        }
        Set<String> eclipseFields = new LinkedHashSet<>();
        for (EclipseCleanupRunner.Diagnostic finding : eclipse) {
            if (isPrivateFieldFinality(finding)) {
                eclipseFields.add(finding.file() + ":" + finding.line());
            }
        }
        List<SupplementalAnalyzer.Finding> retained = new ArrayList<>();
        for (SupplementalAnalyzer.Finding finding : supplemental) {
            if ("java:S9438".equals(finding.rule())
                    && eclipseFields.contains(finding.file() + ":" + finding.line())) {
                continue;
            }
            retained.add(finding);
        }
        int replaced = supplemental.size() - retained.size();
        if (replaced > 0) {
            getLog().info("Equivalent private-field findings provided by Eclipse: "
                    + replaced + ".");
        }
        return List.copyOf(retained);
    }

    private static boolean isPrivateFieldFinality(EclipseCleanupRunner.Diagnostic finding) {
        return finding.startOffset() == finding.endOffset()
                && ("VariableDeclarationCleanUpCore".equals(finding.cleanup())
                || "cleanup.make_private_fields_final".equals(finding.cleanup()));
    }

    private EclipseCleanupRunner.Result analyzeEclipse(List<Issue> sonarIssues)
            throws MojoExecutionException {
        if (!eclipseEnabled) {
            return new EclipseCleanupRunner.Result(List.of(), 0);
        }
        if (eclipseExecutable == null) {
            try {
                Path baseDirectory = project.getBasedir().toPath().toAbsolutePath().normalize();
                String javaSource = Optional.ofNullable(release).filter(value -> !value.isBlank())
                        .or(() -> Optional.ofNullable(source).filter(value -> !value.isBlank()))
                        .orElse("17");
                Charset sourceEncoding = Optional.ofNullable(encoding)
                        .filter(value -> !value.isBlank() && !value.startsWith("${"))
                        .map(Charset::forName)
                        .orElse(StandardCharsets.UTF_8);
                EclipseCleanupRunner.Result result = EmbeddedEclipseCleanupRunner.run(baseDirectory,
                        project.getCompileSourceRoots(), project.getCompileClasspathElements(),
                        javaSource, sourceEncoding,
                        Optional.ofNullable(eclipseCleanupProfile).map(File::toPath).orElse(null),
                        eclipseDuplicatePolicy);
                getLog().info("Running embedded Eclipse JDT cleanup analysis without an IDE.");
                return result;
            } catch (IOException | DependencyResolutionRequiredException | RuntimeException exception) {
                throw new MojoExecutionException("Embedded Eclipse cleanup analysis failed: "
                        + exception.getMessage(), exception);
            }
        }
        if (eclipseCleanupProfile == null) {
            throw new MojoExecutionException("External Eclipse analysis requires eclipseCleanupProfile.");
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

    private void reportSupplementalIssues(List<SupplementalAnalyzer.Finding> issues) {
        List<SupplementalAnalyzer.Finding> sorted = new ArrayList<>(issues);
        sorted.sort(Comparator.comparing(SupplementalAnalyzer.Finding::file)
                .thenComparingLong(SupplementalAnalyzer.Finding::line)
                .thenComparing(SupplementalAnalyzer.Finding::rule));
        for (SupplementalAnalyzer.Finding issue : sorted) {
            getLog().warn(issue.file() + ":" + issue.line()
                    + " [" + issue.rule() + "] " + issue.message());
        }
        if (!issues.isEmpty()) {
            getLog().info("Supplemental Java analysis: " + issues.size() + " violation(s).");
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

    private ProjectAnalysis analyzeProject() throws MojoExecutionException {
        try {
            Path baseDirectory = project.getBasedir().toPath().toAbsolutePath().normalize();
            List<JavaSourceFile> files = collectSources(baseDirectory);
            if (files.isEmpty()) {
                getLog().info("No Java sources to analyze.");
                return new ProjectAnalysis(List.of(), List.of());
            }
            Path binaries = Path.of(project.getBuild().getOutputDirectory()).toAbsolutePath().normalize();
            if (!Files.isDirectory(binaries)) {
                throw new MojoExecutionException("Compiled classes are missing. Run Maven compile first.");
            }
            Path workDirectory = Path.of(project.getBuild().getDirectory(), "static-analysis").toAbsolutePath();
            Path analyzer = findAnalyzer();
            boolean useSymbolicAnalyzer = "reliability".equals(profile)
                    || "sonar-way".equals(profile)
                    || "sonar-way-reliability".equals(profile);
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
            Set<String> supplementalRules = catalog.supplementalRules(profile, enabledRules, disabledRules);
            if (activeRules.isEmpty() && supplementalRules.isEmpty()) {
                getLog().info("No local Java rules selected.");
                return new ProjectAnalysis(List.of(), List.of());
            }
            List<Issue> sonarIssues = List.of();
            if (!activeRules.isEmpty()) {
                getLog().info("Activated " + activeRules.size() + " local Java rule(s).");
                AnalysisConfiguration configuration = AnalysisConfiguration.builder()
                        .setBaseDir(baseDirectory)
                        .addInputFiles(files)
                        .putAllExtraProperties(analysisProperties(binaries))
                        .addActiveRules(activeRules)
                        .build();
                getLog().info("Analyzing " + files.size() + " Java source file(s) locally.");
                if (useSymbolicAnalyzer) {
                    sonarIssues = new StandaloneAnalyzer(analyzer, symbolicAnalyzer, workDirectory,
                            new MavenLogOutput(getLog())).analyze(configuration);
                } else {
                    sonarIssues = new StandaloneAnalyzer(analyzer, workDirectory,
                            new MavenLogOutput(getLog())).analyze(configuration);
                }
            }
            List<SupplementalAnalyzer.Finding> additionalIssues = List.of();
            if (!supplementalRules.isEmpty()) {
                List<Path> sourcePaths = new ArrayList<>();
                for (JavaSourceFile file : files) {
                    sourcePaths.add(Path.of(file.getPath()));
                }
                String sourceVersion = Optional.ofNullable(release).filter(value -> !value.isBlank())
                        .or(() -> Optional.ofNullable(source).filter(value -> !value.isBlank()))
                        .orElse("");
                additionalIssues = SupplementalAnalyzer.analyze(baseDirectory, sourcePaths,
                        project.getTestClasspathElements(), sourceVersion, supplementalRules);
            }
            return new ProjectAnalysis(sonarIssues, removeDuplicateIssues(sonarIssues, additionalIssues));
        } catch (IOException | DependencyResolutionRequiredException | RuntimeException exception) {
            throw new MojoExecutionException("Local Sonar analysis could not complete: " + exception.getMessage(), exception);
        }
    }

    private static List<SupplementalAnalyzer.Finding> removeDuplicateIssues(
            List<Issue> sonarIssues, List<SupplementalAnalyzer.Finding> additionalIssues) {
        Set<String> reported = new LinkedHashSet<>();
        for (Issue issue : sonarIssues) {
            reported.add(location(issue) + ":" + issue.getRuleKey());
        }
        List<SupplementalAnalyzer.Finding> distinct = new ArrayList<>();
        for (SupplementalAnalyzer.Finding issue : additionalIssues) {
            String key = issue.file() + ":" + issue.line() + ":" + issue.rule();
            if (reported.add(key)) {
                distinct.add(issue);
            }
        }
        return distinct;
    }

    private List<JavaSourceFile> collectSources(Path baseDirectory) throws IOException {
        Charset charset = Charset.forName(Optional.ofNullable(encoding)
                .filter(value -> !value.isBlank()).orElse("UTF-8"));
        Map<Path, Boolean> paths = new TreeMap<>();
        collectSourceRoots(baseDirectory, project.getCompileSourceRoots(), false, paths);
        collectSourceRoots(baseDirectory, project.getTestCompileSourceRoots(), true, paths);
        List<JavaSourceFile> files = new ArrayList<>();
        for (Map.Entry<Path, Boolean> entry : paths.entrySet()) {
            files.add(new JavaSourceFile(baseDirectory, entry.getKey(), charset, entry.getValue()));
        }
        return files;
    }

    private static void collectSourceRoots(Path baseDirectory, List<String> roots,
            boolean test, Map<Path, Boolean> paths) throws IOException {
        for (String root : roots) {
            Path directory = baseDirectory.resolve(root).normalize();
            if (Files.isDirectory(directory)) {
                try (Stream<Path> entries = Files.walk(directory)) {
                    for (Path path : entries.filter(Files::isRegularFile)
                            .filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
                        paths.putIfAbsent(path, test);
                    }
                }
            }
        }
    }

    private Map<String, String> analysisProperties(Path binaries)
            throws DependencyResolutionRequiredException {
        Map<String, String> properties = new TreeMap<>();
        properties.put("sonar.java.binaries", binaries.toString());
        Path testBinaries = Path.of(project.getBuild().getTestOutputDirectory())
                .toAbsolutePath().normalize();
        if (Files.isDirectory(testBinaries)) {
            properties.put("sonar.java.test.binaries", testBinaries.toString());
        }
        List<String> libraries = new ArrayList<>();
        for (String element : project.getTestClasspathElements()) {
            Path path = Path.of(element).toAbsolutePath().normalize();
            if (!path.equals(binaries) && !path.equals(testBinaries) && Files.exists(path)) {
                libraries.add(path.toString());
            }
        }
        if (!libraries.isEmpty()) {
            String classpath = String.join(",", libraries);
            properties.put("sonar.java.libraries", classpath);
            properties.put("sonar.java.test.libraries", classpath);
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

    private record ProjectAnalysis(List<Issue> sonarIssues,
            List<SupplementalAnalyzer.Finding> additionalIssues) {
    }

    private static String location(Issue issue) {
        String file = Optional.ofNullable(issue.getInputFile())
                .map(ClientInputFile::relativePath).orElse("<project>");
        return Optional.ofNullable(issue.getTextRange()).map(TextRange::getStartLine)
                .map(line -> file + ":" + line).orElse(file);
    }
}
