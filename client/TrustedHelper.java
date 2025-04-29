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
    private double acceptanceProbability;

    public TrustedHelper(int listeningPort, double acceptanceProbability) {
        this.listeningPort = listeningPort;
        this.acceptedTokens = ConcurrentHashMap.newKeySet();
        this.logger = Logger.getLogger(TrustedHelper.class.getName());
        this.acceptanceProbability = 0.8; // Probabilité fixée à 80%
    }

    public void start() {
        new Thread(this::acceptDownloadRequests).start();
    }

    public void acceptDownloadRequests() {
        try (ServerSocket serverSocket = (listeningPort == 0) ? new ServerSocket(0) : new ServerSocket(listeningPort)) {
            this.listeningPort = serverSocket.getLocalPort();
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
        try (
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())
        ) {
            String request = in.readUTF();
            logger.info("Received request: " + request);

            if (request.startsWith("HELP_REQUEST")) {
                handleHelpRequest(out);
            } else if (request.startsWith("TOKEN")) {
                String tokenId = request.split(" ")[1];
                String nextLine = in.readUTF();
                handleTokenedBlockRequest(out, tokenId, nextLine);
            }

        } catch (IOException e) {
            logger.warning("Error handling TrustedHelper request: " + e.getMessage());
        }
    }

    private void handleHelpRequest(DataOutputStream out) throws IOException {
        Random rand = new Random();
        if (rand.nextDouble() < acceptanceProbability) {
            Token token = generateToken(5);
            out.writeUTF("ACCEPT " + token.getTokenId());
            logger.info("Help request accepted, token generated: " + token.getTokenId());
        } else {
            out.writeUTF("REFUSE");
            logger.info("Help request refused.");
        }
    }

    private void handleTokenedBlockRequest(DataOutputStream out, String tokenId, String blockRequest) throws IOException {
        if (!acceptedTokens.contains(tokenId)) {
            logger.warning("Invalid token: " + tokenId);
            out.writeInt(0);
            return;
        }

        if (blockRequest.startsWith("BLOCK")) {
            String[] parts = blockRequest.split(" ");
            String fileId = parts[1];
            int blockId = Integer.parseInt(parts[2]);

            File file = new File("./client_files/" + fileId);
            if (!file.exists()) {
                logger.warning("File not found for helper: " + fileId);
                out.writeInt(0);
                return;
            }

            int blockSize = 1024;
            byte[] buffer = new byte[blockSize];

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek((long) blockId * blockSize);
                int bytesRead = raf.read(buffer);

                if (bytesRead > 0) {
                    out.writeInt(bytesRead);
                    out.write(buffer, 0, bytesRead);
                    logger.info("Sent block " + blockId + " using token " + tokenId);
                } else {
                    out.writeInt(0);
                }
            }
        }

        acceptedTokens.remove(tokenId); // Consommer le token après usage
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
