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
    private final Set<Socket> activeConnections = ConcurrentHashMap.newKeySet();

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
                activeConnections.add(clientSocket);

                if (activeRequests.get() < capacityCs) {
                    activeRequests.incrementAndGet();
                    new Thread(() -> {
                        try {
                            handleRequest(clientSocket);
                        } finally {
                            activeRequests.decrementAndGet();
                            activeConnections.remove(clientSocket);
                            processNextInQueue();
                        }
                    }).start();
                } else {
                    logger.warning("Capacity reached. Trying delegation...");
                    if (!tryDelegateRequest(new Request(clientSocket))) {
                        logger.warning("Delegation failed. Sending FAILURE to client.");
                        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                        out.writeUTF("FAILURE");
                        clientSocket.close();
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

                out.writeUTF("OK"); // important : signaler que c'est un envoi direct
                sendBlock(out, fileId, blockId);

            } else if (request.startsWith("LIST")) {
                out.writeUTF("file1.txt");
                logger.info("Sent file list to client.");

            } else if (request.startsWith("MD5")) {
                String[] parts = request.split(" ");
                String fileId = parts[1];
                String clientMd5 = parts[2];
                logger.info("Received MD5 for file " + fileId + ": " + clientMd5);
            }else if (request.startsWith("REGISTER_HELPER")) {
                String[] parts = request.split(" ");
                String fileId = parts[1];
                String ip = parts[2];
                int port = Integer.parseInt(parts[3]);
            
                ClientInfo helperInfo = new ClientInfo(ip, port);
                trustedClients.computeIfAbsent(fileId, k -> new ArrayList<>()).add(helperInfo);
            
                logger.info("Registered Trusted Helper for file " + fileId + ": " + ip + ":" + port);
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
                out.writeInt(0);
                return;
            }

            int blockSize = 1024;
            byte[] buffer = new byte[blockSize];

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek((long) blockId * blockSize);
                int bytesRead = raf.read(buffer);

                if (bytesRead == -1) {
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
        if (activeConnections.isEmpty()) {
            logger.info("No active connections to close.");
            return;
        }

        List<Socket> connections = new ArrayList<>(activeConnections);
        Socket toClose = connections.get(randomFailure.nextInt(connections.size()));

        try {
            logger.warning("Closing random connection: " + toClose.getInetAddress());
            toClose.close();
            activeConnections.remove(toClose);
        } catch (IOException e) {
            logger.warning("Error closing random connection: " + e.getMessage());
        }
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
                    activeConnections.remove(nextRequest.getClientSocket());
                    processNextInQueue();
                }
            }).start();
        }
    }

    private boolean tryDelegateRequest(Request request) {
        if (request.getRequestedFile() == null || request.getBlockId() < 0) {
            logger.warning("Invalid request for delegation");
            return false;
        }
    
        List<ClientInfo> helpers = trustedClients.get(request.getRequestedFile());
        if (helpers == null || helpers.isEmpty()) {
            logger.info("No trusted clients available for delegation.");
            return false;
        }
    
        // Shuffle helpers to distribute load
        Collections.shuffle(helpers);
        
        for (ClientInfo helper : helpers) {
            if (contactTrustedClient(helper, request)) {
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
            if (response.startsWith("ACCEPT")) {
                String tokenId = response.split(" ")[1];

                try (DataOutputStream toClient = new DataOutputStream(request.getClientSocket().getOutputStream())) {
                    toClient.writeUTF("DELEGATED " + helper.getIp() + " " + helper.getPort() + " " + tokenId);
                }
                logger.info("Delegation accepted. Token sent to client.");
                return true;
            } else {
                logger.info("Trusted client refused the delegation.");
                return false;
            }

        } catch (IOException e) {
            logger.warning("Failed to delegate to trusted client: " + e.getMessage());
            return false;
        }
    }
}