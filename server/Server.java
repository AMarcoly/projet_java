package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

public class Server {
    private int port;
    private int capacityCs;
    private Map<String, List<ClientInfo>> trustedClients;
    private BlockingQueue<Request> queue;
    private AtomicInteger activeRequests;
    private Random randomFailure;
    private Logger logger;

    public Server(int port, int capacityCs) {
        this.port = port;
        this.capacityCs = capacityCs;
        this.trustedClients = new ConcurrentHashMap<>();
        this.queue = new LinkedBlockingQueue<>();
        this.activeRequests = new AtomicInteger(0);
        this.randomFailure = new Random();
        this.logger = Logger.getLogger(Server.class.getName());
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("Server started on port " + port);

            new Thread(this::monitorConnections).start();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connected: " + clientSocket.getInetAddress());

                if (activeRequests.get() < capacityCs) {
                    activeRequests.incrementAndGet();
                    new Thread(() -> {
                        try {
                            handleRequest(clientSocket);
                        } finally {
                            activeRequests.decrementAndGet();
                            processNextInQueue();
                        }
                    }).start();
                } else {
                    logger.warning("Capacity reached. Trying delegation...");
                    if (!tryDelegateRequest(new Request(clientSocket))) {
                        logger.warning("Delegation failed. Adding to queue.");
                        queue.add(new Request(clientSocket));
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("Server error: " + e.getMessage());
        }
    }

    private void handleRequest(Socket clientSocket) {
        try (
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())
        ) {
            String request = in.readUTF();
            logger.info("Received request: " + request);

            if (request.startsWith("BLOCK")) {
                String[] parts = request.split(" ");
                String fileId = parts[1];
                int blockId = Integer.parseInt(parts[2]);

                sendBlock(out, fileId, blockId);

            } else if (request.startsWith("LIST")) {
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                writer.println("file1.txt;file2.txt;file3.txt"); // Exemple statique

            } else if (request.startsWith("MD5")) {
                String[] parts = request.split(" ");
                String fileId = parts[1];
                String clientMd5 = parts[2];

                logger.info("Received MD5 for file " + fileId + ": " + clientMd5);
                // TODO : Vérifier MD5 serveur vs clientMd5 pour validation
            }

            clientSocket.close();
        } catch (IOException e) {
            logger.warning("Error handling client request: " + e.getMessage());
        }
    }

    private void sendBlock(DataOutputStream out, String fileId, int blockId) {
        try {
            File file = new File("./server_files/" + fileId);
            if (!file.exists()) {
                logger.warning("Requested file not found: " + fileId);
                out.writeInt(0);
                return;
            }

            int blockSize = 1024; // 1KB
            byte[] buffer = new byte[blockSize];

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek((long) blockId * blockSize);
                int bytesRead = raf.read(buffer);

                if (bytesRead == -1) {
                    logger.warning("Requested block not available: blockId=" + blockId);
                    out.writeInt(0);
                } else {
                    out.writeInt(bytesRead);
                    out.write(buffer, 0, bytesRead);
                    logger.info("Block " + blockId + " sent (" + bytesRead + " bytes)");
                }
            }
        } catch (IOException e) {
            logger.warning("Error sending block: " + e.getMessage());
        }
    }

    private void monitorConnections() {
        while (true) {
            try {
                Thread.sleep(5000);
                if (randomFailure.nextDouble() < 0.2) {
                    closeRandomConnection();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Monitor thread interrupted");
                break;
            }
        }
    }

    private void closeRandomConnection() {
        logger.warning("Random connection closure simulation (not yet implemented)");
        // TODO : Gérer une vraie fermeture aléatoire parmi les connexions actives
    }

    private void processNextInQueue() {
        Request nextRequest = queue.poll();
        if (nextRequest != null) {
            logger.info("Processing request from queue.");
            activeRequests.incrementAndGet();
            new Thread(() -> {
                try {
                    handleRequest(nextRequest.getClientSocket());
                } finally {
                    activeRequests.decrementAndGet();
                    processNextInQueue(); // Récursif si plusieurs en attente
                }
            }).start();
        }
    }

    private boolean tryDelegateRequest(Request request) {
        List<ClientInfo> helpers = trustedClients.get(request.getRequestedFile());
        if (helpers == null || helpers.isEmpty()) {
            logger.info("No trusted clients available for delegation.");
            return false;
        }

        for (ClientInfo helper : helpers) {
            if (contactTrustedClient(helper, request)) {
                logger.info("Delegated request to trusted client: " + helper.getIp() + ":" + helper.getPort());
                return true;
            }
        }
        return false;
    }

    private boolean contactTrustedClient(ClientInfo helper, Request request) {
        try (Socket socket = new Socket(helper.getIp(), helper.getPort());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("HELP_REQUEST " + request.getRequestedFile() + " " + request.getBlockId());

            String response = in.readUTF();
            return "ACCEPT".equals(response);

        } catch (IOException e) {
            logger.warning("Failed contacting trusted client: " + e.getMessage());
            return false;
        }
    }
}

// package server;

// import java.io.*;
// import java.net.*;
// import java.util.*;
// import java.util.concurrent.*;
// import java.util.concurrent.atomic.AtomicInteger;
// import java.util.logging.*;

// public class Server {
//     private int port;
//     private int capacityCs;
//     private Map<String, List<ClientInfo>> trustedClients;
//     private BlockingQueue<Request> queue;
//     private Random randomFailure;
//     private Logger logger;
//     private AtomicInteger activeRequests = new AtomicInteger(0);

//     public Server(int port, int capacityCs) {
//         // Initialisation des attributs
//     }

//     public void start() {
//         try (ServerSocket serverSocket = new ServerSocket(port)) {
//             logger.info("Server started on port " + port);
    
//             new Thread(this::monitorConnections).start();
    
//             while (true) {
//                 Socket clientSocket = serverSocket.accept();
//                 logger.info("New client connected: " + clientSocket.getInetAddress());
    
//                 if (activeRequests.get() < capacityCs) {
//                     activeRequests.incrementAndGet();
//                     new Thread(() -> {
//                         try {
//                             handleRequest(clientSocket);
//                         } finally {
//                             activeRequests.decrementAndGet();
//                             processNextInQueue();
//                         }
//                     }).start();
//                 } else {
//                     logger.warning("Capacity reached, adding request to queue");
//                     if (!tryDelegateRequest(new Request(clientSocket))) {
//                         queue.add(new Request(clientSocket));
//                     }
                    
//                 }
//             }
//         } catch (IOException e) {
//             logger.severe("Server error: " + e.getMessage());
//         }
//     }
    
//     private void processNextInQueue() {
//         Request nextRequest = queue.poll(); // Récupère et enlève l'élément en tête
//         if (nextRequest != null) {
//             logger.info("Processing next request from queue.");
//             activeRequests.incrementAndGet();
//             new Thread(() -> {
//                 try {
//                     handleRequest(nextRequest.getClientSocket());
//                 } finally {
//                     activeRequests.decrementAndGet();
//                     processNextInQueue(); // Enchaîner s’il reste d’autres en attente
//                 }
//             }).start();
//         }
//     }
    
//     private void handleRequest(Socket clientSocket) {
//         try (
//             DataInputStream in = new DataInputStream(clientSocket.getInputStream());
//             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())
//         ) {
//             String request = in.readUTF();
//             logger.info("Received request: " + request);
    
//             if (request.startsWith("BLOCK")) {
//                 // Exemple de format reçu: "BLOCK fileId blockId"
//                 String[] parts = request.split(" ");
//                 String fileId = parts[1];
//                 int blockId = Integer.parseInt(parts[2]);
    
//                 sendBlock(out, fileId, blockId);
    
//             } else if (request.startsWith("LIST")) {
//                 // Envoyer la liste de fichiers disponibles
//                 PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
//                 writer.println("file1.txt;file2.txt;file3.txt"); // (à améliorer si besoin)
//             } else if (request.startsWith("MD5")) {
//                 // Le client a terminé le téléchargement
//                 String[] parts = request.split(" ");
//                 String fileId = parts[1];
//                 String clientMd5 = parts[2];
    
//                 // TODO: vérifier le MD5 serveur vs clientMd5 pour valider la copie
//                 logger.info("Received MD5 for file " + fileId + ": " + clientMd5);
//             }
    
//             clientSocket.close();
//         } catch (IOException e) {
//             logger.warning("Error handling client request: " + e.getMessage());
//         }
//     }
    
//     private void sendBlock(DataOutputStream out, String fileId, int blockId) {
//         try {
//             File file = new File("./server_files/" + fileId); // Supposons que tous les fichiers sont ici
//             if (!file.exists()) {
//                 logger.warning("Requested file not found: " + fileId);
//                 out.writeInt(0);
//                 return;
//             }
    
//             int blockSize = 1024; // 1KB par bloc
//             byte[] buffer = new byte[blockSize];
    
//             try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
//                 raf.seek((long) blockId * blockSize);
//                 int bytesRead = raf.read(buffer);
    
//                 if (bytesRead == -1) {
//                     logger.warning("Requested block not available: blockId=" + blockId);
//                     out.writeInt(0);
//                 } else {
//                     out.writeInt(bytesRead);
//                     out.write(buffer, 0, bytesRead);
//                     logger.info("Block " + blockId + " sent successfully (" + bytesRead + " bytes)");
//                 }
//             }
//         } catch (IOException e) {
//             logger.warning("Error sending block: " + e.getMessage());
//         }
//     }
    
//     private boolean tryDelegateRequest(Request request) {
//         // Supposons que request.requestedFile a été rempli
//         List<ClientInfo> helpers = trustedClients.get(request.getRequestedFile());
//         if (helpers == null || helpers.isEmpty()) {
//             logger.info("No trusted clients available for delegation.");
//             return false;
//         }
    
//         for (ClientInfo helper : helpers) {
//             if (contactTrustedClient(helper, request)) {
//                 logger.info("Delegated request to trusted client: " + helper.getIp() + ":" + helper.getPort());
//                 return true; // Délégation réussie
//             }
//         }
//         return false; // Aucun helper n'a accepté
//     }

//     private boolean contactTrustedClient(ClientInfo helper, Request request) {
//         try (Socket socket = new Socket(helper.getIp(), helper.getPort());
//              DataOutputStream out = new DataOutputStream(socket.getOutputStream());
//              DataInputStream in = new DataInputStream(socket.getInputStream())) {
    
//             // Exemple simple : envoyer une demande et recevoir une réponse
//             out.writeUTF("HELP_REQUEST " + request.getRequestedFile() + " " + request.getBlockId());
    
//             String response = in.readUTF();
//             return "ACCEPT".equals(response);
    
//         } catch (IOException e) {
//             logger.warning("Failed contacting trusted client: " + e.getMessage());
//             return false;
//         }
//     }
    
    

//     private void monitorConnections() {
//         // Simulation des déconnexions aléatoires
//     }

//     private void closeRandomConnection() {
//         // Fermer une connexion au hasard
//     }

//     private int getCurrentActiveRequests() {
//         // Retourner le nombre de requêtes actives
//         return 0;
//     }
// }
