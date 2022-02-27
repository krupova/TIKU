package sk.tiku.core.model;

/**
 * Model representing Tiku network node. Nodes are typically running client instances that are logged into
 * tiku server
 */
public class TikuNode {

    public TikuNode() {
    }

    public TikuNode(String host, Integer port, String pubKey) {
        this.host = host;
        this.port = port;
        this.pubKey = pubKey;
    }

    /**
     * Host of the Tiku node (e.g. ip address)
     */
    private String host;
    /**
     * Port of the Tiku node
     */
    private Integer port;
    /**
     * Public key of the node for Diffie-Helman key agreement encoded as Base64String.
     * <p>
     * Other node that is trying to communicate with given Node should use this key
     * together with his own private key to determine encryption key.
     */
    private String pubKey;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }
}
