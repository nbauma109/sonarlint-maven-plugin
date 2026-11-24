package io.github.nbauma109.staticanalysis;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Optional;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

final class MavenSourceVersion {

    private static final String COMPILER = "org.apache.maven.plugins:maven-compiler-plugin";

    private final MavenProject project;
    private final int productionRelease;
    private final int testRelease;

    MavenSourceVersion(MavenProject project, String release, String source,
            String testRelease, String testSource) {
        this.project = project;
        this.productionRelease = RuleJavaVersion.parse(first(release, source).orElse(""));
        this.testRelease = RuleJavaVersion.parse(first(testRelease, release, testSource, source).orElse(""));
    }

    int release(Path file, boolean test) {
        int version = productionRelease;
        if (test) {
            version = testRelease;
        }
        Plugin compiler = project.getPlugin(COMPILER);
        if (compiler == null) {
            return version;
        }
        if (compiler.getConfiguration() instanceof Xpp3Dom configuration) {
            version = configuredRelease(configuration, test).orElse(version);
        }
        for (PluginExecution execution : compiler.getExecutions()) {
            if (execution.getGoals().contains(goal(test))
                    && execution.getConfiguration() instanceof Xpp3Dom configuration
                    && matches(file, test, configuration)) {
                version = configuredRelease(configuration, test).orElse(version);
            }
        }
        return version;
    }

    private static String goal(boolean test) {
        if (test) {
            return "testCompile";
        }
        return "compile";
    }

    private static Optional<Integer> configuredRelease(Xpp3Dom configuration, boolean test) {
        if (test) {
            return first(value(configuration, "testRelease"), value(configuration, "release"),
                    value(configuration, "testSource"), value(configuration, "source"))
                    .map(RuleJavaVersion::parse);
        }
        return first(value(configuration, "release"), value(configuration, "source"))
                .map(RuleJavaVersion::parse);
    }

    private static String value(Xpp3Dom configuration, String name) {
        return Optional.ofNullable(configuration.getChild(name)).map(Xpp3Dom::getValue).orElse(null);
    }

    private static Optional<String> first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !value.startsWith("${")) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private boolean matches(Path file, boolean test, Xpp3Dom configuration) {
        if (test) {
            return matches(file, project.getTestCompileSourceRoots(), configuration,
                    "testIncludes", "testExcludes");
        }
        return matches(file, project.getCompileSourceRoots(), configuration, "includes", "excludes");
    }

    private boolean matches(Path file, List<String> roots, Xpp3Dom configuration,
            String includesName, String excludesName) {
        for (String root : roots) {
            Path directory = project.getBasedir().toPath().resolve(root).toAbsolutePath().normalize();
            if (file.startsWith(directory)) {
                Path relative = directory.relativize(file);
                Xpp3Dom includes = configuration.getChild(includesName);
                Xpp3Dom excludes = configuration.getChild(excludesName);
                return (includes == null || matchesAny(relative, includes))
                        && (excludes == null || !matchesAny(relative, excludes));
            }
        }
        return false;
    }

    private static boolean matchesAny(Path relative, Xpp3Dom patterns) {
        for (Xpp3Dom child : patterns.getChildren()) {
            String pattern = child.getValue();
            if (pattern != null && matchesPattern(relative, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPattern(Path relative, String pattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        if (matcher.matches(relative)) {
            return true;
        }
        if (pattern.startsWith("**/")) {
            return FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3))
                    .matches(relative);
        }
        return false;
    }
}
