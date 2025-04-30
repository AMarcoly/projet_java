package client;

import common.LoggerUtil;
import common.Utils;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Client class responsible for connecting to a server to download a file in multiple blocks,
 * verifying the file with MD5, and optionally acting as a trusted helper for other clients.
 */
public class Client {
    private static final String CLIENT_DIR = "./client_files/";
    private String serverAddress;
    private int serverPort;
    private String fileId;
    private int Dc;
    private Map<Integer, byte[]> blocksReceived;
    private TrustedHelper trustedHelper;
    private Logger logger;

    /**
     * Constructs a Client instance with given server address, port, file ID, and number of connections.
     *
     * @param serverAddress IP address of the server
     * @param serverPort Port number of the server
     * @param fileId ID or name of the file to download
     * @param Dc Number of parallel download connections
     */
    public Client(String serverAddress, int serverPort, String fileId, int Dc) {
        this.logger = LoggerUtil.getLogger(Client.class);
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.fileId = fileId;
        this.Dc = Dc;
        this.blocksReceived = new ConcurrentHashMap<>();
    }

    /**
     * Starts the file download process, assembles the file, sends MD5, and launches the helper.
     */
    public void connect() {
        try {
            List<String> availableFiles = requestFileList();
            if (!availableFiles.contains(fileId)) {
                logger.severe("File " + fileId + " not found on server.");
                return;
            }

            ExecutorService executor = Executors.newFixedThreadPool(Dc);
            for (int i = 0; i < Dc; i++) {
                int blockId = i;
                executor.submit(() -> downloadBlock(blockId));
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);

            logger.info("All downloads finished. Blocks received: " + blocksReceived.size());

            assembleFile();
            sendMD5();
            startTrustedHelper();

        } catch (Exception e) {
            logger.severe("Client connection error: " + e.getMessage());
        }
    }

    /**
     * Requests the list of available files from the server.
     *
     * @return List of file names available on the server
     */
    private List<String> requestFileList() {
        try (Socket socket = new Socket(serverAddress, serverPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("LIST");
            String response = in.readUTF();
            logger.info("Available files: " + response);
            return Arrays.asList(response.split(";"));

        } catch (IOException e) {
            logger.warning("Error requesting file list: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Attempts to download a specific block with retries.
     *
     * @param blockId Block number to download
     */
    private void downloadBlock(int blockId) {
        final int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            if (tryDownloadBlock(blockId)) return;
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        logger.severe("Failed to download block " + blockId + " after " + maxRetries + " retries.");
    }

    /**
     * Tries to download a block either from server or helper.
     *
     * @param blockId Block number to download
     * @return true if the block was successfully downloaded, false otherwise
     */
    private boolean tryDownloadBlock(int blockId) {
        try (Socket socket = new Socket(serverAddress, serverPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("BLOCK " + fileId + " " + blockId);

            String response = in.readUTF();
            switch (response) {
                case "OK" -> {
                    int blockSize = in.readInt();
                    if (blockSize > 0) {
                        byte[] data = new byte[blockSize];
                        in.readFully(data);
                        blocksReceived.put(blockId, data);
                        logger.info("Downloaded block " + blockId + " (" + blockSize + " bytes)");
                        return true;
                    }
                    logger.warning("Received empty block " + blockId);
                }
                case "FAILURE" -> logger.warning("Server delegation failed for block " + blockId);
                default -> {
                    if (response.startsWith("DELEGATED")) {
                        String[] parts = response.split(" ");
                        downloadBlockFromHelper(parts[1], Integer.parseInt(parts[2]), parts[3], blockId);
                        return true;
                    } else {
                        logger.warning("Unknown response for block " + blockId + ": " + response);
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("Error downloading block " + blockId + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Downloads a block from a helper node.
     *
     * @param ip Helper IP address
     * @param port Helper port
     * @param token Authentication token
     * @param blockId ID of the block to download
     */
    private void downloadBlockFromHelper(String ip, int port, String token, int blockId) {
        try (Socket socket = new Socket(ip, port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("TOKEN " + token);
            out.writeUTF("BLOCK " + fileId + " " + blockId);

            int blockSize = in.readInt();
            if (blockSize > 0) {
                byte[] data = new byte[blockSize];
                in.readFully(data);
                blocksReceived.put(blockId, data);
                logger.info("Downloaded block " + blockId + " from helper (" + blockSize + " bytes)");
            } else {
                logger.warning("Empty block received from helper for block " + blockId);
            }

        } catch (IOException e) {
            logger.warning("Error contacting helper for block " + blockId + ": " + e.getMessage());
        }
    }

    /**
     * Assembles all downloaded blocks into the final file on disk.
     */
    private void assembleFile() {
        try {
            File outputDir = new File(CLIENT_DIR);
            if (!outputDir.exists()) outputDir.mkdirs();

            File outputFile = new File(outputDir, fileId);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                blocksReceived.keySet().stream().sorted().forEach(key -> {
                    try {
                        fos.write(blocksReceived.get(key));
                    } catch (IOException e) {
                        logger.severe("Error writing block " + key + ": " + e.getMessage());
                    }
                });
            }
            logger.info("File assembled successfully at: " + outputFile.getAbsolutePath());
            logger.info("Number of blocks assembled: " + blocksReceived.size());

        } catch (IOException e) {
            logger.severe("Error assembling file: " + e.getMessage());
        }
    }

    /**
     * Computes and sends the MD5 hash of the assembled file to the server.
     */
    private void sendMD5() {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            blocksReceived.keySet().stream().sorted().forEach(key -> md.update(blocksReceived.get(key)));

            String md5Hex = Utils.bytesToHex(md.digest());

            try (Socket socket = new Socket(serverAddress, serverPort);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println("MD5 " + fileId + " " + md5Hex);
            }

            logger.info("MD5 sent: " + md5Hex);

        } catch (Exception e) {
            logger.severe("Error computing or sending MD5: " + e.getMessage());
        }
    }

    /**
     * Starts a trusted helper to serve blocks to other clients and registers it on the server.
     */
    private void startTrustedHelper() {
        this.trustedHelper = new TrustedHelper(0, 0.8);
        trustedHelper.start();

        int attempts = 0;
        while (trustedHelper.getListeningPort() == 0 && attempts < 10) {
            try {
                Thread.sleep(500);
                attempts++;
            } catch (InterruptedException ignored) {}
        }

        logger.info("Client ready to help others as TrustedHelper on port " + trustedHelper.getListeningPort());

        if (trustedHelper.getListeningPort() != 0) {
            registerAsHelper();
        } else {
            logger.warning("TrustedHelper port was not assigned.");
        }
    }

    /**
     * Registers the client as a helper on the server.
     */
    private void registerAsHelper() {
        try (Socket socket = new Socket(serverAddress, serverPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String localIp = "127.0.0.1";
            int port = trustedHelper.getListeningPort();

            out.writeUTF("REGISTER_HELPER " + fileId + " " + localIp + " " + port);
            logger.info("Registered as TrustedHelper: " + localIp + ":" + port);

        } catch (IOException e) {
            logger.warning("Failed to register helper: " + e.getMessage());
        }
    }

    /**
     * Entry point of the client program. Parses command-line arguments and starts the client.
     *
     * @param args Command-line arguments (e.g., --ip=..., --port=..., --file=..., --DC=...)
     */
    public static void main(String[] args) {
        String ip = "127.0.0.1";
        int port = 5000;
        String fileName = "file1.txt";
        int dc = 2;

        for (String arg : args) {
            if (arg.startsWith("--ip=")) {
                ip = arg.substring("--ip=".length());
            } else if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--file=")) {
                fileName = arg.substring("--file=".length()).replace("\"", "");
            } else if (arg.startsWith("--DC=")) {
                dc = Integer.parseInt(arg.substring("--DC=".length()));
            }
        }

        Client client = new Client(ip, port, fileName, dc);
        client.connect();
    }
}
