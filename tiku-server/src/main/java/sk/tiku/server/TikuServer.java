package sk.tiku.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import sk.tiku.core.dh.DiffieHellmanService;
import sk.tiku.core.encryption.EncryptionService;
import sk.tiku.core.logging.LogRetention;
import sk.tiku.core.logging.Logger;
import sk.tiku.core.model.*;
import sk.tiku.core.networking.SocketServer;

import javax.crypto.KeyAgreement;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;

public class TikuServer {
    private KeyPair dhKeyPair;
    private final Map<String, TikuNode> loggedInClients = new HashMap<>();
    private final EncryptionService encryptionService = new EncryptionService(true);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        new TikuServer().run(args);

    }


    public void run(String[] args) {

        //CLI options
        Options options = new Options();
        Option port = new Option("p", "port", true, "http port for the server to listen");
        port.setRequired(true);
        port.setType(Number.class);

        Option logLevel = new Option("l", "logLevel", true, "Logging level: [ERROR, WARN, INFO, DEBUG]. Default INFO");
        Option help = new Option("h", "help", false, "Print this help");

        options.addOption(port);
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

            //GENERATE DH KEY PAIR
            dhKeyPair = DiffieHellmanService.generateKeyPair(null);
            //START SERVER
            SocketServer socketServer = new SocketServer(((Number) cmd.getParsedOptionValue(port)).intValue());
            socketServer.start(this::onMessage);
            Scanner scanner = new Scanner(System.in);
            String nextLine;
            //WAIT FOR USER TO END APPLICATION
            do {
                System.out.println("Type 'q' to exit.");
                nextLine = scanner.nextLine();
            } while (!nextLine.equalsIgnoreCase("q"));
            socketServer.stop();
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("tiku-server", options);
            System.exit(1);
        }

    }

    private String onMessage(CommunicationMessage message) {
        String messageData;
        PublicKey publicKey; //klientov na server
        byte[] encryptionKey = null; //vypocitany sifrovaci/desifrovaci klient-server
        if (message.getPubkey() == null) {
            //LOGIN MESSAGE IS NOT ENCRYPTED
            messageData = message.getEncryptedData();
        } else {
            //GET DECRYPTION KEY
            KeyAgreement keyAgreement = DiffieHellmanService.initializeAgreement(dhKeyPair.getPrivate());
            publicKey = DiffieHellmanService.parsePublicKey(Base64.getDecoder().decode(message.getPubkey()));
            //System.out.println(publicKey);
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
            //MAKE SURE EVERYTHING IS ENCRYPTED IF POSSIBLE
            MessageData data = objectMapper.readValue(messageData, MessageData.class);
            if (data.getType() != MessageType.LOGIN && message.getPubkey() == null) {
                throw new IllegalStateException("Only LOGIN message can be unencrypted.");
            }
            //DISPATCH MESSAGE
            return switch (data.getType()) {
                case LOGIN -> loginClient(data);
                case LOGOUT -> logoutClient(data);
                case GET_RELAY -> getRelay(encryptionKey);
                //case GET_RELAY -> getRelay(data);
                default -> throw new IllegalStateException("Unknown message type: " + data);
            };
        } catch (JsonProcessingException e) {
            Logger.getInstance().error("Could parse message data", e);
            throw new RuntimeException(e);
        }
    }

    private String loginClient(MessageData data) {
        //Osetrit chybajuce parametre
        //PARSE ARGUMENTS
        TikuNode tn = new TikuNode(
                data.getPayload().get(TikuMessageTypeParams.LOGIN_ARG_HOST),
                Integer.parseInt(data.getPayload().get(TikuMessageTypeParams.LOGIN_ARG_PORT)),
                data.getPayload().get(TikuMessageTypeParams.LOGIN_ARG_PUBKEY)
        );
        //ADD NODE TO MAP
        String nodeId = String.format("%s_%5d", tn.getHost(), tn.getPort());
        loggedInClients.put(nodeId, tn);
        //getRelay();
        return Base64.getEncoder().encodeToString(dhKeyPair.getPublic().getEncoded());
    }

    private String logoutClient(MessageData data) {
        //Osetrit chybajuce parametre
        //Osetrit nejak aby bolo zarucene, ze nemoze niekto odhlasit len tak hocijakeho klienta
        //dalo by sa to mozno spravit tak, ze login okrem vlastneho verejneho kluca odpovie aj nejakym nahodne
        //vygenerovanym tokenom (ktory uz bude zasifrovany). Pre logout ho bude musiet node poslat
        //desifrovat a odobrat klienta z mapy
        String nodeId = String.format(
                "%s_%5d",
                data.getPayload().get(TikuMessageTypeParams.LOGOUT_ARG_HOST),
                Integer.parseInt(data.getPayload().get(TikuMessageTypeParams.LOGOUT_ARG_PORT))
        );
        loggedInClients.remove(nodeId);
        return null;
    }

    //metoda da klientov, ktory su schopni komunikovat
    //MessageData data ako parameter ak by sme chceli urcovat pocet klientov - 3 vymenit za data
    private String getRelay(byte[] encryptionKey) {
        String message = "";
        String node;
        int count = 0;
        List<TikuNode> nodeList = new ArrayList<>();
        for (TikuNode tn : loggedInClients.values()){
//            System.out.println(i.getHost());//            System.out.println(i.getPort());//            System.out.println(i.getPubKey());
            //DVA SPOSOBY: 1-//oddelovac parametrov = ://            node = i.getHost() + ":" + i.getPort() + ":" + i.getPubKey();
            //oddelovac parametrov = ,
            nodeList.add(tn);

            count++;
            if (count == 3)
                break;
        }
        message = serialize(nodeList);
        //System.out.println(message);
//        message = encryptionService.encrypt(message, encryptionKey);
//        System.out.println(message);
        Logger.getInstance().debug(message);
        //zakodovat message pomocou encryptionKey
        return encryptionService.encrypt(message, encryptionKey);
    }

    private String serialize(Object toSerialize) {
        try {
            return objectMapper.writeValueAsString(toSerialize);
        } catch (JsonProcessingException e) {
            Logger.getInstance().error("Could not serialize value", e);
            throw new RuntimeException(e);
        }
    }

}
