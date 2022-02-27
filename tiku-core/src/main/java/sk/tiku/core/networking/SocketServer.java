package sk.tiku.core.networking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import sk.tiku.core.logging.Logger;
import sk.tiku.core.model.CommunicationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

/**
 * Simple wrapper around sockets to simplify implementation of network communication.
 * <p>
 * To send messages use {@link SocketClient} on the other end of the communication
 */
public class SocketServer {
    /**
     * Port, where server listens
     */
    private final int port;
    /**
     * Thread pool for working threads
     */
    private final ExecutorService workingThreadsExecutor;
    /**
     * Thread pool for processing of incomming connections
     */
    private final ExecutorService serverThreadExecutor;
    /**
     * Object mapper for JSON serialization and deserialization
     */
    private final ObjectMapper objectMapper;
    /**
     * Native java {@link ServerSocket} that backs this implementation
     */
    private ServerSocket server;
    /**
     * Indicator if server is running or stopping
     */
    private boolean running;

    /**
     * Create server with port number
     *
     * @param port Port number, where server should listen
     */
    public SocketServer(int port) {
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.workingThreadsExecutor = Executors.newFixedThreadPool(10);
        this.serverThreadExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Start listening for messages.
     *
     * @param onMessage Server calls this function each received message. Return value is used as response
     */
    public void start(Function<CommunicationMessage, String> onMessage) {
        try {
            Logger.getInstance().info(String.format("Starting server on port %d", port));
            server = new ServerSocket(port);
            server.setReuseAddress(true);
            running = true;
            //EXECUTE OUTSIDE OF MAIN THREAD
            serverThreadExecutor.execute(() -> {
                while (running) {
                    try {
                        Socket connection = server.accept();
                        Logger.getInstance().debug(String.format(
                                "New connection from %s:%d",
                                connection.getInetAddress().getHostAddress(),
                                connection.getPort()
                        ));
                        workingThreadsExecutor.execute(() -> {
                            try {
                                Logger.getInstance().debug(String.format(
                                        "Processing connection from %s:%d",
                                        connection.getInetAddress().getHostAddress(),
                                        connection.getPort()
                                ));
                                try (InputStream is = connection.getInputStream();
                                     OutputStream os = connection.getOutputStream()) {
                                    ByteBuffer buffer = ByteBuffer.allocate(4);
                                    IOUtils.read(is, buffer.array());
                                    int length = buffer.getInt();
                                    byte[] inputBuffer = IOUtils.readFully(is, length);
                                    String inputData = IOUtils.toString(inputBuffer, StandardCharsets.UTF_8.name());
                                    CommunicationMessage communicationMessage = objectMapper.readValue(
                                            inputData,
                                            CommunicationMessage.class
                                    );
                                    String response = onMessage.apply(communicationMessage);
                                    Logger.getInstance().debug(String.format(
                                            "Sending response to %s:%d",
                                            connection.getInetAddress().getHostAddress(),
                                            connection.getPort()
                                    ));
                                    IOUtils.write(response, os, StandardCharsets.UTF_8);
                                    Logger.getInstance().debug(String.format(
                                            "Sent response to %s:%d",
                                            connection.getInetAddress().getHostAddress(),
                                            connection.getPort()
                                    ));

                                } catch (IOException e) {
                                    Logger.getInstance().error(String.format(
                                            "Cannot process connection from %s:%d",
                                            connection.getInetAddress().getHostAddress(),
                                            connection.getPort()
                                    ), e);
                                }
                            } catch (RuntimeException ex) {
                                Logger.getInstance().error("Exception while processing message", ex);
                            } finally {
                                try {
                                    connection.close();
                                } catch (IOException e) {
                                    Logger.getInstance().error("Could not close connection", e);
                                }
                            }
                        });
                    } catch (IOException e) {
                        if (!running && e instanceof SocketException) {
                            Logger.getInstance().debug("Last accept call has been interrupted because server is stopping");
                        } else {
                            Logger.getInstance().error("Could not accept connection.", e);
                        }
                    }
                }
            });
        } catch (Exception e) {
            Logger.getInstance().error("Could not start server.", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Stop server and shutdown all working threads
     */
    public void stop() {
        try {
            Logger.getInstance().info("Stopping server");
            running = false;
            server.close();
            serverThreadExecutor.shutdown();
            workingThreadsExecutor.shutdown();
            Logger.getInstance().info("Server stopped");
        } catch (IOException e) {
            Logger.getInstance().error("Could not stop server.", e);
        }
    }

    /**
     * Get host server is listening on
     *
     * @return Hostname of ip
     */
    public String getHost() {
        return server.getInetAddress().getHostAddress();
    }

}
