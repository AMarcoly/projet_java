package client;

import common.LoggerUtil;
import common.Utils;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class Client {
    private static final String CLIENT_DIR = "./client_files/";
    private String serverAddress;
    private int serverPort;
    private String fileId;
    private int Dc;
    private Map<Integer, byte[]> blocksReceived;
    private TrustedHelper trustedHelper;
    private Logger logger;

    public Client(String serverAddress, int serverPort, String fileId, int Dc) {
        this.logger = LoggerUtil.getLogger(Client.class);
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.fileId = fileId;
        this.Dc = Dc;
        this.blocksReceived = new ConcurrentHashMap<>();
    }


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

    private void downloadBlock(int blockId) {
        final int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            if (tryDownloadBlock(blockId)) return;
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        logger.severe("Failed to download block " + blockId + " after " + maxRetries + " retries.");
    }

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

    private void startTrustedHelper() {
        this.trustedHelper = new TrustedHelper(0, 0.8);
        trustedHelper.start();
        logger.info("Client ready to help others as TrustedHelper on port " + trustedHelper.getListeningPort());

        // attente port attribué
        int attempts = 0;
        while (trustedHelper.getListeningPort() == 0 && attempts < 10) {
            try {
                Thread.sleep(500);
                attempts++;
            } catch (InterruptedException ignored) {}
        }

        if (trustedHelper.getListeningPort() != 0) {
            registerAsHelper();
        } else {
            logger.warning("TrustedHelper port was not assigned.");
        }
    }

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
}
