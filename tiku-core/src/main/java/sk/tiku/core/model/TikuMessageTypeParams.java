package sk.tiku.core.model;

/**
 * Constants for payload map keys
 */
public class TikuMessageTypeParams {
    /**
     * Login argument - node host
     */
    public static final String LOGIN_ARG_HOST = "LOGIN_ARG_HOST";
    /**
     * Login argument - node port
     */
    public static final String LOGIN_ARG_PORT = "LOGIN_ARG_PORT";
    /**
     * Login argument - node public key
     */
    public static final String LOGIN_ARG_PUBKEY = "LOGIN_ARG_PUBKEY";

    /**
     * Logout argument - node host
     */
    public static final String LOGOUT_ARG_HOST = "LOGOUT_ARG_HOST";

    /**
     * Logout argument - node port
     */
    public static final String LOGOUT_ARG_PORT = "LOGOUT_ARG_PORT";

    /**
     * Logout argument - public key
     */
    public static final String LOGOUT_ARG_PUBKEY = "LOGOUT_ARG_PUBKEY";

    /**
     * Fetch argument - url value
     */
    public static final String FETCH_ARG_URL = "FETCH_ARG_URL";

    /**
     * Relay next argument - message
     */
    public static final String RELAY_NEXT_ARG_MESSAGE = "RELAY_NEXT_ARG_MESSAGE";

    /**
     * Relay next argument - ip
     */
    public static final String RELAY_NEXT_ARG_IP = "RELAY_NEXT_ARG_IP";

    /**
     * Relay next argument - port
     */
    public static final String RELAY_NEXT_ARG_PORT = "RELAY_NEXT_ARG_PORT";

    /**
     * Relay next argument - pobKey
     */
    public static final String RELAY_NEXT_ARG_PUBKEY = "RELAY_NEXT_ARG_PUBKEY";
}
