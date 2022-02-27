package sk.tiku.core.model;

/**
 * Communication message. DTO that transfers data between nodes.
 */
public class CommunicationMessage {
    /**
     * AES-256-CBC encrypted data encoded as Base64String
     */
    private String encryptedData;
    /**
     * Sender's public key for Diffie-Helman key agreement encoded as Base64String. Receiver should use this key
     * together with his own private key to determine encryption key for encryptedData
     */
    private String pubkey;

    public CommunicationMessage() {
    }

    public CommunicationMessage(String encryptedData, String pubkey) {
        this.encryptedData = encryptedData;
        this.pubkey = pubkey;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }
}
