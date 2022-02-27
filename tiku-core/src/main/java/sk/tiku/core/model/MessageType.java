package sk.tiku.core.model;

public enum MessageType {
    /**
     * Fetch remote asset from url in payload
     */
    FETCH,
    /**
     * Send message to another tiku-node
     */
    RELAY_NEXT,
    /**
     * Login tiku-client to tiku-server
     */
    LOGIN,
    /**
     * Logout tiku-client from tiku-server
     */
    LOGOUT,
    /**
     * Get encryption relay
     */
    GET_RELAY,
}
