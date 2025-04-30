package client;

import common.LoggerUtil;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class TrustedHelper {
    private int listeningPort;
    private final Set<String> acceptedTokens = ConcurrentHashMap.newKeySet();
    private final Logger logger = LoggerUtil.getLogger(TrustedHelper.class);
    private final double acceptanceProbability;

    public TrustedHelper(int listeningPort, double acceptanceProbability) {
        this.listeningPort = listeningPort;
        this.acceptanceProbability = acceptanceProbability;
    }

    public void start() {
        //new Thread(this::acceptDownloadRequests).start();
        Thread helperThread = new Thread(this::acceptDownloadRequests);
        helperThread.setDaemon(true); // Make it a daemon thread to exit with the main program
        helperThread.start();
    }

    public void acceptDownloadRequests() {
        try (ServerSocket serverSocket = (listeningPort == 0) ? new ServerSocket(0) : new ServerSocket(listeningPort)) {
            this.listeningPort = serverSocket.getLocalPort();
            logger.info("Trusted Helper started on port: " + listeningPort);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (IOException e) {
            logger.severe("Trusted Helper server error: " + e.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        try (
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            String firstRequest = in.readUTF();
            logger.info("Received request: " + firstRequest);

            if (firstRequest.startsWith("HELP_REQUEST")) {
                handleHelpRequest(firstRequest, out);
            } else if (firstRequest.startsWith("TOKEN")) {
                String token = firstRequest.split(" ")[1];
                String next = in.readUTF();
                handleTokenedBlockRequest(token, next, out);
            }

        } catch (IOException e) {
            logger.warning("Error handling client request: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignore) {}
        }
    }

    private void handleHelpRequest(String request, DataOutputStream out) throws IOException {
        if (Math.random() < acceptanceProbability) {
            String tokenId = UUID.randomUUID().toString();
            acceptedTokens.add(tokenId);
            out.writeUTF("ACCEPT " + tokenId);
            logger.info("Accepted HELP request, token generated: " + tokenId);
        } else {
            out.writeUTF("REFUSE");
            logger.info("Refused HELP request.");
        }
    }

    private void handleTokenedBlockRequest(String token, String blockRequest, DataOutputStream out) throws IOException {
        if (!acceptedTokens.contains(token)) {
            logger.warning("Invalid or expired token: " + token);
            out.writeInt(0);
            return;
        }

        if (!blockRequest.startsWith("BLOCK")) {
            logger.warning("Malformed block request: " + blockRequest);
            out.writeInt(0);
            return;
        }

        String[] parts = blockRequest.split(" ");
        String fileId = parts[1];
        int blockId = Integer.parseInt(parts[2]);

        File file = new File("./client_files/" + fileId);
        if (!file.exists()) {
            logger.warning("File not found: " + fileId);
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
                logger.info("Block " + blockId + " sent via token " + token);
            } else {
                logger.warning("Requested block out of range or empty: " + blockId);
                out.writeInt(0);
            }

        } catch (IOException e) {
            logger.warning("Error reading block: " + e.getMessage());
            out.writeInt(0);
        } finally {
            acceptedTokens.remove(token);
        }
    }

    public int getListeningPort() {
        return listeningPort;
    }
}
