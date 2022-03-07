package sk.tiku.klient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import sk.tiku.core.dh.DiffieHellmanService;
import sk.tiku.core.encryption.EncryptionService;
import sk.tiku.core.logging.LogRetention;
import sk.tiku.core.logging.Logger;
import sk.tiku.core.model.*;
import sk.tiku.core.networking.SocketClient;
import sk.tiku.core.networking.SocketServer;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

public class TikuClient {
    private KeyPair localDhKeyPair; //klient + klienti - len s inymi klientmi
    private KeyPair serverDhKeyPair; //klient + server - len so serverom
    private final EncryptionService encryptionService = new EncryptionService(true);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private int localPort;
    private int serverPort;
    private String serverHost;
    private SocketClient socketClient;
    private byte[] serverEncryptionKey;

    public static void main(String[] args) {
        new TikuClient().run(args);
    }

    public void run(String[] args) {

        //CLI options
        Options options = new Options();
        Option localPort = new Option("p", "port", true, "http port for the local server to listen");
        localPort.setRequired(true);
        localPort.setType(Number.class);

        Option serverPort = new Option("sp", "server-port", true, "port of the tiku-server node");
        serverPort.setRequired(true);
        serverPort.setType(Number.class);

        Option serverHost = new Option("sh", "server-host", true, "host of the tiku-server node");
        localPort.setRequired(true);

        Option logLevel = new Option("l", "logLevel", true, "Logging level: [ERROR, WARN, INFO, DEBUG]. Default INFO");
        Option help = new Option("h", "help", false, "Print this help");

        options.addOption(localPort);
        options.addOption(serverPort);
        options.addOption(serverHost);
        options.addOption(logLevel);
        options.addOption(help);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption(help)) {
                formatter.printHelp("tiku-server", options);
                System.exit(0);
            }
            if (cmd.hasOption(logLevel)) {
                String logRetention = cmd.getOptionValue(logLevel);
                LogRetention parsedLogRetention = Arrays.stream(LogRetention.values())
                        .filter((lr) -> lr.name().equalsIgnoreCase(logRetention))
                        .findAny()
                        .orElse(null);
                if (parsedLogRetention == null) {
                    System.out.printf("Unknown log retention: %s%n", logRetention);
                    formatter.printHelp("tiku-server", options);
                    System.exit(1);
                }
                Logger.initLogger(parsedLogRetention);
            } else {
                Logger.initLogger(LogRetention.INFO);
            }

            this.localPort = ((Number) cmd.getParsedOptionValue(localPort)).intValue();
            this.serverPort = ((Number) cmd.getParsedOptionValue(serverPort)).intValue();
            this.serverHost = cmd.getOptionValue(serverHost);

            //GENERATE DH KEY PAIR FOR USE WITH OTHER NODES
            localDhKeyPair = DiffieHellmanService.generateKeyPair(null);
            //START LOCAL NODE LISTENING
            SocketServer socketServer = new SocketServer(this.localPort);
            socketServer.start(this::onMessage);

            //CREATE SOCKET CLIENT
            socketClient = new SocketClient();

            MessageData loginMessageData = new MessageData();
            loginMessageData.setType(MessageType.LOGIN);
            loginMessageData.setPayload(Map.of(
                    TikuMessageTypeParams.LOGIN_ARG_HOST, socketServer.getHost(),
                    TikuMessageTypeParams.LOGIN_ARG_PORT, String.valueOf(this.localPort),
                    TikuMessageTypeParams.LOGIN_ARG_PUBKEY, Base64.getEncoder().encodeToString(localDhKeyPair.getPublic().getEncoded())
            ));
            String serializedMessage = serialize(loginMessageData);

            //LOGIN EVENT IS NOT ENCRYPTED
            String response = socketClient.send(this.serverHost, this.serverPort, new CommunicationMessage(serializedMessage, null));
            //FIXME: Error handling
            setupServerEncryptionKey(response);
            //this.serverEncryptionKey = getServerEncryptionKey(response);

            Scanner scanner = new Scanner(System.in);
            String nextLine;
            do {
                System.out.printf("Type %n'q' to exit%n's' to send message using tiku network%n");
                nextLine = scanner.nextLine();
                if (nextLine.equalsIgnoreCase("s")) {
                    System.out.println("URL: ");
                    String url = scanner.nextLine();
                    System.out.println("FIXME! " + url);
                    //FIXME 2. na server poslat  zasifrovanu spravu daj klientov MessageType.GET_RELAY
                    // nezabudni ze odpoved zo server pride zasifrovana
                    String nodeResponse = "odpoved";

//                    List<TikuNode> nodeList = deserialize(nodeResponse, new TypeReference<>() {
//                    });
//
//                    Collections.reverse(nodeList);
                    //for (TikuNode client : nodeList) {


                    //FIXME potrebujes urobit cibulu
//                    MessageData fetchMessageData = new MessageData();
//                    fetchMessageData.setType(MessageType.FETCH);
//                    fetchMessageData.setPayload(Map.of(
//                            TikuMessageTypeParams.FETCH_ARG_URL, url
//                    ));
//                    String sm = serialize(fetchMessageData);
//                    String zas1 - this.encryptionService.encrypt(sm, klucPoslednehoKlienta);
                }
                //FIXME dalej budujes RELAY_NEXT spravy pre dalsich klientov
                // posles prvemu klientovi
                //odpoved treba cibulovo desifrovat
                //}
            } while (!nextLine.equalsIgnoreCase("q"));
            //LOGOUT
            //FIXME: 1. Logout from tiku-server - sifrovat pomocou encryption service
            //zasifrovat a odoslat spravu logout na server
            MessageData logoutMessageData = new MessageData();
            logoutMessageData.setType(MessageType.LOGOUT);
            logoutMessageData.setPayload(Map.of(
                    TikuMessageTypeParams.LOGOUT_ARG_HOST, socketServer.getHost(),
                    TikuMessageTypeParams.LOGOUT_ARG_PORT, String.valueOf(this.localPort),
                    TikuMessageTypeParams.LOGOUT_ARG_PUBKEY, Base64.getEncoder().encodeToString(serverDhKeyPair.getPublic().getEncoded())
            ));
            serializedMessage = serialize(logoutMessageData);
            String encryptedMessage = encryptionService.encrypt(serializedMessage, serverEncryptionKey);

            //LOGOUT EVENT IS ENCRYPTED
            String pubKey = Base64.getEncoder().encodeToString(serverDhKeyPair.getPublic().getEncoded());
            socketClient.send(this.serverHost, this.serverPort, new CommunicationMessage(encryptedMessage, pubKey));

            socketServer.stop();

        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("tiku-server", options);
            System.exit(1);
        }

    }

    private void setupServerEncryptionKey(String response) {
        try {
            PublicKey serverPublicKey = DiffieHellmanService.parsePublicKey(Base64.getDecoder().decode(response));
            KeyPair keyPairForServer = DiffieHellmanService.generateKeyPair(((DHPublicKey) serverPublicKey).getParams());
            KeyAgreement serverKeyAgreement = DiffieHellmanService.initializeAgreement(keyPairForServer.getPrivate());
            serverKeyAgreement.doPhase(serverPublicKey, true);
            serverDhKeyPair = keyPairForServer;
            //serverDhKeyPair = DiffieHellmanService.generateKeyPair(null);
            serverEncryptionKey = serverKeyAgreement.generateSecret();
            //return serverKeyAgreement.generateSecret();
        } catch (InvalidKeyException e) {
            Logger.getInstance().error("Could not agree on server key", e);
            throw new RuntimeException(e);
        }
    }

    private String onMessage(CommunicationMessage message) {
        String messageData;
        byte[] encryptionKey = null;
        if (message.getPubkey() == null) {
            throw new IllegalStateException("Everything must be encrypted. Please do not resist.");
        } else {
            KeyAgreement keyAgreement = DiffieHellmanService.initializeAgreement(localDhKeyPair.getPrivate());
            PublicKey publicKey = DiffieHellmanService.parsePublicKey(Base64.getDecoder().decode(message.getPubkey()));
            try {
                keyAgreement.doPhase(publicKey, true);
                encryptionKey = keyAgreement.generateSecret();
                messageData = encryptionService.decrypt(message.getEncryptedData(), encryptionKey);
            } catch (InvalidKeyException e) {
                Logger.getInstance().error("Could not agree on encryption key", e);
                throw new RuntimeException(e);
            }
        }
        try {
            MessageData data = objectMapper.readValue(messageData, MessageData.class);
            return switch (data.getType()) {
                case RELAY_NEXT -> this.relayNext(data, encryptionKey);
                case FETCH -> this.fetch(data, encryptionKey);
                default -> throw new IllegalStateException("Unknown message type: " + data);
            };
        } catch (JsonProcessingException e) {
            Logger.getInstance().error("Could not parse message data", e);
            throw new RuntimeException(e);
        }
    }

    //fetch - give client list
    private String fetch(MessageData data, byte[] encryptionKey) {
        //FIXME
        return null;
    }

    //relayNext - give next client in list
    private String relayNext(MessageData data, byte[] encryptionKey) {
        //FIXME

        return null;
    }

    //FIXME: Maybe move this to core to new Utils class
    private String serialize(Object toSerialize) {
        try {
            return objectMapper.writeValueAsString(toSerialize);
        } catch (JsonProcessingException e) {
            Logger.getInstance().error("Could not serialize value", e);
            throw new RuntimeException(e);
        }
    }

    private <T> T deserialize(String string, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(string, typeReference);
        } catch (JsonProcessingException e) {
            Logger.getInstance().error("Could not serialize value", e);
            throw new RuntimeException(e);
        }
    }


}
