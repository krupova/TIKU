package sk.tiku.core.logging;

/**
 * Enum containing all possible retention values.
 *
 * @see Logger
 */
public enum LogRetention {

    /**
     * Debugging log level. Should be used for any logs providing more information for problem solving.
     */
    DEBUG(3),
    /**
     * Info log level. Should be used for normal logs.
     */
    INFO(2),
    /**
     * Warning log level. Should be used for logging any potentially harmful situations.
     */
    WARN(1),
    /**
     * Error log level. Should be used for exception and error logging.
     */
    ERROR(0);

    int value;

    LogRetention(int value){
        this.value =value;
    }

    }
