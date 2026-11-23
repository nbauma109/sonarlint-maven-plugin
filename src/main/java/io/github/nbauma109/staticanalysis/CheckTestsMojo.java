package io.github.nbauma109.staticanalysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Checks supported test-source rules after the Maven test compiler runs.
 */
@Mojo(name = "check-tests", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES,
        requiresDependencyResolution = ResolutionScope.TEST)
public final class CheckTestsMojo extends AbstractMojo {

    private static final Set<String> SUPPORTED_TEST_RULES =
            Set.of("java:S7467", "java:S8786");

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

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
        if (skip || "pom".equals(project.getPackaging())) {
            getLog().info("Skipping test-source static analysis.");
            return;
        }
        Set<String> selected = selectedTestRules();
        if (selected.isEmpty()) {
            getLog().info("No supported test-source Java rules selected.");
            return;
        }
        try {
            Path baseDirectory = project.getBasedir().toPath().toAbsolutePath().normalize();
            List<Path> javaSources = collectTestSources(baseDirectory);
            if (javaSources.isEmpty()) {
                getLog().info("No test Java sources to analyze.");
                return;
            }
            Path testOutput = Path.of(project.getBuild().getTestOutputDirectory());
            if (!Files.isDirectory(testOutput)) {
                throw new MojoExecutionException("Compiled test classes are missing. Run Maven test-compile first.");
            }
            List<SupplementalAnalyzer.Finding> findings = SupplementalAnalyzer.analyze(
                    baseDirectory, javaSources, project.getTestClasspathElements(),
                    sourceLevel(), selected);
            report(findings);
            if (failOnViolation && !findings.isEmpty()) {
                throw new MojoFailureException("Test-source analysis found " + findings.size() + " violation(s).");
            }
        } catch (IOException | DependencyResolutionRequiredException exception) {
            throw new MojoExecutionException("Test-source Java analysis failed: "
                    + exception.getMessage(), exception);
        }
    }

    private Set<String> selectedTestRules() throws MojoExecutionException {
        Set<String> selected = new TreeSet<>();
        if (isFullProfile()) {
            selected.addAll(SUPPORTED_TEST_RULES);
        }
        for (String key : parseRuleKeys(enabledRules)) {
            if (SUPPORTED_TEST_RULES.contains(key)) {
                selected.add(key);
            }
        }
        selected.removeAll(parseRuleKeys(disabledRules));
        return selected;
    }

    private boolean isFullProfile() {
        return "sonar-way".equals(profile) || "sonar-way-reliability".equals(profile);
    }

    private static Set<String> parseRuleKeys(String value) throws MojoExecutionException {
        Set<String> keys = new TreeSet<>();
        if (value != null && !value.isBlank()) {
            for (String part : value.split(",", -1)) {
                String key = part.trim();
                if (key.isEmpty()) {
                    throw new MojoExecutionException("Rule lists must not contain blank entries.");
                }
                keys.add(key);
            }
        }
        return keys;
    }

    private List<Path> collectTestSources(Path baseDirectory) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String sourceRoot : project.getTestCompileSourceRoots()) {
            Path root = baseDirectory.resolve(sourceRoot).normalize();
            if (Files.isDirectory(root)) {
                try (Stream<Path> paths = Files.walk(root)) {
                    for (Path path : paths.toList()) {
                        if (Files.isRegularFile(path) && path.toString().endsWith(".java")) {
                            files.add(path);
                        }
                    }
                }
            }
        }
        files.sort(Comparator.naturalOrder());
        return files;
    }

    private String sourceLevel() {
        for (String candidate : new String[] {testRelease, release, testSource, source}) {
            if (candidate != null && !candidate.isBlank()
                    && !candidate.startsWith("${")) {
                return candidate;
            }
        }
        return Integer.toString(Runtime.version().feature());
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
