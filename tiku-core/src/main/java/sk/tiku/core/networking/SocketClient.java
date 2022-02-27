package sk.tiku.core.networking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import sk.tiku.core.logging.Logger;
import sk.tiku.core.model.CommunicationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Client to send messages to {@link SocketServer}
 */
public class SocketClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Send message to {@link Socket}
     *
     * @param host    Host where {@link SocketServer} listens
     * @param port    Port where {@link SocketServer} listens
     * @param message Message to send
     * @return Response from {@link SocketServer}
     */
    public String send(String host, int port, CommunicationMessage message) {
        try (Socket socket = new Socket(host, port)) {

            try (OutputStream os = socket.getOutputStream();
                 InputStream is = socket.getInputStream()) {
                String serializedMessage = objectMapper.writeValueAsString(message);
                byte[] length = ByteBuffer.allocate(4).putInt(serializedMessage.length()).array();
                //FIRST WE NEED TO SEND 4 BYTES THAT HOLD LENGTH OF THE MESSAGE WE ARE GOING TO WRITE
                IOUtils.write(length, os);
                //WE WRITE MESSAGE ITSELF
                IOUtils.write(serializedMessage, os, StandardCharsets.UTF_8);
                return IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Logger.getInstance().error(String.format(
                    "Could not send message to %s:%d",
                    host,
                    port
            ), e);
            throw new RuntimeException(e);
        }
    }
}
