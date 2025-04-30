package server;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Represents a client's request to download a specific block of a file.
 * Parses the request directly from the client's socket.
 */
public class Request {
    private Socket clientSocket;
    private String requestedFile;
    private int blockId;

    /**
     * Constructs a Request by parsing the incoming message from a client socket.
     * If the request is not a valid BLOCK request, the file and block ID are set to null and -1 respectively.
     *
     * @param socket The client socket from which to read the request
     * @throws IOException If an I/O error occurs while reading the request
     */
    public Request(Socket socket) throws IOException {
        this.clientSocket = socket;
        DataInputStream in = new DataInputStream(socket.getInputStream());
        String request = in.readUTF();

        if (request.startsWith("BLOCK")) {
            String[] parts = request.split(" ");
            this.requestedFile = parts.length > 1 ? parts[1] : null;
            this.blockId = parts.length > 2 ? Integer.parseInt(parts[2]) : -1;
        } else {
            this.requestedFile = null;
            this.blockId = -1;
        }
    }

    /**
     * Constructs a Request manually with a given socket, filename, and block ID.
     *
     * @param clientSocket The client socket
     * @param requestedFile The name of the file requested
     * @param blockId The ID of the block requested
     */
    public Request(Socket clientSocket, String requestedFile, int blockId) {
        this.clientSocket = clientSocket;
        this.requestedFile = requestedFile;
        this.blockId = blockId;
    }

    /**
     * Returns the client socket associated with this request.
     *
     * @return The client socket
     */
    public Socket getClientSocket() {
        return clientSocket;
    }

    /**
     * Returns the name of the requested file.
     *
     * @return The requested file name
     */
    public String getRequestedFile() {
        return requestedFile;
    }

    /**
     * Returns the ID of the requested block.
     *
     * @return The block ID
     */
    public int getBlockId() {
        return blockId;
    }
}
