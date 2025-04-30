package common;

import java.io.File;
import java.io.IOException;
import java.util.logging.*;

/**
 * Utility class for creating and configuring loggers with both file and console handlers.
 * Each class using this utility will have its own log file under the ./logs/ directory.
 */
public class LoggerUtil {
    private static final String LOG_DIRECTORY = "./logs/";

    // Ensure the log directory exists
    static {
        File dir = new File(LOG_DIRECTORY);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Returns a configured logger for the given class.
     * The logger writes logs to a file specific to the class and also outputs to the console.
     *
     * @param clazz The class for which the logger is being created
     * @return A configured Logger instance
     */
    public static Logger getLogger(Class<?> clazz) {
        Logger logger = Logger.getLogger(clazz.getName());
        logger.setUseParentHandlers(false);
        try {
            String logFilePath = LOG_DIRECTORY + clazz.getSimpleName().toLowerCase() + ".log";
            FileHandler fileHandler = new FileHandler(logFilePath, true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);

            // Console handler
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(consoleHandler);

        } catch (IOException e) {
            System.err.println("Erreur lors de la configuration du logger pour "
                + clazz.getSimpleName() + ": " + e.getMessage());
        }
        return logger;
    }
}
