package io.github.nbauma109.staticanalysis;

import org.gradle.api.logging.Logger;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;

final class GradleLogOutput implements LogOutput {

    private final Logger logger;

    GradleLogOutput(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void log(String message, Level level, String stacktrace) {
        if (message != null) {
            switch (level) {
                case ERROR -> logger.error(message);
                case WARN -> logger.warn(message);
                case INFO, DEBUG, TRACE -> logger.debug(message);
                default -> { }
            }
        }
        if (stacktrace != null) {
            logger.debug(stacktrace);
        }
    }
}
