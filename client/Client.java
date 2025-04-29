package client;

import common.Utils;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class Client {
    private String serverAddress;
    private int serverPort;
    private String fileId;
    private int Dc;
    private Map<Integer, byte[]> blocksReceived;
    private TrustedHelper trustedHelper;
    private Logger logger;

    public Client(String[] args) {
        this.serverAddress = "127.0.0.1"; // Valeur par défaut
        this.serverPort = 5000;
        this.fileId = "file1.txt";
        this.Dc = 2;
        this.logger = Logger.getLogger(Client.class.getName());
        this.blocksReceived = new ConcurrentHashMap<>();

        // Parser les arguments
        for (String arg : args) {
            if (arg.startsWith("--server=")) {
                String[] parts = arg.substring(9).split(":");
                this.serverAddress = parts[0];
                if (parts.length > 1) this.serverPort = Integer.parseInt(parts[1]);
            }
            else if (arg.startsWith("--file=")) {
                this.fileId = arg.substring(7);
            }
            else if (arg.startsWith("--dc=")) {
                this.Dc = Integer.parseInt(arg.substring(5));
            }
        }
    }

    public void connect() {
        try {
            requestFileList();
    
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
    
            offerHelp();
    
            // Attendre que le TrustedHelper démarre et attribue son port
            int attempts = 0;
            while (trustedHelper.getListeningPort() == 0 && attempts < 10) {
                Thread.sleep(500);
                attempts++;
            }
    
            if (trustedHelper.getListeningPort() == 0) {
                logger.warning("Failed to start TrustedHelper after waiting.");
            } else {
                registerAsHelper();
            }
    
        } catch (Exception e) {
            logger.severe("Client connection error: " + e.getMessage());
        }
    }
    

    private void registerAsHelper() {
        try (Socket socket = new Socket(serverAddress, serverPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
    
            String localIp = "127.0.0.1"; //InetAddress.getLocalHost().getHostAddress();
            int helperPort = trustedHelper.getListeningPort();
    
            out.writeUTF("REGISTER_HELPER " + fileId + " " + localIp + " " + helperPort);
            logger.info("Registered as TrustedHelper: " + localIp + ":" + helperPort);
    
        } catch (IOException e) {
            logger.warning("Failed to register helper: " + e.getMessage());
        }
    }
    

    private void requestFileList() {
        try (Socket socket = new Socket(serverAddress, serverPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("LIST");
            String response = in.readUTF();
            logger.info("Available files: " + response);

        } catch (IOException e) {
            logger.warning("Error requesting file list: " + e.getMessage());
        }
    }
    private void downloadBlock(int blockId) {
        int retries = 0;
        final int maxRetries = 3;
        final long retryDelay = 1000; // 1 second
        
        while (retries < maxRetries) {
            try (Socket socket = new Socket(serverAddress, serverPort);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                
                out.writeUTF("BLOCK " + fileId + " " + blockId);
                
                String response = in.readUTF();
                if (response.startsWith("DELEGATED")) {
                    String[] parts = response.split(" ");
                    String helperIp = parts[1];
                    int helperPort = Integer.parseInt(parts[2]);
                    String token = parts[3];
                    
                    logger.info("Delegated download received. Contacting helper...");
                    downloadBlockFromHelper(helperIp, helperPort, token, blockId);
                    return;
                } 
                else if (response.equals("OK")) {
                    int blockSize = in.readInt();
                    if (blockSize > 0) {
                        byte[] blockData = new byte[blockSize];
                        in.readFully(blockData);
                        blocksReceived.put(blockId, blockData);
                        logger.info("Downloaded block " + blockId + " (" + blockSize + " bytes)");
                        return;
                    } else {
                        logger.warning("Empty block received for " + blockId);
                        return;
                    }
                }
                
            } catch (IOException e) {
                logger.warning("Error downloading block " + blockId + " (attempt " + (retries+1) + "): " + e.getMessage());
            }
            
            // Wait before retrying
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            retries++;
        }
        
        logger.severe("Failed to download block " + blockId + " after " + maxRetries + " retries.");
    }

    private void retryDownloadBlock(int blockId) {
        try {
            Thread.sleep(1000);
            downloadBlock(blockId);
        } catch (InterruptedException e) {
            logger.warning("Retry interrupted for block " + blockId);
        }
    }

    private void downloadBlockFromHelper(String ip, int port, String token, int blockId) {
        try (Socket socket = new Socket(ip, port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("TOKEN " + token);
            out.writeUTF("BLOCK " + fileId + " " + blockId);

            int blockSize = in.readInt();
            if (blockSize > 0) {
                byte[] blockData = new byte[blockSize];
                in.readFully(blockData);
                blocksReceived.put(blockId, blockData);
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
            File outputDir = new File("./client_files/");
            if (!outputDir.exists()) outputDir.mkdirs();
            
            File outputFile = new File(outputDir, fileId);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                // Trier les blocs par ID avant assemblage
                blocksReceived.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        try {
                            fos.write(entry.getValue());
                        } catch (IOException e) {
                            logger.severe("Error writing block " + entry.getKey());
                        }
                    });
            }
            logger.info("File assembled. Total blocks: " + blocksReceived.size());
        } catch (IOException e) {
            logger.severe("File assembly failed: " + e.getMessage());
        }
    }

    private void sendMD5() {
        try {
            List<Integer> sortedKeys = new ArrayList<>(blocksReceived.keySet());
            Collections.sort(sortedKeys);

            MessageDigest md = MessageDigest.getInstance("MD5");
            for (int key : sortedKeys) {
                md.update(blocksReceived.get(key));
            }

            byte[] digest = md.digest();
            String md5Hex = Utils.bytesToHex(digest);

            try (Socket socket = new Socket(serverAddress, serverPort);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println("MD5 " + fileId + " " + md5Hex);
            }

            logger.info("MD5 sent: " + md5Hex);

        } catch (Exception e) {
            logger.severe("Error computing or sending MD5: " + e.getMessage());
        }
    }

    public void offerHelp() {
        this.trustedHelper = new TrustedHelper(0, 0.8);
        trustedHelper.start();
        logger.info("Client ready to help others as TrustedHelper on port " + trustedHelper.getListeningPort());
    }

    // Méthode main
    public static void main(String[] args) {
        Client client = new Client(args);
        client.connect();
    }
}
