package server;

import java.net.Socket;

public class Request {
    private Socket clientSocket;
    private String requestedFile;
    private int blockId;

    public Request(Socket clientSocket) {
        // Constructeur de base
    }

    public Request(Socket clientSocket, String requestedFile, int blockId) {
        // Constructeur complet
    }

    public Socket getClientSocket() { return clientSocket; }
    public String getRequestedFile() { return requestedFile; }
    public int getBlockId() { return blockId; }
}
