package sk.tiku.core.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple logging utility.
 * <p>
 * Logs statements in format
 * DATE-TIME [RETENTION] {thread} VALUE.
 */
public class Logger {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static Logger instance;

    /**
     * Initialize logger with retention
     *
     * @param retention Retention
     */
    public static void initLogger(LogRetention retention) {
        if (instance == null) {
            instance = new Logger(retention);
            instance.info(String.format("Logger initialized with retention %s", retention.name()));
        } else {
            throw new RuntimeException("Logger already initialized");
        }
    }

    /**
     * Get logger instance. initLogger must be called before first call to this metod.
     *
     * @return Logger instance
     */
    public static Logger getInstance() {
        return instance;
    }

    private final LogRetention logLevel;

    private Logger(LogRetention logLevel) {
        this.logLevel = logLevel;
    }


    /**
     * Log INFO statement
     *
     * @param value Log message
     */
    public void info(String value) {
        doLog(value, LogRetention.INFO);
    }

    /**
     * Log WARN statement
     *
     * @param value Log message
     */
    public void warn(String value) {
        doLog(value, LogRetention.WARN);
    }

    /**
     * Log DEBUG statement
     *
     * @param value Log message
     */
    public void debug(String value) {
        doLog(value, LogRetention.DEBUG);
    }

    /**
     * Log ERROR statement
     *
     * @param value Log message
     */
    public void error(String value) {
        doLog(value, LogRetention.ERROR);
    }

    /**
     * Log ERROR statement
     *
     * @param value Log message
     * @param cause {@link Throwable} that have caused the error
     */
    public void error(String value, Throwable cause) {
        if (cause == null) {
            error(value);
        } else {
            try (StringWriter sw = new StringWriter();
                 PrintWriter pw = new PrintWriter(sw)) {
                cause.printStackTrace(pw);
                error(String.format("%s%nCaused by: %s%n%s", value, cause.getMessage(), sw));
            } catch (IOException ex) {
                error(String.format("%s%nCaused by: %s", value, cause.getMessage()));
            }
        }
    }


    private void doLog(String value, LogRetention retention) {
        if (logLevel.value >= retention.value) {
            String colorForRetention = getColorForRetention(retention);
            String dateTime = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            System.out.printf("%s%s [%s] {%s} %s%s%n",
                    colorForRetention,
                    dateTime,
                    retention.name(),
                    Thread.currentThread().getName(),
                    value,
                    ANSI_RESET
            );
        }
    }

    private String getColorForRetention(LogRetention retention) {
        return switch (retention) {
            case INFO -> ANSI_WHITE;
            case ERROR -> ANSI_RED;
            case WARN -> ANSI_YELLOW;
            case DEBUG -> ANSI_CYAN;
        };
    }

}
