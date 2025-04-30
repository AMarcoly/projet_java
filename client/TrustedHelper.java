package client;

import common.LoggerUtil;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * TrustedHelper is a lightweight helper service that runs in the background to serve file blocks
 * to other clients in a distributed file transfer system, based on token authorization.
 */
public class TrustedHelper {
    private int listeningPort;
    private final Set<String> acceptedTokens = ConcurrentHashMap.newKeySet();
    private final Logger logger = LoggerUtil.getLogger(TrustedHelper.class);
    private final double acceptanceProbability;

    /**
     * Constructs a TrustedHelper instance with the specified listening port and acceptance probability.
     *
     * @param listeningPort Port to listen on (0 for automatic selection)
     * @param acceptanceProbability Probability of accepting help requests (0.0 to 1.0)
     */
    public TrustedHelper(int listeningPort, double acceptanceProbability) {
        this.listeningPort = listeningPort;
        this.acceptanceProbability = acceptanceProbability;
    }

    /**
     * Starts the TrustedHelper in a daemon thread that listens for incoming connections.
     */
    public void start() {
        Thread helperThread = new Thread(this::acceptDownloadRequests);
        helperThread.setDaemon(true);
        helperThread.start();
    }

    /**
     * Accepts incoming download requests on the configured port and delegates handling to worker threads.
     */
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

    /**
     * Handles a request from a single client, either a help request or a token-based block request.
     *
     * @param socket The client socket
     */
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

    /**
     * Handles a HELP_REQUEST message by probabilistically accepting and generating a token.
     *
     * @param request The help request string
     * @param out The output stream to the client
     * @throws IOException If an I/O error occurs
     */
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

    /**
     * Handles a token-based block request and sends the requested block if authorized.
     *
     * @param token The token string provided by the client
     * @param blockRequest The block request command
     * @param out The output stream to the client
     * @throws IOException If an I/O error occurs
     */
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

    /**
     * Gets the port number the TrustedHelper is listening on.
     *
     * @return the listening port number
     */
    public int getListeningPort() {
        return listeningPort;
    }
}
