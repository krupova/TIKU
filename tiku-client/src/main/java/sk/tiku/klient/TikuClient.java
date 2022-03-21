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
import sk.tiku.core.networking.TikuHttpClient;

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
    private final TikuHttpClient tikuHttpClient = new TikuHttpClient();

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
                System.out.printf("Type %n'q' to exit %n's' to comunicate using tiku network%n");
                nextLine = scanner.nextLine();
                if (nextLine.equalsIgnoreCase("s")) {
                    System.out.println("Type URL: ");
                    String url = scanner.nextLine();
                    //Na server sa posle  zasifrovana sprava daj klientov MessageType.GET_RELAY
                    //odpoved zo servera pride zasifrovana
                    String nodeResponse = "odpoved";
                    MessageData getRelayMessageData = new MessageData();
                    getRelayMessageData.setType(MessageType.GET_RELAY);
                    getRelayMessageData.setPayload(Map.of(
                            TikuMessageTypeParams.LOGOUT_ARG_HOST, socketServer.getHost(),
                            TikuMessageTypeParams.LOGOUT_ARG_PORT, String.valueOf(this.localPort),
                            TikuMessageTypeParams.LOGOUT_ARG_PUBKEY, Base64.getEncoder().encodeToString(serverDhKeyPair.getPublic().getEncoded())
                    ));
                    serializedMessage = serialize(getRelayMessageData);
                    String encryptedMessage = encryptionService.encrypt(serializedMessage, serverEncryptionKey);

                    //GET_RELAY EVENT IS ENCRYPTED
                    String pubKey = Base64.getEncoder().encodeToString(serverDhKeyPair.getPublic().getEncoded());
                    nodeResponse = socketClient.send(this.serverHost, this.serverPort, new CommunicationMessage(encryptedMessage, pubKey));

                    Logger.getInstance().debug("nodeResponse: "+nodeResponse);
                    //MessageData data = objectMapper.readValue(nodeResponse, MessageData.class);

//                    String nodeResponse = "odpoved";
                    String decryptedMessage = encryptionService.decrypt(nodeResponse, serverEncryptionKey);
                    Logger.getInstance().debug("decryptedMessage: "+decryptedMessage);
                    List<TikuNode> nodeList = deserialize(decryptedMessage, new TypeReference<>() {
                    });
                    Logger.getInstance().debug("nodeList: "+nodeList.get(0));

                    System.out.println("FIXME! " + url);
//
//                    Collections.reverse(nodeList);
                    //for (TikuNode client : nodeList) {
                    List<Map.Entry<byte[],KeyPair>> encryptionKeys = new ArrayList<>(nodeList.size());
                    for (TikuNode client : nodeList){
                        try {
                            PublicKey nodePublicKey = DiffieHellmanService.parsePublicKey(Base64.getDecoder().decode(client.getPubKey()));
                            KeyPair keyPairForKlient = DiffieHellmanService.generateKeyPair(((DHPublicKey) nodePublicKey).getParams());
                            KeyAgreement clientKeyAgreement = DiffieHellmanService.initializeAgreement(keyPairForKlient.getPrivate());
                            clientKeyAgreement.doPhase(nodePublicKey, true);
                            byte[] klientEncryptionKey = clientKeyAgreement.generateSecret();
                            encryptionKeys.add(new AbstractMap.SimpleEntry<>(klientEncryptionKey,keyPairForKlient));
                        } catch (InvalidKeyException e) {
                            Logger.getInstance().error("Could not agree on server key", e);
                            throw new RuntimeException(e);
                        }
                    }
//FIXME potrebujes urobit cibulu

                    //podla poctu klientov spravim
                    String sprava = "";
                    int pocetKlientov = nodeList.size() - 1;
//                    1. Vygeneruje spravu FETCH. V parametroch su instrukcie co ma fetchnut.
                    MessageData fetchMessageData = new MessageData();
                    fetchMessageData.setType(MessageType.FETCH);
                    fetchMessageData.setPayload(Map.of(
                            TikuMessageTypeParams.FETCH_ARG_URL, url
                    ));
                    //2. Fecth spravu zoserializuje a zasifruje klucom posledneho nodu. (S1)
                    String sm = serialize(fetchMessageData);
                    sprava = this.encryptionService.encrypt(sm, encryptionKeys.get(pocetKlientov).getKey());

                    for (int i = pocetKlientov; i >= 1; i--) {
                        //vyberat z listu - podla toho urcovat pordie klucov
                        //dodat erte asi udaje o klientovi
                        //sprava = this.encryptionService.encrypt(sm, nodeList.get(i).getPubKey()) + sprava;

//                    3.  Vygeneruje sa sprava RELAY_NEXT. V parametroch je sprava S1, adresa a port posledneho nodu.
                        MessageData relayNextMessageData = new MessageData();
                        relayNextMessageData.setType(MessageType.RELAY_NEXT);
                        relayNextMessageData.setPayload(Map.of(
                                TikuMessageTypeParams.RELAY_NEXT_ARG_MESSAGE, sprava,
                                TikuMessageTypeParams.RELAY_NEXT_ARG_IP, nodeList.get(i).getHost(),
                                TikuMessageTypeParams.RELAY_NEXT_ARG_PORT, String.valueOf(nodeList.get(i).getPort()),
                                TikuMessageTypeParams.RELAY_NEXT_ARG_PUBKEY, Base64.getEncoder().encodeToString(encryptionKeys.get(i).getValue().getPublic().getEncoded())
                        ));
                        sm = serialize(relayNextMessageData);
//                      4. RELAY_NEXT zasifruje klucom predposledneho nodu a vznikne sprava S2.
                        sprava = this.encryptionService.encrypt(sm, encryptionKeys.get(i-1).getKey());
//                    5. Tento for pokracuje dalej v generovani RELAY_NEXT sprav. Vzdy sifruje klucom nodu n a do parametrov da spravu ktoru uz ma a adresu n+1 nodu. Postupne vznikne sprava Sn, ktoru zasifruje klucom prveho nodu.
                    }

//                    String nodeId = String.format(
//                            "%s_%5d",
//                            data.getPayload().get(TikuMessageTypeParams.LOGOUT_ARG_HOST),
//                            Integer.parseInt(data.getPayload().get(TikuMessageTypeParams.LOGOUT_ARG_PORT))
//                    );


//                    6. Spravu Sn, posles prvemu nodu.
                    String pubKeyForFirst = Base64.getEncoder().encodeToString(encryptionKeys.get(0).getValue().getPublic().getEncoded());
                    nodeResponse = socketClient.send(nodeList.get(0).getHost(), nodeList.get(0).getPort(), new CommunicationMessage(sprava, pubKeyForFirst));
                    Logger.getInstance().debug("sprava klient klient: "+nodeResponse);
                    //Spravu od Klienta desifrujeme - ziskame finalnu odpoved co sme sa pytali, treba v cykle

                    String finalMessage = nodeResponse;
                    for (Map.Entry<byte[],KeyPair> eK : encryptionKeys) {

                        finalMessage = encryptionService.decrypt(finalMessage, eK.getKey());
                        Logger.getInstance().debug(finalMessage);
                    }
                    System.out.println(finalMessage);
                    //Funkcia relay next - relayNext(MessageData data, byte[] encryptionKey)
                    



                }
                //FIXME dalej budujes RELAY_NEXT spravy pre dalsich klientov
                // posles prvemu klientovi
                //odpoved treba cibulovo desifrovat
                //}

            } while (!nextLine.equalsIgnoreCase("q"));
            //LOGOUT
            //Logout from tiku-server - sifrovat pomocou encryption service
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

    //fetch - get message from url
    private String fetch(MessageData data, byte[] encryptionKey) {
        //FIXME
        //10.Node po desifrovani zisti, ze tam je sprava FETCH
        ////                    11. Urobi FETCH a odpoved zasifruje rovnakym klucom akym spravu desifroval. Vznikne odpoved O1
        ////                    12. Posledny node vrati O1 predposlednemu nodu.
        ////                    13. Predposledny node ju zasifruje vlastnym klucom, ktory pouzil na desifrovanie spravy S2 a vznikne O2, ktoru vrati nodu pred nim.
        ////                    14. Zasifrovana odpoved On sa takymto sposobom dostane az na klienta, ktory zacal komunikaciu.
        ////                    15. Klient musi spravu desifrovat klucmi, ktorymi sifroval spravy Sn az S1 (v rovnakom poradi. Po desifrovani vsetkymi klucmi v spravnom poradi dostane odpoved, ktoru posledny node ziskal pomocou vykonania FETCH-u
        ////                    15. Zaroven je zarucene, ze sprava presla vsetkymi nodami v spravnom poradi (lebo inak by niektoru spravu nedel desifrovat)
        String message = tikuHttpClient.get(data.getPayload().get(TikuMessageTypeParams.FETCH_ARG_URL));
        Logger.getInstance().debug("Web response> "+message);
        return encryptionService.encrypt(message, encryptionKey);
    }

    //relayNext - send message to next client
    private String relayNext(MessageData data, byte[] encryptionKey) {
        //FIXME
        Logger.getInstance().debug("sprava klient klientovi" + data);
        //            7. Prvy node vlastnym klucom desifruje spravu Sn a zisti, ze jej obsahom je RELAY_NEXT sprava pre Sn-1 (zasifrovana a on ju nevie desifrovat).
//                    8. Prvy node preposle spravu druhemu nodovi a ako pubkey spravy nastavi verejny kluc prveho nodu
//                    9. Sprava takymto sposobom cestuje sietou az k poslednemu nodu, kde je z nej uz len sprava S1
        String pubKeyForAnother = data.getPayload().get(TikuMessageTypeParams.RELAY_NEXT_ARG_PUBKEY);
        String nodeResponse = socketClient.send(
                data.getPayload().get(TikuMessageTypeParams.RELAY_NEXT_ARG_IP),
                Integer.parseInt(data.getPayload().get(TikuMessageTypeParams.RELAY_NEXT_ARG_PORT)),
                new CommunicationMessage(
                        data.getPayload().get(TikuMessageTypeParams.RELAY_NEXT_ARG_MESSAGE),
                        pubKeyForAnother
                )
        );
        Logger.getInstance().debug("sprava klient klient: "+ nodeResponse);
        return encryptionService.encrypt(nodeResponse, encryptionKey);
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
