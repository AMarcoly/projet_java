package client;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class TrustedHelper {
    private int listeningPort;
    private Set<String> acceptedTokens;
    private Logger logger;
    private double acceptanceProbability; // Pc : probabilité d'accepter une aide

    public TrustedHelper(int listeningPort, double acceptanceProbability) {
        this.listeningPort = listeningPort;
        this.acceptedTokens = ConcurrentHashMap.newKeySet();
        this.logger = Logger.getLogger(TrustedHelper.class.getName());
        this.acceptanceProbability = acceptanceProbability;
    }

    public void start() {
        new Thread(this::acceptDownloadRequests).start();
    }

    public void acceptDownloadRequests() {
        try (ServerSocket serverSocket = (listeningPort == 0) ? new ServerSocket(0) : new ServerSocket(listeningPort)) {
            this.listeningPort = serverSocket.getLocalPort(); // Récupérer un port libre si nécessaire
            logger.info("Trusted Helper started on port: " + listeningPort);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleRequest(clientSocket)).start();
            }
        } catch (IOException e) {
            logger.severe("Trusted Helper server error: " + e.getMessage());
        }
    }

    private void handleRequest(Socket clientSocket) {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            String request = in.readUTF();
            logger.info("Received request: " + request);

            if (request.startsWith("HELP_REQUEST")) {
                handleHelpRequest(out);
            }
            // Plus tard, ici : traiter d'autres types de demandes (ex: téléchargement de blocs avec token)

        } catch (IOException e) {
            logger.warning("Error in TrustedHelper.handleRequest: " + e.getMessage());
        }
    }

    private void handleHelpRequest(DataOutputStream out) throws IOException {
        Random rand = new Random();
        if (rand.nextDouble() < acceptanceProbability) {
            // Accepter
            Token token = generateToken(5); // Autoriser par exemple 5 blocs avec ce token
            out.writeUTF("ACCEPT " + token.getTokenId());
            logger.info("Help request accepted, token generated: " + token.getTokenId());
        } else {
            // Refuser
            out.writeUTF("REFUSE");
            logger.info("Help request refused.");
        }
    }

    public Token generateToken(int allowedBlocks) {
        String tokenId = UUID.randomUUID().toString();
        acceptedTokens.add(tokenId);
        return new Token(tokenId, allowedBlocks);
    }

    public int getListeningPort() {
        return listeningPort;
    }
}
