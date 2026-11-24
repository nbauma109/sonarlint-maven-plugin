package io.github.nbauma109.staticanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.Issue;

@DisableCachingByDefault(because = "Reports test-source diagnostics.")
public abstract class StaticAnalysisTestsTask extends DefaultTask {

    private StaticAnalysisExtension extension;
    private SourceSet sourceSet;
    private Configuration analyzerConfiguration;

    @Internal
    public Configuration getAnalyzerConfiguration() {
        return analyzerConfiguration;
    }

    public void setAnalyzerConfiguration(Configuration analyzerConfiguration) {
        this.analyzerConfiguration = analyzerConfiguration;
    }

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

    @TaskAction
    public void analyse() {
        if (extension.isSkip()) {
            getLogger().lifecycle("Skipping test-source static analysis.");
            return;
        }
        try {
            Set<String> rules = RuleCatalog.testRules(extension.getProfile(),
                    extension.getEnabledRules(), extension.getDisabledRules());
            Path project = getProject().getProjectDir().toPath().toAbsolutePath().normalize();
            SourceAnalysisScope scope = new SourceAnalysisScope(project,
                    getProject().getLayout().getBuildDirectory().get().getAsFile().toPath());
            List<Path> sources = new ArrayList<>();
            for (File file : sourceSet.getAllJava().getFiles()) {
                Path path = file.toPath().toAbsolutePath().normalize();
                if (scope.includes(path)) {
                    sources.add(path);
                }
            }
            sources.sort(Path::compareTo);
            List<SupplementalAnalyzer.Finding> findings = GradleSupplementalAnalysis.analyze(
                    getProject(), sourceSet, project, sources, rules);
            List<Issue> nativeIssues = analyzeNative(project, sources);
            List<String> reported = new ArrayList<>();
            for (Issue issue : nativeIssues) {
                String location = NativeTestAnalysis.location(issue);
                reported.add(location + ":" + issue.getRuleKey());
                getLogger().warn(location + " [" + issue.getRuleKey() + "] " + issue.getMessage());
            }
            getLogger().lifecycle("Native test-source Java analysis: " + nativeIssues.size() + " violation(s).");
            findings = SupplementalAnalyzer.withoutNativeDuplicates(findings, reported);
            GradleSupplementalAnalysis.report(getLogger(), findings);
            getLogger().lifecycle("Test-source Java analysis: " + findings.size() + " violation(s).");
            int total = nativeIssues.size() + findings.size();
            if (extension.isFailOnViolation() && total > 0) {
                throw new GradleException("Test-source analysis found " + total + " violation(s).");
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new GradleException("Test-source Java analysis failed: " + exception.getMessage(), exception);
        }
    }

    private List<Issue> analyzeNative(Path project, List<Path> sources) throws IOException {
        if (sources.isEmpty()) {
            return List.of();
        }
        Path analyzer = analyzerConfiguration.getSingleFile().toPath();
        RuleCatalog catalog = RuleCatalog.load(analyzer);
        List<RuleParameters> overrides = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : extension.getRuleParameters().entrySet()) {
            RuleParameters parameter = new RuleParameters();
            parameter.setKey(entry.getKey());
            parameter.setProperties(entry.getValue());
            overrides.add(parameter);
        }
        List<ActiveRule> rules = catalog.selectForTests(extension.getProfile(), extension.getEnabledRules(),
                extension.getDisabledRules(), extension.getCognitiveComplexityThreshold(), overrides);
        if (rules.isEmpty()) {
            return List.of();
        }
        Map<String, String> properties = new TreeMap<>();
        List<String> binaries = new ArrayList<>();
        for (File file : sourceSet.getOutput().getClassesDirs().getFiles()) {
            if (file.isDirectory()) {
                binaries.add(file.getAbsolutePath());
            }
        }
        List<String> libraries = new ArrayList<>();
        for (File file : sourceSet.getCompileClasspath().getFiles()) {
            if (Files.exists(file.toPath())) {
                libraries.add(file.getAbsolutePath());
            }
        }
        properties.put("sonar.java.binaries", String.join(",", binaries));
        properties.put("sonar.java.test.binaries", String.join(",", binaries));
        properties.put("sonar.java.libraries", String.join(",", libraries));
        properties.put("sonar.java.test.libraries", String.join(",", libraries));
        properties.put("sonar.java.source", GradleSourceVersion.release(getProject(), sourceSet));
        Path work = getProject().getLayout().getBuildDirectory().get().getAsFile().toPath()
                .resolve("static-analysis/test-sonar");
        return NativeTestAnalysis.analyze(project, work, analyzer, null, sources,
                StandardCharsets.UTF_8, properties, rules, new GradleLogOutput(getLogger()));
    }
}
