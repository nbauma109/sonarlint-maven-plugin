package io.github.nbauma109.staticanalysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.plugin.logging.Log;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.container.global.GlobalAnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.module.ModuleContainer;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;

final class StandaloneAnalyzer {

    private final Path analyzerJar;
    private final Path workDirectory;
    private final Log logger;

    StandaloneAnalyzer(Path analyzerJar, Path workDirectory, Log logger) {
        this.analyzerJar = analyzerJar;
        this.workDirectory = workDirectory;
        this.logger = logger;
    }

    List<Issue> analyze(AnalysisConfiguration configuration) throws IOException {
        LogOutput previousTarget = SonarLintLogger.getTargetForCopy();
        SonarLintLogger.setTarget(new MavenLogOutput(logger));
        try {
            return analyzeLocally(configuration);
        } finally {
            SonarLintLogger.setTarget(previousTarget);
        }
    }

    private List<Issue> analyzeLocally(AnalysisConfiguration configuration) throws IOException {
        Files.createDirectories(workDirectory);
        PluginsLoader.Configuration pluginConfiguration = new PluginsLoader.Configuration(
                Set.of(analyzerJar), Set.of(SonarLanguage.JAVA), false, Optional.empty());
        PluginsLoadResult plugins = new PluginsLoader().load(pluginConfiguration, Set.of());
        if (!plugins.getLoadedPlugins().getAnalysisPluginInstancesByKeys().containsKey("java")) {
            plugins.close();
            throw new IOException("SonarJava was not loaded. Check the analyzer version and Java runtime.");
        }

        AnalysisEngineConfiguration engineConfiguration = AnalysisEngineConfiguration.builder()
                .setWorkDir(workDirectory)
                .setExtraProperties(Map.of("sonar.userHome", workDirectory.toString()))
                .setClientPid(ProcessHandle.current().pid())
                .build();
        GlobalAnalysisContainer container = new GlobalAnalysisContainer(
                engineConfiguration, plugins.getLoadedPlugins());
        try {
            container.startComponents();
            return runAnalysis(container, configuration);
        } finally {
            container.stopComponents();
        }
    }

    private static List<Issue> runAnalysis(GlobalAnalysisContainer container,
            AnalysisConfiguration configuration) throws IOException {
        ModuleContainer module = container.getModuleRegistry()
                .createTransientContainer(configuration.inputFiles());
        List<Issue> issues = Collections.synchronizedList(new ArrayList<>());
        try {
            AnalysisResults results = module.analyze(configuration, issues::add, new ProgressMonitor(null));
            if (!results.failedAnalysisFiles().isEmpty()) {
                throw new IOException("Sonar analysis failed for "
                        + results.failedAnalysisFiles().size() + " source file(s).");
            }
            if (results.indexedFileCount() != configuration.inputFiles().size()) {
                throw new IOException("Sonar indexed " + results.indexedFileCount()
                        + " of " + configuration.inputFiles().size() + " source file(s).");
            }
            return List.copyOf(issues);
        } finally {
            module.stopComponents();
        }
    }
}
