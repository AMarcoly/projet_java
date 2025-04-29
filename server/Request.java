package server;

import java.net.Socket;

public class Request {
    private Socket clientSocket;
    private String requestedFile;
    private int blockId;

    public Request(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public Request(Socket clientSocket, String requestedFile, int blockId) {
        this.clientSocket = clientSocket;
        this.requestedFile = requestedFile;
        this.blockId = blockId;
    }

    public Socket getClientSocket() { return clientSocket; }
    public String getRequestedFile() { return requestedFile; }
    public int getBlockId() { return blockId; }
}
