package server;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import common.Utils;
import common.LoggerUtil;

public class Server {
    private final int port;
    private final int capacityCs;
    private final Map<String, List<ClientInfo>> trustedClients = new ConcurrentHashMap<>();
    private final BlockingQueue<Request> queue = new LinkedBlockingQueue<>();
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final Set<Socket> activeConnections = ConcurrentHashMap.newKeySet();
    private final Random randomFailure = new Random();
    private final Logger logger = LoggerUtil.getLogger(Server.class);
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    public Server(int port, int capacityCs) {
        this.port = port;
        this.capacityCs = capacityCs;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("Server started on port " + port);

            new Thread(this::monitorConnections).start();

            while (running) {
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connected: " + clientSocket.getInetAddress());
                activeConnections.add(clientSocket);

                if (activeRequests.get() < capacityCs) {
                    activeRequests.incrementAndGet();
                    threadPool.submit(() -> {
                        try {
                            handleRequest(clientSocket);
                        } finally {
                            activeRequests.decrementAndGet();
                            activeConnections.remove(clientSocket);
                            processNextInQueue();
                        }
                    });
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

    public void stop() {
        running = false;
        threadPool.shutdownNow();
        logger.info("Server stopped.");
    }

    private void monitorConnections() {
        while (running) {
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

    private void handleRequest(Socket clientSocket) {
        try (
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())
        ) {
            String request = in.readUTF();
            logger.info("Received request: " + request);

            if (request.startsWith("BLOCK")) {
                handleBlockRequest(request, out);
            } else if (request.startsWith("LIST")) {
                handleListRequest(out);
            } else if (request.startsWith("MD5")) {
                handleMD5Verification(request);
            } else if (request.startsWith("REGISTER_HELPER")) {
                handleHelperRegistration(request);
            }

        }catch (IOException e) {
            logger.warning("Error handling client request from " + 
            (clientSocket != null ? clientSocket.getInetAddress() : "unknown") + 
            ": " + (e.getMessage() != null ? e.getMessage() : "Connection reset"));
        }finally {
            try {
                clientSocket.close();
            } catch (IOException ignore) {}
        }
    }

    private void handleBlockRequest(String request, DataOutputStream out) throws IOException {
        String[] parts = request.split(" ");
        String fileId = parts[1];
        int blockId = Integer.parseInt(parts[2]);

        File file = new File("./server_files/" + fileId);
        if (!file.exists()) {
            out.writeUTF("FAILURE");
            logger.warning("Requested file not found: " + fileId);
            return;
        }

        int blockSize = 1024;
        byte[] buffer = new byte[blockSize];

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek((long) blockId * blockSize);
            int bytesRead = raf.read(buffer);

            if (bytesRead <= 0) {
                out.writeUTF("FAILURE");
                logger.warning("Empty or missing block: " + blockId);
            } else {
                out.writeUTF("OK");
                out.writeInt(bytesRead);
                out.write(buffer, 0, bytesRead);
                logger.info("Block " + blockId + " sent (" + bytesRead + " bytes)");
            }
        }
    }

    private void handleListRequest(DataOutputStream out) throws IOException {
        File dir = new File("./server_files/");
        String[] files = dir.list((d, name) -> name.endsWith(".txt"));
        if (files == null) files = new String[0];
        out.writeUTF(String.join(";", files));
        logger.info("Sent file list to client.");
    }

    private void handleMD5Verification(String request) {
        String[] parts = request.split(" ");
        String fileId = parts[1];
        String clientMd5 = parts[2];

        try {
            File file = new File("./server_files/" + fileId);
            if (!file.exists()) {
                logger.warning("File for MD5 verification not found: " + fileId);
                return;
            }

            byte[] data = Files.readAllBytes(file.toPath());
            String serverMd5 = Utils.computeMD5(data);

            if (serverMd5.equals(clientMd5)) {
                logger.info("MD5 verification success for " + fileId);
            } else {
                logger.warning("MD5 mismatch: server=" + serverMd5 + " / client=" + clientMd5);
            }
        } catch (IOException e) {
            logger.warning("Error during MD5 verification: " + e.getMessage());
        }
    }

    private void handleHelperRegistration(String request) {
        String[] parts = request.split(" ");
        String fileId = parts[1];
        String ip = parts[2];
        int port = Integer.parseInt(parts[3]);

        ClientInfo helper = new ClientInfo(ip, port);
        trustedClients.computeIfAbsent(fileId, k -> new ArrayList<>()).add(helper);
        logger.info("Registered Trusted Helper for file " + fileId + ": " + ip + ":" + port);
    }

    private void processNextInQueue() {
        Request next = queue.poll();
        if (next != null) {
            activeRequests.incrementAndGet();
            threadPool.submit(() -> {
                try {
                    handleRequest(next.getClientSocket());
                } finally {
                    activeRequests.decrementAndGet();
                    activeConnections.remove(next.getClientSocket());
                    processNextInQueue();
                }
            });
        }
    }

    private void closeRandomConnection() {
        List<Socket> connections = new ArrayList<>(activeConnections);
        if (connections.isEmpty()) {
            logger.info("No active connections to close.");
            return;
        }

        Socket toClose = connections.get(randomFailure.nextInt(connections.size()));
        try {
            logger.warning("Closing random connection: " + toClose.getInetAddress());
            toClose.close();
            activeConnections.remove(toClose);
        } catch (IOException e) {
            logger.warning("Error closing random connection: " + e.getMessage());
        }
    }

    private boolean tryDelegateRequest(Request request) {
        if (request == null || request.getRequestedFile() == null) {
            logger.warning("Invalid delegation request - no file specified");
            return false;
        }
        List<ClientInfo> helpers = trustedClients.getOrDefault(request.getRequestedFile(), List.of());
        for (ClientInfo helper : helpers) {
            if (contactTrustedClient(helper, request)) {
                logger.info("Delegated request to " + helper.getIp() + ":" + helper.getPort());
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
                String token = response.split(" ")[1];
                try (DataOutputStream toClient = new DataOutputStream(request.getClientSocket().getOutputStream())) {
                    toClient.writeUTF("DELEGATED " + helper.getIp() + " " + helper.getPort() + " " + token);
                }
                return true;
            }
        } catch (IOException e) {
            logger.warning("Delegation to " + helper.getIp() + " failed: " + e.getMessage());
        }
        return false;
    }

    public static void main(String[] args) {
        int port = 5000;
        int capacity = 2;
    
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--capacity=")) {
                capacity = Integer.parseInt(arg.substring("--capacity=".length()));
            }
        }
    
        Server server = new Server(port, capacity); // tu peux adapter 2 à un paramètre si tu veux
        server.start();
    }
    
}
