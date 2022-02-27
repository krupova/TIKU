package sk.tiku.core.model;

import java.util.Map;

/**
 * Message data containing instruction and payload for node. This data should be encrypted before sending to another
 * node
 */
public class MessageData {
    /**
     * Message type. Determines operation that needs to do.
     */
    private MessageType type;
    /**
     * Any additional parameters for operation stored as key-value pairs
     */
    private Map<String, String> payload;

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, String> payload) {
        this.payload = payload;
    }
}
