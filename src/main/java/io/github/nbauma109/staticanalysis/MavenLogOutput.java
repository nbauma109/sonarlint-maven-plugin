package io.github.nbauma109.staticanalysis;

import org.apache.maven.plugin.logging.Log;
import org.sonarsource.staticAnalysis.core.commons.log.LogOutput;

final class MavenLogOutput implements LogOutput {

    private final Log logger;

    MavenLogOutput(Log logger) {
        this.logger = logger;
    }

    @Override
    public void log(String message, Level level, String stacktrace) {
        if (message != null) {
            logMessage(message, level);
        }
        if (stacktrace != null) {
            logger.debug(stacktrace);
        }
    }

    private void logMessage(String message, Level level) {
        switch (level) {
            case ERROR -> logger.error(message);
            case WARN -> logger.warn(message);
            case INFO -> logger.debug(message);
            case DEBUG, TRACE -> logger.debug(message);
            default -> { }
        }
    }
}
