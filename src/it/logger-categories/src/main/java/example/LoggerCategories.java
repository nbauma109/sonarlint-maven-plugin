package example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggerCategories {

    private LoggerCategories() {
    }

    public static final class WrongCategory {
        private static final Logger LOGGER = LoggerFactory.getLogger(LoggerCategories.class);

        public void record(String message) {
            LOGGER.info(message);
        }
    }

    public static final class CorrectCategory {
        private static final Logger LOGGER = LoggerFactory.getLogger(CorrectCategory.class);

        public void record(String message) {
            LOGGER.info(message);
        }
    }
}
