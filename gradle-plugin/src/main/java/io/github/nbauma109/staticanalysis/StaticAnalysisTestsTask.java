package io.github.nbauma109.staticanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Reports test-source diagnostics.")
public abstract class StaticAnalysisTestsTask extends DefaultTask {

    private StaticAnalysisExtension extension;
    private SourceSet sourceSet;

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
            GradleSupplementalAnalysis.report(getLogger(), findings);
            getLogger().lifecycle("Test-source Java analysis: " + findings.size() + " violation(s).");
            if (extension.isFailOnViolation() && !findings.isEmpty()) {
                throw new GradleException("Test-source analysis found " + findings.size() + " violation(s).");
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new GradleException("Test-source Java analysis failed: " + exception.getMessage(), exception);
        }
    }
}
