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

    public Client(String serverAddress, int serverPort, String fileId, int Dc) {
        // Initialisation des attributs
    }

    public void connect() {
        // Connexion au serveur et lancement des téléchargements


        sendMD5();
        assembleFile();
        offerHelp();
    }

    private void requestFileList() {
        // Demander la liste des fichiers
    }

    private void downloadBlock(int blockId) {
        try (
            Socket socket = new Socket(serverAddress, serverPort);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream())
        ) {
            out.writeUTF("BLOCK " + fileId + " " + blockId);
    
            int blockSize = in.readInt(); // Taille réelle reçue
            if (blockSize > 0) {
                byte[] blockData = new byte[blockSize];
                in.readFully(blockData);
    
                blocksReceived.put(blockId, blockData);
                logger.info("Downloaded block " + blockId + " (" + blockSize + " bytes)");
            } else {
                logger.warning("Received empty block or file missing: blockId=" + blockId);
            }
        } catch (IOException e) {
            logger.warning("Error downloading block " + blockId + ": " + e.getMessage());
        }
    }
    
    private void assembleFile() {
        try {
            // Créer un fichier de sortie (par exemple, dans ./client_files/)
            File outputDir = new File("./client_files/");
            if (!outputDir.exists()) {
                outputDir.mkdirs(); // Crée le dossier s'il n'existe pas
            }
    
            File outputFile = new File(outputDir, fileId);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                List<Integer> sortedKeys = new ArrayList<>(blocksReceived.keySet());
                Collections.sort(sortedKeys); // Très important : assembler dans l'ordre des blocs !
    
                for (int blockId : sortedKeys) {
                    byte[] blockData = blocksReceived.get(blockId);
                    fos.write(blockData);
                }
            }
    
            logger.info("File assembled successfully at: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            logger.severe("Error assembling file: " + e.getMessage());
        }
    }
    

    private void sendMD5() {
        // Envoyer le hash MD5 pour vérification
    }

    public void offerHelp() {
        // Devenir un trusted helper
    }

    public void acceptHelpRequest(Token token) {
        // Accepter de l'aide d'un autre client
    }

    private String bytesToHex(byte[] bytes) {
        // Convertir un tableau d'octets en hexadécimal
        return null;
    }
}
