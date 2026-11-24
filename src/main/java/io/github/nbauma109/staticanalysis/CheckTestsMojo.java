package io.github.nbauma109.staticanalysis;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Set;
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
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.Issue;

/**
 * Checks supported test-source rules after the Maven test compiler runs.
 */
@Mojo(name = "check-tests", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES,
        requiresDependencyResolution = ResolutionScope.TEST)
public final class CheckTestsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${plugin.artifacts}", readonly = true, required = true)
    private List<Artifact> pluginArtifacts;

    @Parameter(property = "staticAnalysis.cognitiveComplexityThreshold", defaultValue = "15")
    private int cognitiveComplexityThreshold;

    @Parameter
    private List<RuleParameters> ruleParameters;

    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    @Parameter(property = "staticAnalysis.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "staticAnalysis.failOnViolation", defaultValue = "false")
    private boolean failOnViolation;

    @Parameter(property = "staticAnalysis.profile", defaultValue = "priority")
    private String profile;

    @Parameter(property = "staticAnalysis.enabledRules", defaultValue = "")
    private String enabledRules;

    @Parameter(property = "staticAnalysis.disabledRules", defaultValue = "")
    private String disabledRules;

    @Parameter(defaultValue = "${maven.compiler.testRelease}")
    private String testRelease;

    @Parameter(defaultValue = "${maven.compiler.release}")
    private String release;

    @Parameter(defaultValue = "${maven.compiler.testSource}")
    private String testSource;

    @Parameter(defaultValue = "${maven.compiler.source}")
    private String source;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        int count = analyzeTests();
        if (failOnViolation && count > 0) {
            throw new MojoFailureException("Test-source analysis found " + count + " violation(s).");
        }
    }

    private int analyzeTests() throws MojoExecutionException {
        if (skip || "pom".equals(project.getPackaging())) {
            getLog().info("Skipping test-source static analysis.");
            return 0;
        }
        try {
            Set<String> selected = RuleCatalog.testRules(profile, enabledRules, disabledRules);
            Path baseDirectory = project.getBasedir().toPath().toAbsolutePath().normalize();
            List<Path> javaSources = collectTestSources(baseDirectory);
            if (javaSources.isEmpty()) {
                getLog().info("No test Java sources to analyze.");
                return 0;
            }
            Path testOutput = Path.of(project.getBuild().getTestOutputDirectory());
            if (!Files.isDirectory(testOutput)) {
                throw new MojoExecutionException("Compiled test classes are missing. Run Maven test-compile first.");
            }
            MavenSourceVersion versions = new MavenSourceVersion(project, release, source,
                    testRelease, testSource);
            Map<Integer, List<Path>> byRelease = new TreeMap<>();
            for (Path file : javaSources) {
                byRelease.computeIfAbsent(versions.release(file, true), ignored -> new ArrayList<>())
                        .add(file);
            }
            List<SupplementalAnalyzer.Finding> findings = SupplementalAnalyzer.analyzeByRelease(
                    baseDirectory, byRelease, project.getTestClasspathElements(), selected);
            List<Issue> nativeIssues = analyzeNative(baseDirectory, byRelease);
            List<String> reported = new ArrayList<>();
            for (Issue issue : nativeIssues) {
                String location = NativeTestAnalysis.location(issue);
                reported.add(location + ":" + issue.getRuleKey());
                getLog().warn(location + " [" + issue.getRuleKey() + "] " + issue.getMessage());
            }
            getLog().info("Native test-source Java analysis: " + nativeIssues.size() + " violation(s).");
            findings = SupplementalAnalyzer.withoutNativeDuplicates(findings, reported);
            report(findings);
            return nativeIssues.size() + findings.size();
        } catch (IOException | DependencyResolutionRequiredException | IllegalArgumentException exception) {
            throw new MojoExecutionException("Test-source Java analysis failed: "
                    + exception.getMessage(), exception);
        }
    }

    private List<Issue> analyzeNative(Path baseDirectory, Map<Integer, List<Path>> byRelease)
            throws IOException, DependencyResolutionRequiredException {
        Path analyzer = findAnalyzer("sonar-java-plugin");
        RuleCatalog catalog = RuleCatalog.load(analyzer);
        List<ActiveRule> rules = catalog.selectForTests(profile, enabledRules, disabledRules,
                cognitiveComplexityThreshold, Optional.ofNullable(ruleParameters).orElse(List.of()));
        if (rules.isEmpty()) {
            return List.of();
        }
        Map<String, String> properties = new TreeMap<>();
        properties.put("sonar.java.binaries", project.getBuild().getOutputDirectory());
        properties.put("sonar.java.test.binaries", project.getBuild().getTestOutputDirectory());
        String libraries = String.join(",", project.getTestClasspathElements());
        properties.put("sonar.java.libraries", libraries);
        properties.put("sonar.java.test.libraries", libraries);
        Charset charset = Optional.ofNullable(encoding).filter(value -> !value.isBlank())
                .map(Charset::forName).orElse(StandardCharsets.UTF_8);
        List<Issue> issues = new ArrayList<>();
        for (Map.Entry<Integer, List<Path>> entry : byRelease.entrySet()) {
            properties.put("sonar.java.source", Integer.toString(entry.getKey()));
            Path work = Path.of(project.getBuild().getDirectory(), "static-analysis",
                    "test-java-" + entry.getKey()).toAbsolutePath();
            issues.addAll(NativeTestAnalysis.analyze(baseDirectory, work, analyzer, null,
                    entry.getValue(), charset, properties, rules, new MavenLogOutput(getLog())));
        }
        return issues;
    }

    private Path findAnalyzer(String artifactId) throws IOException {
        for (Artifact artifact : pluginArtifacts) {
            if ("org.sonarsource.java".equals(artifact.getGroupId())
                    && artifactId.equals(artifact.getArtifactId()) && artifact.getFile() != null) {
                return artifact.getFile().toPath();
            }
        }
        throw new IOException("The Sonar analyzer dependency was not resolved: " + artifactId);
    }

    private List<Path> collectTestSources(Path baseDirectory) throws IOException {
        SourceAnalysisScope scope = new SourceAnalysisScope(baseDirectory,
                Path.of(project.getBuild().getDirectory()));
        List<Path> files = new ArrayList<>(new LinkedHashSet<>(
                scope.javaFiles(project.getTestCompileSourceRoots())));
        files.sort(Comparator.naturalOrder());
        return files;
    }

    private void report(List<SupplementalAnalyzer.Finding> findings) {
        List<SupplementalAnalyzer.Finding> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator.comparing(SupplementalAnalyzer.Finding::file)
                .thenComparingLong(SupplementalAnalyzer.Finding::line)
                .thenComparing(SupplementalAnalyzer.Finding::rule));
        for (SupplementalAnalyzer.Finding finding : sorted) {
            getLog().warn(finding.file() + ":" + finding.line() + " [" + finding.rule()
                    + "] " + finding.message());
        }
        getLog().info("Test-source Java analysis: " + findings.size() + " violation(s).");
    }
}
