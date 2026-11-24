package io.github.nbauma109.staticanalysis;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * Registers offline static analysis for Java projects.
 */
public final class StaticAnalysisGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        StaticAnalysisExtension extension = project.getExtensions()
                .create("staticAnalysis", StaticAnalysisExtension.class);
        Configuration analyzer = project.getConfigurations().create("staticAnalysisAnalyzer", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setVisible(false);
        });
        ExternalModuleDependency dependency = (ExternalModuleDependency) project.getDependencies()
                .create("org.sonarsource.java:sonar-java-plugin:8.6.0.37351");
        dependency.setTransitive(false);
        analyzer.getDependencies().add(dependency);
        Configuration symbolicAnalyzer = project.getConfigurations()
                .create("staticAnalysisSymbolicAnalyzer", configuration -> {
                    configuration.setCanBeConsumed(false);
                    configuration.setCanBeResolved(true);
                    configuration.setVisible(false);
                });
        ExternalModuleDependency symbolicDependency = (ExternalModuleDependency)
                project.getDependencies().create(
                        "org.sonarsource.java:sonar-java-symbolic-execution-plugin:8.6.0.37351");
        symbolicDependency.setTransitive(false);
        symbolicAnalyzer.getDependencies().add(symbolicDependency);

        project.getPluginManager().withPlugin("java", applied -> {
            SourceSet sources = project.getExtensions().getByType(SourceSetContainer.class)
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            TaskProvider<StaticAnalysisTask> analysis = project.getTasks()
                    .register("staticAnalysis", StaticAnalysisTask.class, task -> {
                        task.setAnalysisExtension(extension);
                        task.setSourceSet(sources);
                        task.setAnalyzerConfiguration(analyzer);
                        task.setSymbolicAnalyzerConfiguration(symbolicAnalyzer);
                        task.dependsOn(project.getTasks().named("classes"));
                    });
            SourceSet tests = project.getExtensions().getByType(SourceSetContainer.class)
                    .getByName(SourceSet.TEST_SOURCE_SET_NAME);
            TaskProvider<StaticAnalysisTestsTask> testAnalysis = project.getTasks()
                    .register("staticAnalysisTests", StaticAnalysisTestsTask.class, task -> {
                        task.setAnalysisExtension(extension);
                        task.setSourceSet(tests);
                        task.dependsOn(project.getTasks().named("testClasses"));
                    });
            project.getTasks().named("check").configure(task -> task.dependsOn(analysis, testAnalysis));
        });
    }
}
